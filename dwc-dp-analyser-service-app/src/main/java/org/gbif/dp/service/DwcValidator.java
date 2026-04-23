package org.gbif.dp.service;

import org.gbif.dp.analysis.AnalysisFeature;
import org.gbif.dp.analysis.DataPackageAnalyser;
import org.gbif.dp.analysis.ValidationOptions;
import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

import org.gbif.dp.duckdb.DefaultDuckDbConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class DwcValidator implements Validator {

  private static final Logger log = LoggerFactory.getLogger(DwcValidator.class);

  private static final List<AnalysisFeature> ONLY_VALIDATION = List.of(
    AnalysisFeature.PRIMARY_KEY_UNIQUE,
    AnalysisFeature.DATA_TYPE_CONSTRAINT,
    AnalysisFeature.FOREIGN_KEY_CONSTRAINT
  );

  private final Path archiveRepository;
  private final Path unpackRepository;

  private final DataPackageAnalyser validator;

  public DwcValidator(DataPackageAnalyser validator, Path archiveRepository, Path unpackRepository) {
    this.archiveRepository = archiveRepository;
    this.unpackRepository = unpackRepository;
    this.validator = validator;
  }

  @Override
  public DatapackageAnalysisResult validate(ValidationRequest request) throws Exception {
    // {archiveRepository}/{datasetUuid}/{datasetUuid}.{attempt}.dwcdp
    Path zipFile = archiveRepository
      .resolve(request.datasetUuid())
      .resolve(request.datasetUuid() + "." + request.attempt() + ".dwcdp");

    // {unpackRepository}/{datasetUuid}/{datasetUuid}.{attempt}/
    Path unpackDir = unpackRepository
      .resolve(request.datasetUuid())
      .resolve(request.datasetUuid() + "." + request.attempt());

    log.info("Unzipping [{}] to [{}]", zipFile, unpackDir);
    ZipUtils.unzip(zipFile, unpackDir);

    return validator.analyse(unpackDir.resolve("datapackage.json"), ValidationOptions.defaults(), ONLY_VALIDATION);
  }
}
