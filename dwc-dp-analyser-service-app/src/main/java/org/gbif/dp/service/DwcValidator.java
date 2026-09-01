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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DwcValidator implements Validator {

  private static final Logger log = LoggerFactory.getLogger(DwcValidator.class);

  private static final List<AnalysisFeature> ONLY_VALIDATION = List.of(
    AnalysisFeature.PRIMARY_KEY_UNIQUE,
    AnalysisFeature.DATA_TYPE_CONSTRAINT,
    AnalysisFeature.FOREIGN_KEY_CONSTRAINT,
    AnalysisFeature.EML_VALIDATION,
    AnalysisFeature.DESCRIPTOR_VALIDATION
  );

  private final DataPackageAnalysisOrchestrator validator;
  private final DatapackagePathResolver pathResolver;
  private final DwcValidatorConfig config;

  public DwcValidator(DataPackageAnalysisOrchestrator validator, DatapackagePathResolver pathResolver,
                      DwcValidatorConfig config
  ) {
    this.validator = validator;
    this.pathResolver = pathResolver;
    this.config = config;
  }

  @Override
  public AnalysisExecution<DatapackageAnalysisResult> validate(ValidationRequest request) throws Exception {
    Path unpackDir = null;

    try {
      DatapackagePathResolver.Resolution resolution = pathResolver.resolve(
        request.datasetUuid(), request.attempt());
      unpackDir = resolution.unpackDirToCleanUp();

      log.debug("Running analysis for dataset [{}]", request.datasetUuid());
      AnalysisExecution<DatapackageAnalysisResult> analysisResult = validator.analyseWithFullReport(
        resolution.datapackageJson().toString(), config.validationOptions(), AnalysisFeature.ALL_FEATURES);
      log.debug("Validated [{}], valid: [{}]", request.datasetUuid(), analysisResult.metadata().isValid());
      return analysisResult;
    } catch (Exception e) {
      throw new RuntimeException(String.format("Runtime error - Unzip/Validation failed for dataset [%s]", request.datasetUuid()), e);
    } finally {
      if (unpackDir != null) {
        Path parentDir = unpackDir.getParent();
        if (Files.exists(parentDir)) {
          log.debug("Cleaning up [{}]", parentDir);
          deleteFolder(parentDir);
        }
      }
    }
  }

  private void deleteFolder(Path folder) throws IOException {
    try (var stream = Files.walk(folder)) {
      stream.sorted(Comparator.reverseOrder())
        .forEach(p -> {
          try { Files.delete(p); }
          catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }
  }

  public record DwcValidatorConfig(ValidationOptions validationOptions) {
  }
}
