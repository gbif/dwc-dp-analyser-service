package org.gbif.dp.service;

import org.gbif.dp.analysis.api.ResourceAnalysisResult;
import org.gbif.dp.analysis.api.DatapackageAnalysisResult;
import org.gbif.dp.analysis.api.PrimaryKeyViolation;

import java.util.List;

public class DatapackageAnalysisTestResults {
  public static DatapackageAnalysisResult invalidResult() {
    List<ResourceAnalysisResult> resourceAnalysisResults = List.of(new ResourceAnalysisResult(
      "test",
      List.of(),
      new PrimaryKeyViolation("test", List.of("id"), 1, List.of()),
      List.of(),
      List.of(),
      1));
    return new DatapackageAnalysisResult(resourceAnalysisResults);
  }

  public static DatapackageAnalysisResult validResult() {
    return new DatapackageAnalysisResult(List.of());
  }
}
