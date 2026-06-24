/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.optimize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CredentialsProvider;
import io.camunda.zeebe.config.OptimizeProperties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Optimize REST client. A {@link CredentialsProvider} (OAuth 2.0 client_credentials) supplies the
 * bearer token for {@code /api/**} requests; it handles token fetching, caching, refresh and retry,
 * so this client only issues the API calls and measures their latency.
 */
public class OptimizeApiClient {

  static final String API_DASHBOARD_MANAGEMENT = "/api/dashboard/management";
  static final String API_DASHBOARD_INSTANT = "/api/dashboard/instant/";
  static final String API_PROCESS_OVERVIEW = "/api/process/overview";

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeApiClient.class);

  private final OptimizeProperties props;
  private final ObjectMapper objectMapper;
  private final CredentialsProvider credentials;
  private final WebClient optimizeClient;
  private final LongSupplier nowMillis;

  public OptimizeApiClient(
      final OptimizeProperties props,
      final WebClient.Builder webClientBuilder,
      final ObjectMapper objectMapper,
      final CredentialsProvider credentials) {
    this(props, webClientBuilder, objectMapper, credentials, System::currentTimeMillis);
  }

  OptimizeApiClient(
      final OptimizeProperties props,
      final WebClient.Builder webClientBuilder,
      final ObjectMapper objectMapper,
      final CredentialsProvider credentials,
      final LongSupplier nowMillis) {
    this.props = props;
    this.objectMapper = objectMapper;
    this.credentials = credentials;
    this.nowMillis = nowMillis;
    optimizeClient =
        webClientBuilder
            .clone()
            .baseUrl(props.getBaseUrl())
            .defaultHeader("X-Optimize-Client-Timezone", "UTC")
            .defaultHeader("X-Optimize-Client-Locale", "en")
            .build();
  }

  public PageEvaluationResult evaluateHomepage() {
    final TimedResponse dashboard = execute(HttpMethod.GET, API_DASHBOARD_MANAGEMENT, null);
    if (!isSuccessful(dashboard.statusCode)) {
      throw new OptimizeApiException(
          String.format(
              "Optimize management dashboard request failed with status %d", dashboard.statusCode),
          dashboard.statusCode);
    }
    return new PageEvaluationResult(
        dashboard.statusCode, dashboard.responseTimeMs, evaluateReports(dashboard.body));
  }

  public PageEvaluationResult evaluateDetailedPage(final String processDefinitionKey) {
    final TimedResponse dashboard =
        execute(HttpMethod.GET, API_DASHBOARD_INSTANT + processDefinitionKey, null);
    if (!isSuccessful(dashboard.statusCode)) {
      throw new OptimizeApiException(
          String.format(
              "Optimize instant dashboard request for key %s failed with status %d",
              processDefinitionKey, dashboard.statusCode),
          dashboard.statusCode);
    }
    return new PageEvaluationResult(
        dashboard.statusCode, dashboard.responseTimeMs, evaluateReports(dashboard.body));
  }

  public String fetchFirstProcessDefinitionKey() {
    final TimedResponse response = execute(HttpMethod.GET, API_PROCESS_OVERVIEW, null);
    if (response.statusCode != 200) {
      throw new OptimizeApiException(
          String.format(
              "Process overview request failed with status %d: %s",
              response.statusCode, response.body),
          response.statusCode);
    }
    try {
      final JsonNode rootNode = objectMapper.readTree(response.body);
      if (!rootNode.isArray() || rootNode.isEmpty()) {
        throw new OptimizeApiException(
            "No processes found in Optimize process overview response", response.statusCode);
      }
      return rootNode.get(0).get("processDefinitionKey").asText();
    } catch (final OptimizeApiException e) {
      throw e;
    } catch (final Exception e) {
      throw new OptimizeApiException(
          "Failed to parse Optimize process overview response", e, response.statusCode);
    }
  }

  List<String> extractReportIdsFromDashboard(final String dashboardBody) {
    final List<String> reportIds = new ArrayList<>();
    try {
      final JsonNode rootNode = objectMapper.readTree(dashboardBody);
      if (rootNode.has("tiles") && rootNode.get("tiles").isArray()) {
        for (final JsonNode tile : rootNode.get("tiles")) {
          if (tile.has("id")) {
            final String reportId = tile.get("id").asText();
            if (reportId != null && !reportId.isBlank()) {
              reportIds.add(reportId);
            }
          }
        }
      }
    } catch (final Exception e) {
      LOG.warn("Failed to parse dashboard body for report ids", e);
    }
    return reportIds;
  }

  private List<ReportEvaluationResult> evaluateReports(final String dashboardBody) {
    final List<ReportEvaluationResult> results = new ArrayList<>();
    for (final String reportId : extractReportIdsFromDashboard(dashboardBody)) {
      final TimedResponse response =
          execute(HttpMethod.POST, "/api/report/" + reportId + "/evaluate", "{}");
      results.add(
          new ReportEvaluationResult(
              reportId, response.statusCode, response.responseTimeMs, response.body));
    }
    return results;
  }

  private TimedResponse execute(final HttpMethod method, final String path, final String body) {
    final long startMillis = nowMillis.getAsLong();
    final WebClient.RequestBodySpec request =
        optimizeClient.method(method).uri(path).headers(this::applyBearer);
    final WebClient.RequestHeadersSpec<?> prepared =
        body == null ? request : request.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
    final TimedResponse response =
        prepared
            .exchangeToMono(
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new TimedResponse(r.statusCode().value(), b, 0L)))
            .block(props.getRequestTimeout());
    if (response == null) {
      throw new OptimizeApiException("Optimize request returned no response", 0);
    }
    return new TimedResponse(
        response.statusCode, response.body, nowMillis.getAsLong() - startMillis);
  }

  private void applyBearer(final HttpHeaders headers) {
    try {
      credentials.applyCredentials((key, value) -> headers.set(key, value));
    } catch (final IOException e) {
      throw new OptimizeApiException("Failed to obtain Optimize access token", e, 0);
    }
  }

  private static boolean isSuccessful(final int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }

  public record ReportEvaluationResult(
      String reportId, int statusCode, long responseTimeMs, String responseBody) {
    public boolean success() {
      return statusCode >= 200 && statusCode < 300;
    }
  }

  /** Result of evaluating a dashboard page (homepage or detailed) and its per-tile reports. */
  public record PageEvaluationResult(
      int dashboardStatusCode,
      long dashboardResponseTimeMs,
      List<ReportEvaluationResult> reportResults) {}

  /** Thrown when an Optimize API call returns an unexpected status or unparseable body. */
  public static class OptimizeApiException extends RuntimeException {
    private final int statusCode;

    public OptimizeApiException(final String message, final int statusCode) {
      super(message);
      this.statusCode = statusCode;
    }

    public OptimizeApiException(final String message, final Throwable cause, final int statusCode) {
      super(message, cause);
      this.statusCode = statusCode;
    }

    public int getStatusCode() {
      return statusCode;
    }
  }

  private record TimedResponse(int statusCode, String body, long responseTimeMs) {}
}
