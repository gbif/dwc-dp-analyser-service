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

import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link RegistryClient} backed by the Java 17 {@link HttpClient}.
 * No additional dependencies required.
 */
public class HttpRegistryClient implements RegistryClient {

  private static final Logger log = LoggerFactory.getLogger(HttpRegistryClient.class);

  private static final Set<Integer> successStatusCodes = Set.of(
    200, 201, 204
  );

  private final String baseUrl;
  private final String basicAuthHeader;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public HttpRegistryClient(String baseUrl, String user, String password, ObjectMapper objectMapper) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.basicAuthHeader = "Basic " + Base64.getEncoder()
      .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = objectMapper;
  }

  @Override
  public void putValidationReport(UUID datasetKey, int attempt, DatapackageAnalysisResult result) {
    URI uri = URI.create(baseUrl + "/dataset/" + datasetKey + "/validationreport/" + attempt);

    String reportJson;
    try {
      reportJson = objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new RegistryClientException(
        "Failed to serialise validation report for dataset [" + datasetKey + "] attempt [" + attempt + "]", e);
    }

    HttpRequest request = HttpRequest.newBuilder(uri)
      .header("Content-Type", "application/json")
      .header("Authorization", basicAuthHeader)
      .PUT(HttpRequest.BodyPublishers.ofString(reportJson, StandardCharsets.UTF_8))
      .build();

    try {
      HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      if (successStatusCodes.contains(response.statusCode())) {
        log.warn("Registry PUT returned unexpected status [{}] for dataset [{}] attempt [{}]",
                 response.statusCode(), datasetKey, attempt);
      }
    } catch (Exception e) {
      throw new RegistryClientException(
        "Failed to PUT validation report for dataset [" + datasetKey + "] attempt [" + attempt + "]", e);
    }
  }
}
