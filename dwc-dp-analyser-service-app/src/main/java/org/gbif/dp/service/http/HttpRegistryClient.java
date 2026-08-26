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
import org.gbif.dp.analysis.api.DatapackageAnalysisResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
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

  private static final Set<Integer> SUCCESS_STATUS_CODES = Set.of(
    200, 201, 204
  );

  private final String baseUrl;
  private final String basicAuthHeader;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final RetryConfig retryConfig;

  public HttpRegistryClient(
    String baseUrl,
    String user,
    String password,
    ObjectMapper objectMapper,
    RetryConfig retryConfig
  ) {
    Objects.requireNonNull(baseUrl, "baseUrl");
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(password, "password");

    this.baseUrl = baseUrl.endsWith("/")
      ? baseUrl.substring(0, baseUrl.length() - 1)
      : baseUrl;

    this.basicAuthHeader = "Basic " + Base64.getEncoder()
      .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));

    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.retryConfig = Objects.requireNonNull(retryConfig, "retryConfig");
  }

  @Override
  public void putValidationReport(
    UUID datasetKey,
    int attempt,
    AnalysisExecution<DatapackageAnalysisResult> result
  ) {
    URI uri = URI.create(
      baseUrl + "/dataset/" + datasetKey + "/validationreport/" + attempt
    );

    String reportJson;
    try {
      reportJson = objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new RegistryClientException(
        "Failed to serialise validation report for dataset ["
        + datasetKey
        + "] attempt ["
        + attempt
        + "]",
        e
      );
    }

    HttpRequest request = HttpRequest.newBuilder(uri)
      .header("Content-Type", "application/json")
      .header("Authorization", basicAuthHeader)
      .PUT(HttpRequest.BodyPublishers.ofString(reportJson, StandardCharsets.UTF_8))
      .build();

    sendWithRetry(request, datasetKey, attempt);
  }

  private void sendWithRetry(
    HttpRequest request,
    UUID datasetKey,
    int attempt
  ) {
    SendResult result = send(request, datasetKey, attempt);

    for (int retry = 0; ; retry++) {
      if (result.successful()) {
        log.debug(
          "Posted validation report for dataset [{}] attempt [{}]",
          datasetKey,
          attempt
        );
        return;
      }

      if (!result.retryable() || retry >= retryConfig.retries()) {
        throw failure(result, datasetKey, attempt, retry);
      }

      sleepBeforeRetry(retry, result, datasetKey, attempt);
      result = send(request, datasetKey, attempt);
    }
  }

  private SendResult send(
    HttpRequest request,
    UUID datasetKey,
    int attempt
  ) {
    try {
      HttpResponse<Void> response = httpClient.send(
        request,
        HttpResponse.BodyHandlers.discarding()
      );

      int statusCode = response.statusCode();
      boolean successful = SUCCESS_STATUS_CODES.contains(statusCode);

      return new SendResult(
        successful,
        isRetryableStatus(statusCode),
        statusCode,
        null
      );
    } catch (IOException e) {
      return new SendResult(false, true, null, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();

      throw new RegistryClientException(
        "Interrupted while PUTting validation report for dataset ["
        + datasetKey
        + "] attempt ["
        + attempt
        + "]",
        e
      );
    }
  }

  private void sleepBeforeRetry(
    int retry,
    SendResult result,
    UUID datasetKey,
    int attempt
  ) {
    Duration delay = retryConfig.delayForRetry(retry);

    log.warn(
      "Registry PUT {} for dataset [{}] attempt [{}]. Retrying in [{}] ms ({}/{})",
      result.description(),
      datasetKey,
      attempt,
      delay.toMillis(),
      retry + 1,
      retryConfig.retries(),
      result.exception()
    );

    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();

      throw new RegistryClientException(
        "Interrupted while waiting to retry validation report for dataset ["
        + datasetKey
        + "] attempt ["
        + attempt
        + "]",
        e
      );
    }
  }

  private RegistryClientException failure(
    SendResult result,
    UUID datasetKey,
    int attempt,
    int retries
  ) {
    String message =
      "Failed to PUT validation report for dataset ["
      + datasetKey
      + "] attempt ["
      + attempt
      + "] after ["
      + retries
      + "] retries: "
      + result.description();

    return new RegistryClientException(message, result.exception());
  }

  private static boolean isRetryableStatus(int statusCode) {
    return statusCode == 429
           || statusCode >= 500 && statusCode < 600;
  }

  private record SendResult(
    boolean successful,
    boolean retryable,
    Integer statusCode,
    IOException exception
  ) {

    private String description() {
      return statusCode != null
        ? "returned HTTP status [" + statusCode + "]"
        : "failed with an I/O error";
    }
  }
}
