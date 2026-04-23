package org.gbif.dp.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.gbif.dp.analysis.model.DatapackageAnalysisResult;
import org.gbif.dp.service.api.DwcDpValidationFinished;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ValidationRequestHandlerTest {

  private static final String MSG_ID = "test-msg-id";
  private static final ValidationRequest REQUEST = new ValidationRequest("dataset-uuid-123", 1);

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
    assertEquals(REQUEST.datasetUuid(), published.datasetUuid());
    assertEquals(REQUEST.attempt(), published.attempt());
  }

  @Test
  void handle_invalidResult_doesNotPublish() throws Exception {
    Validator validator = req -> DatapackageAnalysisTestResults.invalidResult();
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher);

    handler.handle(REQUEST, MSG_ID);

    assertNull(capturedPublish.get(), "Expected no message to be published for invalid result");
  }

  @Test
  void handle_validatorThrows_propagatesException() {
    Validator validator = req -> { throw new RuntimeException("validation exploded"); };
    ValidationRequestHandler handler = new ValidationRequestHandler(validator, publisher);

    assertThrows(Exception.class, () -> handler.handle(REQUEST, MSG_ID));
    assertNull(capturedPublish.get(), "Expected no message to be published on failure");
  }
}
