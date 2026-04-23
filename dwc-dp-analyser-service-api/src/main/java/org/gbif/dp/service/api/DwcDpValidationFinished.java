package org.gbif.dp.service.api;

public record DwcDpValidationFinished(
  String datasetUuid,
  int attempt
) {

}
