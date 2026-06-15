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
