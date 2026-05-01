package org.gbif.dp.service.api;

import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

import java.util.Objects;

public class DwcDpValidationFinished {
  private final String datasetUuid;
  private final int attempt;
  private final Boolean valid;
  private final DatapackageAnalysisResult validationReport;

  public DwcDpValidationFinished(
    String datasetUuid,
    int attempt,
    Boolean valid,
    DatapackageAnalysisResult validationReport
  ) {
    this.datasetUuid = datasetUuid;
    this.attempt = attempt;
    this.valid = valid;
    this.validationReport = validationReport;
  }

  public String datasetUuid() {
    return datasetUuid;
  }

  public int attempt() {
    return attempt;
  }

  public Boolean valid() {
    return valid;
  }

  public DatapackageAnalysisResult validationReport() {
    return validationReport;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (DwcDpValidationFinished) obj;
    return Objects.equals(this.datasetUuid, that.datasetUuid) &&
      this.attempt == that.attempt &&
      Objects.equals(this.valid, that.valid) &&
      Objects.equals(this.validationReport, that.validationReport);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetUuid, attempt, valid, validationReport);
  }

  @Override
  public String toString() {
    return "DwcDpValidationFinished[" +
      "datasetUuid=" + datasetUuid + ", " +
      "attempt=" + attempt + ", " +
      "valid=" + valid + ", " +
      "validationReport=" + validationReport + ']';
  }


}
