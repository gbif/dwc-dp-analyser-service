package org.gbif.dp.service;

import org.gbif.dp.analysis.api.DatapackageAnalysisResult;
import org.gbif.dp.service.api.DwcDpValidationFinished;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles a single validated ValidationRequest.
 * Separated from RabbitMQ plumbing so it can be tested independently.
 */
public class ValidationRequestHandler {

  private static final Logger log = LoggerFactory.getLogger(ValidationRequestHandler.class);

  private final Validator validator;
  private final ValidationFinishedPublisher publisher;

  public ValidationRequestHandler(Validator validator, ValidationFinishedPublisher publisher) {
    this.validator = validator;
    this.publisher = publisher;
  }

  public void handle(ValidationRequest request, String messageId) throws Exception {
    log.info("Validating msgId: [{}], dataset: [{}], attempt: [{}]",
      messageId, request.datasetUuid(), request.attempt());

    DatapackageAnalysisResult result = validator.validate(request);
    boolean valid = DatapackageAnalysisResult.isValid(result);

    publisher.publish(new DwcDpValidationFinished(
      request.datasetUuid(),
      request.attempt(),
      valid,
      result
      ));

    log.info("Processed dataset: [{}], attempt: [{}], valid: [{}]",
      request.datasetUuid(), request.attempt(), valid);
  }
}
