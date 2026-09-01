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
package org.gbif.dp.service.http;

import org.gbif.dp.analysis.api.AnalysisExecution;
import org.gbif.dp.analysis.api.AnalysisFeature;
import org.gbif.dp.analysis.api.AnalysisMetadata;
import org.gbif.dp.analysis.api.ColumnStatistics;
import org.gbif.dp.analysis.api.DataTypeViolation;
import org.gbif.dp.analysis.api.DatapackageAnalysisResult;
import org.gbif.dp.analysis.api.ForeignKeyViolation;
import org.gbif.dp.analysis.api.PrimaryKeyViolation;
import org.gbif.dp.analysis.api.ResourceAnalysisResult;
import org.gbif.dp.service.Main;
import org.gbif.dp.validator.api.DescriptorValidationResult;
import org.gbif.dp.validator.api.DescriptorViolationType;
import org.gbif.dp.validator.api.EmlValidationResult;
import org.gbif.dp.validator.api.ValidationIssue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class HttpRegistryClientTest {

  @Test
  void testSerializationOfReport() throws JsonProcessingException {
    AnalysisExecution<DatapackageAnalysisResult> report = getDatapackageAnalysisResultAnalysisExecution();

    ObjectMapper mapper = Main.getObjectMapper();

    String reportSerialized = mapper.writeValueAsString(report);
    assertNotNull(reportSerialized);
  }

  @Test
  void testSerializedDateFormat() throws JsonProcessingException {
    AnalysisExecution<DatapackageAnalysisResult> report = getDatapackageAnalysisResultAnalysisExecution();
    ObjectMapper mapper = Main.getObjectMapper();

    String reportSerialized = mapper.writeValueAsString(report);
    JsonNode root = mapper.readTree(reportSerialized);

    String iso8601MillisUtcPattern = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00$";

    JsonNode metadata = root.get("metadata");
    assertNotNull(metadata, "metadata should be present in serialized report");

    String started = metadata.get("started").asText();
    String finished = metadata.get("finished").asText();

    assertTrue(started.matches(iso8601MillisUtcPattern),
               "started should be ISO-8601 with millis+00:00, was: " + started);
    assertTrue(finished.matches(iso8601MillisUtcPattern),
               "finished should be ISO-8601 with millis+00:00, was: " + finished);
  }

  private static AnalysisExecution<DatapackageAnalysisResult> getDatapackageAnalysisResultAnalysisExecution() {
    DatapackageAnalysisResult result = new DatapackageAnalysisResult(
      new DescriptorValidationResult(
        List.of(
          new ValidationIssue(
            ValidationIssue.Severity.WARNING,
            DescriptorViolationType.UNKNOWN_FIELD_TYPE,
            "Test Message",
            "some/location",
            "Some detail")
        ),
        true,
        true
      ),
      new EmlValidationResult(true, List.of(
        new ValidationIssue(
          ValidationIssue.Severity.WARNING,
          DescriptorViolationType.EML_XSD_VIOLATION,
          "Test msg",
          "location/at/point",
          "information")
      ), true),
      List.of(
        new ResourceAnalysisResult(
          "name",
          List.of(
            new ForeignKeyViolation(
              "resource",
              List.of("f1", "f2"),
              "other resource",
              List.of("some field"),
              2,
              List.of(
                Map.of("A", "B")
              )
            )
          ),
          new PrimaryKeyViolation(
            "resource",
            List.of("f1", "f2"),
            1,
            List.of(
              Map.of("a", "b")
            )
          ),
          List.of(
            new DataTypeViolation(
              "resource",
              "field",
              "type",
              5,
              List.of("1", "2", "3", "4", "5")
            )
          ),
          List.of(
            new ColumnStatistics(
              "Stats", 10, 10
            )
          ),
          10
        )
      )
    );
    AnalysisMetadata analysisMetadata =
      new AnalysisMetadata(LocalDateTime.now(), LocalDateTime.now(), AnalysisFeature.ALL_FEATURES, true);

    AnalysisExecution<DatapackageAnalysisResult> report =
        new AnalysisExecution<>(
          result, analysisMetadata);
    return report;
  }

}
