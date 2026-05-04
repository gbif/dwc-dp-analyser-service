package org.gbif.dp.service.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

import java.util.Objects;
import java.util.UUID;

public class DwcDpValidationFinished {
  private final UUID datasetUuid;
  private final int attempt;
  private final boolean valid;
  private final DatapackageAnalysisResult validationReport;

  @JsonCreator
  public DwcDpValidationFinished(
    @JsonProperty("datasetUuid") UUID datasetUuid,
    @JsonProperty("attempt")        int attempt,
    @JsonProperty("valid")          Boolean valid,
    @JsonProperty("validationReport") DatapackageAnalysisResult validationReport
  ) {
    Objects.requireNonNull(datasetUuid, "datasetUuid is null");
    Objects.requireNonNull(validationReport, "validationReport is null");
    if (attempt < 1) {
      throw new IllegalArgumentException("attempt must be >= 1");
    }
    this.datasetUuid = datasetUuid;
    this.attempt = attempt;
    this.valid = valid;
    this.validationReport = validationReport;
  }

  public UUID getDatasetUuid() {
    return datasetUuid;
  }

  public int getAttempt() {
    return attempt;
  }

  public Boolean isValid() {
    return valid;
  }

  public DatapackageAnalysisResult getValidationReport() {
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
