package org.gbif.dp.service.messaging;

import java.io.IOException;

public interface Ack {
  void ack() throws IOException;
  void nack() throws IOException;
}
