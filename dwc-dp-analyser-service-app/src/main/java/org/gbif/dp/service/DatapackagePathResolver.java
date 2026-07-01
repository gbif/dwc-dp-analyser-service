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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the on-disk location of a DwC-DP datapackage for a given dataset/attempt,
 * trying multiple known layouts in priority order. Unzips into {@code unpackRepository}
 * when only an archive is found.
 *
 * Priority:
 * 1. {archiveRepository}/{uuid}/{uuid}.{attempt}/datapackage.json   (already unpacked, this attempt)
 * 2. {archiveRepository}/{uuid}/datapackage.json                    (already unpacked, latest pattern)
 * 3. {archiveRepository}/{uuid}/{uuid}.{attempt}/{uuid}.{attempt}.dwcdp
 * 4. {archiveRepository}/{uuid}/{uuid}.{attempt}/{uuid}.{attempt}.zip
 */
public class DatapackagePathResolver {

  private static final Logger log = LoggerFactory.getLogger(DatapackagePathResolver.class);
  private static final String DATAPACKAGE_JSON = "datapackage.json";

  private final Path archiveRepository;
  private final Path unpackRepository;

  public DatapackagePathResolver(Path archiveRepository, Path unpackRepository) {
    this.archiveRepository = archiveRepository;
    this.unpackRepository = unpackRepository;
  }

  /**
   * Result of resolution: the path to the datapackage.json to validate, and — if an
   * archive had to be unzipped — the directory that should be cleaned up afterwards.
   */
  public record Resolution(Path datapackageJson, Path unpackDirToCleanUp) {
  }

  public Resolution resolve(UUID datasetUuid, int attempt) throws IOException {
    Path datasetDir = archiveRepository.resolve(datasetUuid.toString());
    String attemptName = datasetUuid + "." + attempt;

    // 1. {uuid}/{uuid}.{attempt}/datapackage.json
    Path attemptUnpacked = datasetDir.resolve(attemptName).resolve(DATAPACKAGE_JSON);
    if (Files.exists(attemptUnpacked)) {
      log.debug("Resolved via attempt-specific unpacked datapackage [{}]", attemptUnpacked);
      return new Resolution(attemptUnpacked, null);
    }

    // 2. {uuid}/datapackage.json (latest pattern)
    Path latestUnpacked = datasetDir.resolve(DATAPACKAGE_JSON);
    if (Files.exists(latestUnpacked)) {
      log.debug("Resolved via latest-pattern unpacked datapackage [{}]", latestUnpacked);
      return new Resolution(latestUnpacked, null);
    }

    // 3. {uuid}/{uuid}.{attempt}/{uuid}.{attempt}.dwcdp
    Path dwcdpArchive = datasetDir.resolve(attemptName).resolve(attemptName + ".dwcdp");
    if (Files.exists(dwcdpArchive)) {
      return unzipAndResolve(datasetUuid, attemptName, dwcdpArchive);
    }

    // 4. {uuid}/{uuid}.{attempt}/{uuid}.{attempt}.zip
    Path zipArchive = datasetDir.resolve(attemptName).resolve(attemptName + ".zip");
    if (Files.exists(zipArchive)) {
      return unzipAndResolve(datasetUuid, attemptName, zipArchive);
    }

    throw new IOException(String.format(
      "No datapackage found for dataset [%s] attempt [%d] under [%s] — "
      + "checked unpacked (attempt-specific, latest) and archived (.dwcdp, .zip)",
      datasetUuid, attempt, datasetDir));
  }

  private Resolution unzipAndResolve(UUID datasetUuid, String attemptName, Path archiveFile) throws IOException {
    Path unpackDir = unpackRepository.resolve(datasetUuid.toString()).resolve(attemptName);
    log.debug("Unzipping [{}] to [{}]", archiveFile, unpackDir);
    ZipUtils.unzip(archiveFile, unpackDir);
    return new Resolution(unpackDir.resolve(DATAPACKAGE_JSON), unpackDir);
  }
}
