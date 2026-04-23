package org.gbif.dp.service.messaging;

import java.io.IOException;

@FunctionalInterface
public interface MessageConsumer {
  void consume(MessageHandler handler) throws IOException;
}
