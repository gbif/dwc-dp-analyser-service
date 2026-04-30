package org.gbif.dp.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.gbif.dp.service.api.DwcDpValidationFinished;
import org.gbif.dp.service.messaging.InMemoryMessageBus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DownloadFinishConsumerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final long TIMEOUT_SECONDS = 2;

  private InMemoryMessageBus inbound;
  private InMemoryMessageBus outbound;
  private DownloadFinishConsumer consumer;

  @BeforeEach
  void setUp() {
    inbound = new InMemoryMessageBus();
    outbound = new InMemoryMessageBus();
  }

  private DownloadFinishConsumer consumerWith(Validator validator) {
    ValidationFinishedPublisher publisher = new ValidationFinishedPublisher(outbound, MAPPER);
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher);
    return new DownloadFinishConsumer(inbound, MAPPER, handler);
  }

  private static void await(CountDownLatch latch) throws InterruptedException {
    assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timed out waiting for messages to be processed");
  }

  @Test
  void validMessage_validResult_acksAndPublishes() throws Exception {
    CountDownLatch latch = inbound.enqueueAndLatch(MAPPER.writeValueAsBytes(new TestMessage("uuid-1", 1)));
    consumer = consumerWith(req -> DatapackageAnalysisTestResults.validResult());

    consumer.start();
    await(latch);

    assertEquals(0, inbound.queued(), "Message should be acked and removed");
    assertEquals(1, outbound.published().size(), "Expected one published result");

    DwcDpValidationFinished published = MAPPER.readValue(
      outbound.published().get(0), DwcDpValidationFinished.class);
    assertEquals("uuid-1", published.datasetUuid());
    assertEquals(1, published.attempt());
    assertTrue(published.valid());
  }

  @Test
  void validMessage_invalidResult_acksButDoesNotPublish() throws Exception {
    CountDownLatch latch = inbound.enqueueAndLatch(MAPPER.writeValueAsBytes(new TestMessage("uuid-2", 2)));
    consumer = consumerWith(req -> DatapackageAnalysisTestResults.invalidResult());

    consumer.start();
    await(latch);

    assertEquals(0, inbound.queued());
    assertEquals(1, outbound.published().size());

    DwcDpValidationFinished published = MAPPER.readValue(
      outbound.published().get(0), DwcDpValidationFinished.class);
    assertFalse(published.valid());
  }

  @Test
  void validMessage_validatorThrows_nacksAndDoesNotPublish() throws Exception {
    CountDownLatch latch = inbound.enqueueAndLatch(MAPPER.writeValueAsBytes(new TestMessage("uuid-3", 3)));
    consumer = consumerWith(req -> { throw new RuntimeException("Processing went wrong"); });

    consumer.start();
    await(latch);

    assertEquals(0, inbound.queued());
    assertEquals(0, outbound.published().size());
  }

  @Test
  void malformedMessage_nacksAndDoesNotPublish() throws Exception {
    CountDownLatch latch = inbound.enqueueAndLatch("not json at all".getBytes());
    consumer = consumerWith(req -> DatapackageAnalysisTestResults.validResult());

    consumer.start();
    await(latch);

    assertEquals(0, inbound.queued());
    assertEquals(1, outbound.published().size());
  }

  // Minimal message shape matching what the consumer deserializes
  record TestMessage(String datasetUuid, int attempt) {}
}
