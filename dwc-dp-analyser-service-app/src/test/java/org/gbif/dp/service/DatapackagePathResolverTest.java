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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DatapackagePathResolverTest {

  @TempDir Path archiveRepository;
  @TempDir Path unpackRepository;

  private static final UUID DATASET_UUID = UUID.randomUUID();
  private static final int ATTEMPT = 1;

  private DatapackagePathResolver resolver() {
    return new DatapackagePathResolver(archiveRepository, unpackRepository);
  }

  private Path datasetDir() {
    return archiveRepository.resolve(DATASET_UUID.toString());
  }

  private Path attemptDir() {
    return datasetDir().resolve(DATASET_UUID + "." + ATTEMPT);
  }

  @Test
  void resolve_priority1_attemptSpecificUnpacked() throws Exception {
    Files.createDirectories(attemptDir());
    Path expected = attemptDir().resolve("datapackage.json");
    Files.createFile(expected);

    DatapackagePathResolver.Resolution resolution = resolver().resolve(DATASET_UUID, ATTEMPT);

    assertEquals(expected, resolution.datapackageJson());
    assertNull(resolution.unpackDirToCleanUp(), "No unzip happened, nothing to clean up");
  }

  @Test
  void resolve_priority1_takesPrecedenceOverPriority2() throws Exception {
    // Both an attempt-specific and a latest-pattern datapackage.json exist —
    // attempt-specific must win.
    Files.createDirectories(attemptDir());
    Path attemptSpecific = attemptDir().resolve("datapackage.json");
    Files.createFile(attemptSpecific);

    Path latestPattern = datasetDir().resolve("datapackage.json");
    Files.createFile(latestPattern);

    DatapackagePathResolver.Resolution resolution = resolver().resolve(DATASET_UUID, ATTEMPT);

    assertEquals(attemptSpecific, resolution.datapackageJson());
  }

  @Test
  void resolve_priority2_latestPatternUnpacked() throws Exception {
    Files.createDirectories(datasetDir());
    Path expected = datasetDir().resolve("datapackage.json");
    Files.createFile(expected);

    DatapackagePathResolver.Resolution resolution = resolver().resolve(DATASET_UUID, ATTEMPT);

    assertEquals(expected, resolution.datapackageJson());
    assertNull(resolution.unpackDirToCleanUp());
  }

  @Test
  void resolve_priority3_dwcdpArchive_unzipsAndReturnsCleanupDir() throws Exception {
    Files.createDirectories(attemptDir());
    Path zipDest = attemptDir().resolve(DATASET_UUID + "." + ATTEMPT + ".dwcdp");

    try (var in = getClass().getResourceAsStream("/fixtures/test-dataset.dwcdp")) {
      assertNotNull(in, "Fixture zip(dwcdp) not found in test resources");
      Files.copy(in, zipDest);
    }

    DatapackagePathResolver.Resolution resolution = resolver().resolve(DATASET_UUID, ATTEMPT);

    Path expectedUnpackDir = unpackRepository
      .resolve(DATASET_UUID.toString())
      .resolve(DATASET_UUID + "." + ATTEMPT);

    assertEquals(expectedUnpackDir, resolution.unpackDirToCleanUp());
    assertEquals(expectedUnpackDir.resolve("datapackage.json"), resolution.datapackageJson());
    assertTrue(Files.exists(resolution.datapackageJson()), "Archive should have been unzipped, no datapackage at: " + resolution.datapackageJson());
  }

  @Test
  void resolve_priority4_zipArchive_usedWhenDwcdpAbsent() throws Exception {
    Files.createDirectories(attemptDir());
    Path zipDest = attemptDir().resolve(DATASET_UUID + "." + ATTEMPT + ".zip");

    try (var in = getClass().getResourceAsStream("/fixtures/test-dataset.zip")) {
      assertNotNull(in, "Fixture zip(.zip) not found in test resources");
      Files.copy(in, zipDest);
    }

    DatapackagePathResolver.Resolution resolution = resolver().resolve(DATASET_UUID, ATTEMPT);

    Path expectedUnpackDir = unpackRepository
      .resolve(DATASET_UUID.toString())
      .resolve(DATASET_UUID + "." + ATTEMPT);

    assertEquals(expectedUnpackDir, resolution.unpackDirToCleanUp());
    assertTrue(Files.exists(resolution.datapackageJson()), "Archive should have been unzipped, no datapackage at: " + resolution.datapackageJson());
  }

  @Test
  void resolve_priority3_takesPrecedenceOverPriority4() throws Exception {
    // Both .dwcdp and .zip present — .dwcdp must win
    Files.createDirectories(attemptDir());
    Path dwcdpDest = attemptDir().resolve(DATASET_UUID + "." + ATTEMPT + ".dwcdp");
    Path zipDest = attemptDir().resolve(DATASET_UUID + "." + ATTEMPT + ".zip");

    try (var in = getClass().getResourceAsStream("/fixtures/test-dataset.dwcdp")) {
      assertNotNull(in, "Fixture zip(dwcdp) not found in test resources");
      Files.copy(in, dwcdpDest);
    }
    // Presence alone is enough to prove precedence — content doesn't need to be valid
    Files.createFile(zipDest);

    DatapackagePathResolver.Resolution resolution = resolver().resolve(DATASET_UUID, ATTEMPT);

    Path expectedUnpackDir = unpackRepository
      .resolve(DATASET_UUID.toString())
      .resolve(DATASET_UUID + "." + ATTEMPT);

    assertEquals(expectedUnpackDir, resolution.unpackDirToCleanUp());
    assertTrue(Files.exists(resolution.datapackageJson()), "Could not find datapackage at: " + resolution.datapackageJson());
  }

  @Test
  void resolve_nothingFound_throwsIOExceptionWithAllCheckedPaths() {
    // No files placed at all
    IOException ex = assertThrows(IOException.class,
                                  () -> resolver().resolve(DATASET_UUID, ATTEMPT));

    String message = ex.getMessage();
    assertTrue(message.contains(DATASET_UUID.toString()), "Message should mention the dataset UUID");
    assertTrue(message.contains(String.valueOf(ATTEMPT)), "Message should mention the attempt");
  }

  @Test
  void resolve_differentAttempt_doesNotMatchOtherAttemptsFiles() throws Exception {
    // Place files for attempt 1, then request attempt 2 — should fall through to not-found
    Files.createDirectories(attemptDir());
    Files.createFile(attemptDir().resolve("datapackage.json"));

    assertThrows(IOException.class,
                 () -> resolver().resolve(DATASET_UUID, ATTEMPT + 1));
  }
}
