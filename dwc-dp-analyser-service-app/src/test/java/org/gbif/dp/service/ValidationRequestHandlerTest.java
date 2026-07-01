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
  private RegistryClient noOpRegistryClient;

  @BeforeEach
  void setUp() {
    capturedPublish = new AtomicReference<>();
    ObjectMapper mapper = new ObjectMapper();
    publisher = new ValidationFinishedPublisher(
      msg -> capturedPublish.set(mapper.readValue(msg, DwcDpValidationFinished.class)), mapper);
    noOpRegistryClient = (datasetKey, attempt, result) -> {};
  }

  @Test
  void handle_validResult_publishesMessage() throws Exception {
    Validator validator = req -> DatapackageAnalysisTestResults.validResult();
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher, noOpRegistryClient);

    handler.handle(REQUEST, MSG_ID);

    DwcDpValidationFinished published = capturedPublish.get();
    assertNotNull(published, "Expected a message to be published");
    assertEquals(REQUEST.datasetUuid(), published.getDatasetUuid());
    assertEquals(REQUEST.attempt(), published.getAttempt());
  }

  @Test
  void handle_invalidResult_doesNotPublish() throws Exception {
    Validator validator = req -> DatapackageAnalysisTestResults.invalidResult();
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher, noOpRegistryClient);

    handler.handle(REQUEST, MSG_ID);

    DwcDpValidationFinished dwcDpValidationFinished = capturedPublish.get();
    assertFalse(dwcDpValidationFinished.isValid());
  }

  @Test
  void handle_validatorThrows_propagatesException() {
    Validator validator = req -> {
      throw new RuntimeException("validation exploded");
    };
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher, noOpRegistryClient);

    assertThrows(Exception.class, () -> handler.handle(REQUEST, MSG_ID));
    assertNull(capturedPublish.get(), "Expected no message to be published on failure");
  }

  @Test
  void handle_registryClientThrows_stillPublishesMessage() throws Exception {
    Validator validator = req -> DatapackageAnalysisTestResults.validResult();
    RegistryClient failingClient = (datasetKey, attempt, result) -> {
      throw new RegistryClientException("registry unavailable", new RuntimeException());
    };
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher, failingClient);

    handler.handle(REQUEST, MSG_ID);

    DwcDpValidationFinished published = capturedPublish.get();
    assertNotNull(published, "Expected message to be published despite registry failure");
    assertEquals(REQUEST.datasetUuid(), published.getDatasetUuid());
    assertEquals(REQUEST.attempt(), published.getAttempt());
  }

  @Test
  void handle_registryClientCalled_withCorrectArguments() throws Exception {
    DatapackageAnalysisResult expectedResult = DatapackageAnalysisTestResults.validResult();
    Validator validator = req -> expectedResult;

    AtomicReference<UUID> capturedKey = new AtomicReference<>();
    AtomicReference<Integer> capturedAttempt = new AtomicReference<>();
    AtomicReference<DatapackageAnalysisResult> capturedResult = new AtomicReference<>();

    RegistryClient capturingClient = (datasetKey, attempt, result) -> {
      capturedKey.set(datasetKey);
      capturedAttempt.set(attempt);
      capturedResult.set(result);
    };

    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher, capturingClient);
    handler.handle(REQUEST, MSG_ID);

    assertEquals(REQUEST.datasetUuid(), capturedKey.get());
    assertEquals(REQUEST.attempt(), capturedAttempt.get());
    assertSame(expectedResult, capturedResult.get());
  }
}
