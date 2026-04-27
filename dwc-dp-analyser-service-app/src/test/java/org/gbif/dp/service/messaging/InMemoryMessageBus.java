package org.gbif.dp.service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory message bus for tests.
 * Use as both publisher (to enqueue messages) and consumer (to process them).
 *
 * <pre>
 *   InMemoryMessageBus inbound = new InMemoryMessageBus();
 *   InMemoryMessageBus outbound = new InMemoryMessageBus();
 *
 *   inbound.enqueue(payload);               // simulate incoming message
 *   consumer.start();                       // processes from inbound, publishes to outbound
 *   byte[] result = outbound.published().get(0);
 * </pre>
 */
public class InMemoryMessageBus implements MessageConsumer, MessagePublisher {

  private static final Logger log = LoggerFactory.getLogger(InMemoryMessageBus.class);

  private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
  private final List<byte[]> deadLetterQueue = new ArrayList<>();
  private final List<byte[]> published = new ArrayList<>();

  private CountDownLatch latch;

  /** Enqueue a raw message as if it arrived from a broker. */
  public void enqueue(byte[] body) {
    queue.add(body);
  }

  public CountDownLatch enqueueAndLatch(byte[]... messages) {
    latch = new CountDownLatch(messages.length);
    Collections.addAll(queue, messages);
    return latch;
  }

  /** Number of messages currently waiting (nacked messages re-appear here). */
  public int queued() {
    return queue.size();
  }

  /** All messages published to this bus. */
  public List<byte[]> published() {
    return published;
  }

  public List<byte[]> deadLetterQueue() {
    return deadLetterQueue;
  }

  @Override
  public void publish(byte[] body) throws IOException {
    published.add(body);
  }

  /**
   * Drains all currently queued messages synchronously.
   * Ack is a no-op; nack re-enqueues for inspection via queued().
   */
  @Override
  public void consume(MessageHandler handler) throws IOException {
    byte[] body;
    while ((body = queue.poll()) != null) {
      final byte[] msg = body;
      Ack ack = new Ack() {
        @Override public void ack() { countDown(); }
        @Override public void nack() {
          deadLetterQueue.add(msg);
          countDown();
        }
      };
      try {
        handler.handle(msg, ack);
      } catch (Exception e) {
        log.error("Error handling request", e);
      }
    }
  }

  private void countDown() {
    if (latch != null) {
      latch.countDown();
    }
  }
}
