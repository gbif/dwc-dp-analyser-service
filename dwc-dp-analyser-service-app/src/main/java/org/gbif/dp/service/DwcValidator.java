package org.gbif.dp.service;

import org.gbif.dp.analysis.api.AnalysisFeature;
import org.gbif.dp.analysis.api.DataPackageAnalysisOrchestrator;
import org.gbif.dp.analysis.api.ValidationOptions;
import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

import org.gbif.dp.duckdb.DuckDbConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class DwcValidator implements Validator {

  private static final Logger log = LoggerFactory.getLogger(DwcValidator.class);

  private static final String DATAPACKAGE_JSON = "datapackage.json";

  private static final List<AnalysisFeature> ONLY_VALIDATION = List.of(
    AnalysisFeature.PRIMARY_KEY_UNIQUE,
    AnalysisFeature.DATA_TYPE_CONSTRAINT,
    AnalysisFeature.FOREIGN_KEY_CONSTRAINT,
    AnalysisFeature.EML_VALIDATION,
    AnalysisFeature.DESCRIPTOR_VALIDATION
  );

  private final DataPackageAnalysisOrchestrator validator;
  private final DwcValidatorConfig config;

  public DwcValidator(DataPackageAnalysisOrchestrator validator, DwcValidatorConfig config) {
    this.validator = validator;
    this.config = config;
  }

  @Override
  public DatapackageAnalysisResult validate(ValidationRequest request) throws Exception {
    Path unpackDir = null;

    try {
      Path datasetPath = config.archiveRepository().resolve(request.datasetUuid().toString());
      Path unpackedDatapackage = datasetPath.resolve(DATAPACKAGE_JSON);
      boolean isUnpacked = Files.exists(unpackedDatapackage);

      if (!isUnpacked) {
        // {archiveRepository}/{datasetUuid}/{datasetUuid}.{attempt}.dwcdp
        Path zipFile = datasetPath
          .resolve(request.datasetUuid() + "." + request.attempt() + ".dwcdp");

        // {unpackRepository}/{datasetUuid}/{datasetUuid}.{attempt}/
        unpackDir = config.unpackRepository()
          .resolve(request.datasetUuid().toString())
          .resolve(request.datasetUuid() + "." + request.attempt());

        log.debug("Unzipping [{}] to [{}]", zipFile, unpackDir);
        ZipUtils.unzip(zipFile, unpackDir);

        unpackedDatapackage = unpackDir.resolve(DATAPACKAGE_JSON);
      } else {
        log.debug("datapackage [{}] already unpacked", unpackedDatapackage);
      }

      log.debug("Running analysis for dataset [{}]", request.datasetUuid());
      DatapackageAnalysisResult analysisResult = validator.analyse(unpackedDatapackage, ValidationOptions.defaults(), ONLY_VALIDATION);
      log.debug("Validated [{}], valid: [{}]", request.datasetUuid(), DatapackageAnalysisResult.isValid(analysisResult));
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

  public record DwcValidatorConfig(Path archiveRepository, Path unpackRepository, ValidationOptions validationOptions) {
  }
}
