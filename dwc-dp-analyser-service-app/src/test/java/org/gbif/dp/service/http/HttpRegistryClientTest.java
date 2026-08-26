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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpRegistryClientTest {

  @Test
  void testSerializationOfReport() throws JsonProcessingException {
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

    ObjectMapper mapper = Main.getObjectMapper();

    String reportSerialized = mapper.writeValueAsString(report);
    assertNotNull(reportSerialized);
  }

}
