package org.gbif.dp.service;

import org.gbif.dp.analysis.api.ValidationOptions;
import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DwcValidatorTest {

  @TempDir Path archiveRepository;
  @TempDir Path unpackRepository;

  private static final UUID DATASET_UUID = UUID.randomUUID();
  private static final int ATTEMPT = 1;
  private DwcValidator.DwcValidatorConfig config;

  @BeforeEach
  void setUp() throws Exception {
    // Place the fixture zip at the expected path:
    // {archiveRepository}/{datasetUuid}/{datasetUuid}.{attempt}.dwcdp
    Path datasetDir = archiveRepository.resolve(DATASET_UUID.toString());
    Files.createDirectories(datasetDir);
    Path zipDest = datasetDir.resolve(DATASET_UUID + "." + ATTEMPT + ".dwcdp");

    try (var in = getClass().getResourceAsStream("/fixtures/test-dataset.dwcdp")) {
      assertNotNull(in, "Fixture zip(dwcdp) not found in test resources");
      Files.copy(in, zipDest, StandardCopyOption.REPLACE_EXISTING);
    }

    this.config = new DwcValidator.DwcValidatorConfig(archiveRepository, unpackRepository, ValidationOptions.defaults());
  }

  @Test
  void validate_unpacksZipAndCallsAnalyser() throws Exception {
    AtomicReference<Path> capturedPath = new AtomicReference<>();

    DwcValidator validator = new DwcValidator(
      (datapackagePath, options, features) -> {
        capturedPath.set(datapackagePath);
        return DatapackageAnalysisTestResults.validResult();
      },
      config);

    DatapackageAnalysisResult result = validator.validate(new ValidationRequest(DATASET_UUID, ATTEMPT));

    assertTrue(DatapackageAnalysisResult.isValid(result));

    // Verify analyser received the expected unpack directory
    Path datasetKeyDir = unpackRepository.resolve(DATASET_UUID.toString());
    Path expectedUnpackDir = datasetKeyDir.resolve(DATASET_UUID + "." + ATTEMPT);
    Path dataPackagePath = capturedPath.get();
    assertEquals(expectedUnpackDir, dataPackagePath.getParent());

    assertFalse(Files.exists(expectedUnpackDir), "Unpack directory should have been deleted from cleanup");
    assertFalse(Files.exists(datasetKeyDir), "Dataset key directory should have been deleted from cleanup");
  }

  @Test
  void validate_missingZip_throwsException() {
    DwcValidator validator = new DwcValidator(
      (datapackagePath, options, features) -> DatapackageAnalysisTestResults.validResult(),
      config);

    // Use a UUID that has no zip on disk
    UUID anotherUuid = UUID.randomUUID();
    assertThrows(Exception.class,
      () -> validator.validate(new ValidationRequest(anotherUuid, 99)));
  }
}
