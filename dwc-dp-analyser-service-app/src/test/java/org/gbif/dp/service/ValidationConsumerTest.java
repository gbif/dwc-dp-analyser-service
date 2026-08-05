/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.dp.service;

import org.gbif.dp.service.api.DwcDpValidationFinished;
import org.gbif.dp.service.http.RegistryClient;
import org.gbif.dp.service.messaging.InMemoryMessageBus;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class ValidationConsumerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final long TIMEOUT_SECONDS = 2;

  private InMemoryMessageBus inbound;
  private InMemoryMessageBus outbound;
  private ValidationConsumer consumer;
  private RegistryClient noOpRegistryClient;

  @BeforeEach
  void setUp() {
    inbound = new InMemoryMessageBus();
    outbound = new InMemoryMessageBus();
    noOpRegistryClient = (datasetKey, attempt, result) -> {};
  }

  private ValidationConsumer consumerWith(Validator validator) {
    ValidationFinishedPublisher publisher = new ValidationFinishedPublisher(outbound, MAPPER);
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher, noOpRegistryClient);
    return new ValidationConsumer(inbound, MAPPER, handler);
  }

  private static void await(CountDownLatch latch) throws InterruptedException {
    assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timed out waiting for messages to be processed");
  }

  @Test
  void validMessage_validResult_acksAndPublishes() throws Exception {
    UUID uuid = UUID.randomUUID();
    CountDownLatch latch = inbound.enqueueAndLatch(MAPPER.writeValueAsBytes(new TestMessage(uuid, 1)));
    consumer = consumerWith(req -> DatapackageAnalysisTestResults.validResult());

    consumer.start();
    await(latch);

    assertEquals(0, inbound.queued(), "Message should be acked and removed");
    assertEquals(1, outbound.published().size(), "Expected one published result");

    DwcDpValidationFinished published = MAPPER.readValue(
      outbound.published().get(0), DwcDpValidationFinished.class);
    assertEquals(uuid, published.getDatasetUuid());
    assertEquals(1, published.getAttempt());
    assertTrue(published.isValid());
  }

  @Test
  void validMessage_invalidResult_acksButDoesNotPublish() throws Exception {
    UUID uuid = UUID.randomUUID();
    CountDownLatch latch = inbound.enqueueAndLatch(MAPPER.writeValueAsBytes(new TestMessage(uuid, 2)));
    consumer = consumerWith(req -> DatapackageAnalysisTestResults.invalidResult());

    consumer.start();
    await(latch);

    assertEquals(0, inbound.queued());
    assertEquals(1, outbound.published().size());

    DwcDpValidationFinished published = MAPPER.readValue(
      outbound.published().get(0), DwcDpValidationFinished.class);
    assertFalse(published.isValid());
  }

  @Test
  void validMessage_validatorThrows_nacksAndDoesNotPublish() throws Exception {
    UUID uuid = UUID.randomUUID();
    CountDownLatch latch = inbound.enqueueAndLatch(MAPPER.writeValueAsBytes(new TestMessage(uuid, 3)));
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
    assertEquals(0, outbound.published().size());
  }

  // Minimal message shape matching what the consumer deserializes
  record TestMessage(UUID datasetUuid, int attempt) {}
}
