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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class ValidationRequestHandlerTest {

  private static final String MSG_ID = "test-msg-id";
  private static final ValidationRequest REQUEST = new ValidationRequest(UUID.randomUUID(), 1);

  private AtomicReference<DwcDpValidationFinished> capturedPublish;
  private ValidationFinishedPublisher publisher;

  @BeforeEach
  void setUp() {
    capturedPublish = new AtomicReference<>();
    // Minimal publisher stub — captures what was published

    ObjectMapper mapper = new ObjectMapper();
    publisher = new ValidationFinishedPublisher(msg -> capturedPublish.set(mapper.readValue(msg, DwcDpValidationFinished.class)), mapper);
  }

  @Test
  void handle_validResult_publishesMessage() throws Exception {
    Validator validator = req -> DatapackageAnalysisTestResults.validResult();
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher);

    handler.handle(REQUEST, MSG_ID);

    DwcDpValidationFinished published = capturedPublish.get();
    assertNotNull(published, "Expected a message to be published");
    assertEquals(REQUEST.datasetUuid(), published.getDatasetUuid());
    assertEquals(REQUEST.attempt(), published.getAttempt());
  }

  @Test
  void handle_invalidResult_doesNotPublish() throws Exception {
    Validator validator = req -> DatapackageAnalysisTestResults.invalidResult();
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher);

    handler.handle(REQUEST, MSG_ID);

    DwcDpValidationFinished dwcDpValidationFinished = capturedPublish.get();
    assertFalse(dwcDpValidationFinished.isValid());
  }

  @Test
  void handle_validatorThrows_propagatesException() {
    Validator validator = req -> {
      throw new RuntimeException("validation exploded");
    };
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher);

    assertThrows(Exception.class, () -> handler.handle(REQUEST, MSG_ID));
    assertNull(capturedPublish.get(), "Expected no message to be published on failure");
  }
}
