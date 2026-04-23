package org.gbif.dp.service.messaging;

import java.io.IOException;

@FunctionalInterface
public interface MessagePublisher {
  void publish(byte[] body) throws IOException;
}
