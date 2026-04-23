package org.gbif.dp.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.gbif.dp.service.api.DwcDpValidationFinished;
import org.gbif.dp.service.messaging.MessagePublisher;

public class ValidationFinishedPublisher {

  private final MessagePublisher publisher;
  private final ObjectMapper mapper;

  public ValidationFinishedPublisher(MessagePublisher publisher, ObjectMapper mapper) {
    this.publisher = publisher;
    this.mapper = mapper;
  }

  public void publish(DwcDpValidationFinished result) throws Exception {
    publisher.publish(mapper.writeValueAsBytes(result));
  }
}
