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

import org.gbif.dp.analysis.api.AnalysisExecution;
import org.gbif.dp.analysis.api.AnalysisFeature;
import org.gbif.dp.analysis.api.DataPackageAnalysisOrchestrator;
import org.gbif.dp.analysis.api.DatapackageAnalysisResult;
import org.gbif.dp.analysis.api.ValidationOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DwcValidatorTest {

  @TempDir Path archiveRepository;
  @TempDir Path unpackRepository;

  private static final UUID DATASET_UUID = UUID.randomUUID();
  private static final int ATTEMPT = 1;

  private DatapackagePathResolver pathResolver;
  private DwcValidator.DwcValidatorConfig config;

  private static DataPackageAnalysisOrchestrator fake(AtomicReference<String> callCapture,
                                                      AnalysisExecution<DatapackageAnalysisResult> response) {
    return new DataPackageAnalysisOrchestrator() {

      @Override
      public DatapackageAnalysisResult analyse(
        String descriptorLocation,
        ValidationOptions options,
        List<AnalysisFeature> features
      ) throws IOException, SQLException {
        callCapture.set(descriptorLocation);
        return response.result();
      }

      @Override
      public AnalysisExecution<DatapackageAnalysisResult> analyseWithFullReport(
        String descriptorLocation,
        ValidationOptions options,
        List<AnalysisFeature> features
      ) throws IOException, SQLException {
        callCapture.set(descriptorLocation);
        return response;
      }
    };
  }

  @BeforeEach
  void setUp() throws Exception {
    // Place the fixture zip at the expected path:
    // {archiveRepository}/{datasetUuid}/{datasetUuid}.{attempt}/{datasetUuid}.{attempt}.dwcdp
    Path datasetDir = archiveRepository.resolve(DATASET_UUID.toString());
    Path attemptDir = datasetDir.resolve(DATASET_UUID + "." + ATTEMPT);
    Files.createDirectories(attemptDir);
    Path zipDest = attemptDir.resolve(DATASET_UUID + "." + ATTEMPT + ".dwcdp");

    try (var in = getClass().getResourceAsStream("/fixtures/test-dataset.dwcdp")) {
      assertNotNull(in, "Fixture zip(dwcdp) not found in test resources");
      Files.copy(in, zipDest, StandardCopyOption.REPLACE_EXISTING);
    }

    this.pathResolver = new DatapackagePathResolver(archiveRepository, unpackRepository);
    this.config = new DwcValidator.DwcValidatorConfig(ValidationOptions.defaults());
  }

  @Test
  void validate_unpacksZipAndCallsAnalyser() throws Exception {
    AtomicReference<String> capturedLocation = new AtomicReference<>();

    DwcValidator validator = new DwcValidator(
      fake(capturedLocation, DatapackageAnalysisTestResults.validResult()),
      pathResolver,
      config);

    DatapackageAnalysisResult result = validator.validate(new ValidationRequest(DATASET_UUID, ATTEMPT)).result();

    assertTrue(DatapackageAnalysisResult.isValid(result));

    // Verify analyser received the expected unpack directory
    Path datasetKeyDir = unpackRepository.resolve(DATASET_UUID.toString());
    Path expectedUnpackDir = datasetKeyDir.resolve(DATASET_UUID + "." + ATTEMPT);
    Path dataPackagePath = Path.of(capturedLocation.get());
    assertEquals(expectedUnpackDir, dataPackagePath.getParent());

    assertFalse(Files.exists(expectedUnpackDir), "Unpack directory should have been deleted from cleanup");
    assertFalse(Files.exists(datasetKeyDir), "Dataset key directory should have been deleted from cleanup");
  }

  @Test
  void validate_missingZip_throwsException() {
    DwcValidator validator = new DwcValidator(
      fake(new AtomicReference<>(), DatapackageAnalysisTestResults.validResult()),
      pathResolver,
      config);

    // Use a UUID that has no zip on disk
    UUID anotherUuid = UUID.randomUUID();
    assertThrows(Exception.class,
                 () -> validator.validate(new ValidationRequest(anotherUuid, 99)));
  }

  @Test
  void validate_skipsUnzipWhenAlreadyUnpacked_attemptSpecific() throws Exception {
    // Pre-place datapackage.json at {uuid}/{uuid}.{attempt}/datapackage.json (priority 1)
    Path attemptDir = archiveRepository.resolve(DATASET_UUID.toString()).resolve(DATASET_UUID + "." + ATTEMPT);
    Files.createFile(attemptDir.resolve("datapackage.json"));

    AtomicReference<String> capturedLocation = new AtomicReference<>();

    DwcValidator validator = new DwcValidator(
      fake(capturedLocation, DatapackageAnalysisTestResults.validResult()),
      pathResolver,
      config);

    DatapackageAnalysisResult result = validator.validate(new ValidationRequest(DATASET_UUID, ATTEMPT)).result();

    assertTrue(DatapackageAnalysisResult.isValid(result));

    // Analyser should have received the archive path, not the unpack path
    assertTrue(Path.of(capturedLocation.get()).startsWith(archiveRepository),
               "Should have used pre-unpacked path, not unpackRepository");

    // Nothing should have been written to unpackRepository
    assertFalse(Files.exists(unpackRepository.resolve(DATASET_UUID.toString())),
                "Unpack directory should not have been created");

    // Pre-placed file should still exist — no cleanup should have run
    assertTrue(Files.exists(attemptDir.resolve("datapackage.json")),
               "Pre-existing datapackage.json should not have been deleted");
  }

  @Test
  void validate_skipsUnzipWhenAlreadyUnpacked_latestPattern() throws Exception {
    // Pre-place datapackage.json directly at {uuid}/datapackage.json (priority 2, no attempt dir)
    Path datasetDir = archiveRepository.resolve(DATASET_UUID.toString());
    Files.createFile(datasetDir.resolve("datapackage.json"));

    AtomicReference<String> capturedLocation = new AtomicReference<>();

    DwcValidator validator = new DwcValidator(
      fake(capturedLocation, DatapackageAnalysisTestResults.validResult()),
      pathResolver,
      config);

    DatapackageAnalysisResult result = validator.validate(new ValidationRequest(DATASET_UUID, ATTEMPT)).result();

    assertTrue(DatapackageAnalysisResult.isValid(result));
    assertEquals(datasetDir.resolve("datapackage.json").toString(), capturedLocation.get());

    assertFalse(Files.exists(unpackRepository.resolve(DATASET_UUID.toString())),
                "Unpack directory should not have been created");
  }
}
