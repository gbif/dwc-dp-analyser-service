package org.gbif.dp.service;

import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

@FunctionalInterface
public interface Validator {
  DatapackageAnalysisResult validate(ValidationRequest request) throws Exception;
}
