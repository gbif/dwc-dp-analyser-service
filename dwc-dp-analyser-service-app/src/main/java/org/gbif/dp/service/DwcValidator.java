package org.gbif.dp.service;

import org.gbif.dp.analysis.api.AnalysisFeature;
import org.gbif.dp.analysis.api.DataPackageAnalysisOrchestrator;
import org.gbif.dp.analysis.api.ValidationOptions;
import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

import org.gbif.dp.duckdb.DuckDbConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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

    log.info("Unzipping [{}] to [{}]", zipFile, unpackDir);
    ZipUtils.unzip(zipFile, unpackDir);

    return validator.analyse(unpackDir.resolve("datapackage.json"), ValidationOptions.defaults(), ONLY_VALIDATION);
  }

  public record DwcValidatorConfig(Path archiveRepository, Path unpackRepository, ValidationOptions validationOptions) {

    public static ValidationOptions withDuckDbConfig(DuckDbConfig config) {
      ValidationOptions defaultOptions = ValidationOptions.defaults();
      return new ValidationOptions(
        defaultOptions.sampleSize()
      );
    }
  }
}
