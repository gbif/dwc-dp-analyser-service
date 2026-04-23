package org.gbif.dp.service.messaging;

@FunctionalInterface
public interface MessageHandler {
  void handle(byte[] body, Ack ack) throws Exception;
}
