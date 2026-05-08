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
    // {archiveRepository}/{datasetUuid}/{datasetUuid}.{attempt}.dwcdp
    Path zipFile = config.archiveRepository()
      .resolve(request.datasetUuid().toString())
      .resolve(request.datasetUuid() + "." + request.attempt() + ".dwcdp");

    // {unpackRepository}/{datasetUuid}/{datasetUuid}.{attempt}/
    Path unpackDir = config.unpackRepository()
      .resolve(request.datasetUuid().toString())
      .resolve(request.datasetUuid() + "." + request.attempt());

    try {
      log.debug("Unzipping [{}] to [{}]", zipFile, unpackDir);
      ZipUtils.unzip(zipFile, unpackDir);

      log.debug("Running analysis for dataset [{}]", request.datasetUuid());
      DatapackageAnalysisResult analysisResult = validator.analyse(unpackDir.resolve("datapackage.json"), ValidationOptions.defaults(), ONLY_VALIDATION);
      log.debug("Validated [{}], valid: [{}]", request.datasetUuid(), DatapackageAnalysisResult.isValid(analysisResult));
      return analysisResult;
    } catch (Exception e) {
      log.error("Runtime error - Validation failed for dataset [{}]", request.datasetUuid(), e);
      throw e;
    } finally {
      Path parentDir = unpackDir.getParent();
      if (Files.exists(parentDir)) {
        log.debug("Cleaning up [{}]", parentDir);
        deleteFolder(parentDir);
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
