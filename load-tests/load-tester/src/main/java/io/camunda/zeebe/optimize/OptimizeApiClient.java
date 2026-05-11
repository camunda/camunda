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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.config.OptimizeProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Talks to a deployed Optimize via its REST API. Authenticates with Keycloak using the password
 * grant flow (requires Optimize {@code api.jwtAuthForApiEnabled=true}) and uses the resulting JWT
 * as a bearer token on every Optimize request.
 *
 * <p>Not a Spring bean — owned by {@link OptimizeReportEvaluator} so tests can construct it with a
 * fake {@link WebClient.Builder}.
 */
public class OptimizeApiClient {

  static final String API_DASHBOARD_MANAGEMENT = "/api/dashboard/management";
  static final String API_DASHBOARD_INSTANT = "/api/dashboard/instant/";
  static final String API_REPORT_EVALUATE = "/api/report/evaluate";
  static final String API_PROCESS_OVERVIEW = "/api/process/overview";

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeApiClient.class);

  private final OptimizeProperties props;
  private final ObjectMapper objectMapper;
  private final WebClient optimizeClient;
  private final WebClient keycloakClient;
  private final LongSupplier nowMillis;

  private String accessToken;
  private long tokenExpiresAtMillis;

  public OptimizeApiClient(
      final OptimizeProperties props,
      final WebClient.Builder webClientBuilder,
      final ObjectMapper objectMapper) {
    this(props, webClientBuilder, objectMapper, System::currentTimeMillis);
  }

  OptimizeApiClient(
      final OptimizeProperties props,
      final WebClient.Builder webClientBuilder,
      final ObjectMapper objectMapper,
      final LongSupplier nowMillis) {
    this.props = props;
    this.objectMapper = objectMapper;
    this.nowMillis = nowMillis;
    optimizeClient =
        webClientBuilder
            .clone()
            .baseUrl(props.getBaseUrl())
            .defaultHeader("X-Optimize-Client-Timezone", "UTC")
            .defaultHeader("X-Optimize-Client-Locale", "en")
            .build();
    keycloakClient = webClientBuilder.clone().baseUrl(props.getKeycloakUrl()).build();
  }

  public void authenticateWithPasswordGrant() {
    final String body =
        "grant_type=password"
            + "&client_id="
            + URLEncoder.encode(props.getClientId(), StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(props.getClientSecret(), StandardCharsets.UTF_8)
            + "&username="
            + URLEncoder.encode(props.getUsername(), StandardCharsets.UTF_8)
            + "&password="
            + URLEncoder.encode(props.getPassword(), StandardCharsets.UTF_8)
            + "&scope=openid";

    final TimedResponse response =
        keycloakClient
            .post()
            .uri("/auth/realms/{realm}/protocol/openid-connect/token", props.getRealm())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(body)
            .exchangeToMono(
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new TimedResponse(r.statusCode().value(), b, 0L)))
            .block(props.getRequestTimeout());

    if (response == null || response.statusCode != 200) {
      final int status = response == null ? 0 : response.statusCode;
      final String responseBody = response == null ? "<no response>" : response.body;
      throw new OptimizeAuthException(
          String.format("Password grant failed with status %d: %s", status, responseBody), status);
    }

    try {
      final JsonNode jsonNode = objectMapper.readTree(response.body);
      accessToken = jsonNode.get("access_token").asText();
      final int expiresIn = jsonNode.get("expires_in").asInt();
      tokenExpiresAtMillis = nowMillis.getAsLong() + (expiresIn * 1000L);
    } catch (final Exception e) {
      throw new OptimizeAuthException("Failed to parse Keycloak token response", e);
    }
    LOG.debug("Successfully authenticated with Keycloak via password grant");
  }

  public void ensureValidToken() {
    if (accessToken == null
        || nowMillis.getAsLong() >= tokenExpiresAtMillis - props.getTokenRefreshSkew().toMillis()) {
      LOG.debug("Optimize token expired or missing, re-authenticating");
      authenticateWithPasswordGrant();
    }
  }

  public HomepageResult evaluateHomepage() {
    ensureValidToken();
    final TimedResponse dashboard = executeGet(API_DASHBOARD_MANAGEMENT);
    if (dashboard.statusCode < 200 || dashboard.statusCode >= 300) {
      throw new OptimizeApiException(
          String.format(
              "Optimize management dashboard request failed with status %d", dashboard.statusCode),
          dashboard.statusCode);
    }
    final List<ReportEvaluationResult> reports = fetchAllReportMetrics(dashboard.body);
    return new HomepageResult(dashboard.statusCode, dashboard.responseTimeMs, reports);
  }

  public DetailedPageResult evaluateDetailedPage(final String processDefinitionKey) {
    ensureValidToken();
    final TimedResponse dashboard = executeGet(API_DASHBOARD_INSTANT + processDefinitionKey);
    if (dashboard.statusCode < 200 || dashboard.statusCode >= 300) {
      throw new OptimizeApiException(
          String.format(
              "Optimize instant dashboard request for key %s failed with status %d",
              processDefinitionKey, dashboard.statusCode),
          dashboard.statusCode);
    }
    final List<ReportEvaluationResult> reports = fetchAllReportMetrics(dashboard.body);

    final List<ReportEvaluationResult> detailed = new ArrayList<>();
    for (final ReportEvaluationResult report : reports) {
      if (!report.success()) {
        continue;
      }
      detailed.add(fetchDetailedReportMetrics(report.reportId(), report.responseBody()));
    }
    return new DetailedPageResult(
        dashboard.statusCode, dashboard.responseTimeMs, reports, detailed);
  }

  public String fetchFirstProcessDefinitionKey() {
    ensureValidToken();
    final TimedResponse response = executeGet(API_PROCESS_OVERVIEW);
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

  public List<String> extractReportIdsFromDashboard(final String dashboardBody) {
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

  String transformForDetailedEvaluate(final String responseBody) {
    try {
      final JsonNode rootNode = objectMapper.readTree(responseBody);
      if (!(rootNode instanceof final ObjectNode root)) {
        return responseBody;
      }
      root.remove("result");

      final JsonNode data = root.path("data");
      if (!(data instanceof final ObjectNode dataObj)) {
        return objectMapper.writeValueAsString(root);
      }

      final JsonNode view = dataObj.path("view");
      if (view instanceof final ObjectNode viewObj) {
        viewObj.remove("entity");
        if (viewObj.has("properties")) {
          viewObj.set("properties", objectMapper.createArrayNode().add("rawData"));
        }
      }

      final JsonNode groupBy = dataObj.path("groupBy");
      if (groupBy instanceof final ObjectNode groupByObj) {
        groupByObj.put("type", "none");
        groupByObj.remove("value");
      }

      final JsonNode sorting = dataObj.path("configuration").path("sorting");
      if (sorting instanceof final ObjectNode sortingObj) {
        sortingObj.put("by", "startDate");
      }

      return objectMapper.writeValueAsString(root);
    } catch (final Exception e) {
      LOG.warn("Failed to transform report body for detailed evaluate, sending original", e);
      return responseBody;
    }
  }

  String getAccessTokenForTesting() {
    return accessToken;
  }

  long getTokenExpiresAtForTesting() {
    return tokenExpiresAtMillis;
  }

  private List<ReportEvaluationResult> fetchAllReportMetrics(final String dashboardBody) {
    final List<String> reportIds = extractReportIdsFromDashboard(dashboardBody);
    final List<ReportEvaluationResult> results = new ArrayList<>();
    for (final String reportId : reportIds) {
      results.add(fetchReportMetrics(reportId));
    }
    return results;
  }

  private ReportEvaluationResult fetchReportMetrics(final String reportId) {
    ensureValidToken();
    final TimedResponse response = executePost("/api/report/" + reportId + "/evaluate", "{}");
    return new ReportEvaluationResult(
        reportId, response.statusCode, response.responseTimeMs, response.body);
  }

  private ReportEvaluationResult fetchDetailedReportMetrics(
      final String reportId, final String responseBody) {
    ensureValidToken();
    final String transformedBody = transformForDetailedEvaluate(responseBody);
    final TimedResponse response = executePost(API_REPORT_EVALUATE, transformedBody);
    return new ReportEvaluationResult(
        reportId, response.statusCode, response.responseTimeMs, response.body);
  }

  private TimedResponse executeGet(final String path) {
    final long startMillis = nowMillis.getAsLong();
    final TimedResponse response =
        optimizeClient
            .get()
            .uri(path)
            .headers(h -> h.setBearerAuth(accessToken))
            .exchangeToMono(
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new TimedResponse(r.statusCode().value(), b, 0L)))
            .block(props.getRequestTimeout());
    return withElapsed(response, startMillis);
  }

  private TimedResponse executePost(final String path, final String body) {
    final long startMillis = nowMillis.getAsLong();
    final TimedResponse response =
        optimizeClient
            .post()
            .uri(path)
            .headers(h -> h.setBearerAuth(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchangeToMono(
                r ->
                    r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new TimedResponse(r.statusCode().value(), b, 0L)))
            .block(props.getRequestTimeout());
    return withElapsed(response, startMillis);
  }

  private TimedResponse withElapsed(final TimedResponse response, final long startMillis) {
    if (response == null) {
      throw new OptimizeApiException("Optimize request returned no response", 0);
    }
    return new TimedResponse(
        response.statusCode, response.body, nowMillis.getAsLong() - startMillis);
  }

  public record ReportEvaluationResult(
      String reportId, int statusCode, long responseTimeMs, String responseBody) {
    public boolean success() {
      return statusCode >= 200 && statusCode < 300;
    }
  }

  public record HomepageResult(
      int dashboardStatusCode,
      long dashboardResponseTimeMs,
      List<ReportEvaluationResult> reportResults) {}

  public record DetailedPageResult(
      int dashboardStatusCode,
      long dashboardResponseTimeMs,
      List<ReportEvaluationResult> reportEvaluationResults,
      List<ReportEvaluationResult> detailedEvaluationResults) {}

  /** Thrown when Keycloak password-grant authentication fails. */
  public static class OptimizeAuthException extends RuntimeException {
    private final int statusCode;

    public OptimizeAuthException(final String message, final int statusCode) {
      super(message);
      this.statusCode = statusCode;
    }

    public OptimizeAuthException(final String message, final Throwable cause) {
      super(message, cause);
      statusCode = 0;
    }

    public int getStatusCode() {
      return statusCode;
    }
  }

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
