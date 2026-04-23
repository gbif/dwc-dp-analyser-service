package org.gbif.dp.service;

import org.gbif.dp.analysis.model.DatapackageAnalysisResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DwcValidatorTest {

  @TempDir Path archiveRepository;
  @TempDir Path unpackRepository;

  private static final String DATASET_UUID = "test-dataset-uuid";
  private static final int ATTEMPT = 1;

  @BeforeEach
  void setUp() throws Exception {
    // Place the fixture zip at the expected path:
    // {archiveRepository}/{datasetUuid}/{datasetUuid}.{attempt}.dwcdp
    Path datasetDir = archiveRepository.resolve(DATASET_UUID);
    Files.createDirectories(datasetDir);
    Path zipDest = datasetDir.resolve(DATASET_UUID + "." + ATTEMPT + ".dwcdp");

    try (var in = getClass().getResourceAsStream("/fixtures/test-dataset.dwcdp")) {
      assertNotNull(in, "Fixture zip(dwcdp) not found in test resources");
      Files.copy(in, zipDest, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  @Test
  void validate_unpacksZipAndCallsAnalyser() throws Exception {
    AtomicReference<Path> capturedPath = new AtomicReference<>();

    DwcValidator validator = new DwcValidator(
      (datapackagePath, options, features) -> {
        capturedPath.set(datapackagePath);
        return DatapackageAnalysisTestResults.validResult();
      },
      archiveRepository,
      unpackRepository);

    DatapackageAnalysisResult result = validator.validate(new ValidationRequest(DATASET_UUID, ATTEMPT));

    assertTrue(result.isValid());

    // Verify analyser received the expected unpack directory
    Path expectedUnpackDir = unpackRepository.resolve(DATASET_UUID).resolve(DATASET_UUID + "." + ATTEMPT);
    Path dataPackagePath = capturedPath.get();
    assertEquals(expectedUnpackDir, dataPackagePath.getParent());

    assertTrue(Files.exists(expectedUnpackDir), "Unpack directory should exist");
  }

  @Test
  void validate_missingZip_throwsException() {
    DwcValidator validator = new DwcValidator(
      (datapackagePath, options, features) -> DatapackageAnalysisTestResults.validResult(),
      archiveRepository,
      unpackRepository);

    // Use a UUID that has no zip on disk
    assertThrows(Exception.class,
      () -> validator.validate(new ValidationRequest("nonexistent-uuid", 99)));
  }
}
