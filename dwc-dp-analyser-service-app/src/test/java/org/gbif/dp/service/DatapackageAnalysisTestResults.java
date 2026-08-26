/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.dp.service;

import org.gbif.dp.analysis.api.AnalysisExecution;
import org.gbif.dp.analysis.api.AnalysisFeature;
import org.gbif.dp.analysis.api.AnalysisMetadata;
import org.gbif.dp.analysis.api.DatapackageAnalysisResult;
import org.gbif.dp.analysis.api.PrimaryKeyViolation;
import org.gbif.dp.analysis.api.ResourceAnalysisResult;
import org.gbif.dp.validator.api.DescriptorValidationResult;
import org.gbif.dp.validator.api.EmlValidationResult;

import java.time.LocalDateTime;
import java.util.List;

public class DatapackageAnalysisTestResults {
  public static AnalysisExecution<DatapackageAnalysisResult> invalidResult() {
    List<ResourceAnalysisResult> resourceAnalysisResults = List.of(new ResourceAnalysisResult(
      "test",
      List.of(),
      new PrimaryKeyViolation("test", List.of("id"), 1, List.of()),
      List.of(),
      List.of(),
      1));
    return new AnalysisExecution<>(
      new DatapackageAnalysisResult(DescriptorValidationResult.ok(), EmlValidationResult.absent(), resourceAnalysisResults),
      new AnalysisMetadata(LocalDateTime.now(), LocalDateTime.now(), AnalysisFeature.ALL_FEATURES, false)
    );
  }

  public static AnalysisExecution<DatapackageAnalysisResult> validResult() {
    return
      new AnalysisExecution<>(
        new DatapackageAnalysisResult(DescriptorValidationResult.ok(), EmlValidationResult.absent(), List.of()),
        new AnalysisMetadata(LocalDateTime.now(), LocalDateTime.now(), AnalysisFeature.ALL_FEATURES, true)
      );
  }
}
