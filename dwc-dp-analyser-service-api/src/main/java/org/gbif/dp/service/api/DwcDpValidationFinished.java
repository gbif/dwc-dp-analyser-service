package org.gbif.dp.service.api;

import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

public record DwcDpValidationFinished(
  String datasetUuid,
  int attempt,
  Boolean valid,
  DatapackageAnalysisResult validationReport
) {

}
