/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.config.OptimizeCfg;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizeReportApiClient implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(OptimizeReportApiClient.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  // API paths
  private static final String API_DASHBOARD_MANAGEMENT = "/api/dashboard/management";
  private static final String API_DASHBOARD_INSTANT = "/api/dashboard/instant/";
  private static final String API_REPORT_EVALUATE = "/api/report/evaluate";
  private static final String API_PROCESS_OVERVIEW = "/api/process/overview";

  // Auth
  private static final String AUTH_COOKIE_NAME = "X-Optimize-Authorization_0";
  private static final long TOKEN_REFRESH_BUFFER_MS = 30_000;

  private final HttpClient httpClient;
  private final String optimizeBaseUrl;
  private final String keycloakBaseUrl;
  private final String realm;
  private final String clientId;
  private final String username;
  private final String password;
  private final String clientSecret;

  private String accessToken;
  private long tokenExpiresAt;

  public OptimizeReportApiClient(final OptimizeCfg config) {
    optimizeBaseUrl = config.getBaseUrl();
    keycloakBaseUrl = config.getKeycloakUrl();
    realm = config.getRealm();
    clientId = config.getClientId();
    username = config.getUsername();
    password = config.getPassword();
    clientSecret = config.getClientSecret();
    httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(120))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  // ---------------------------------------------------------------------------
  // Authentication (Keycloak Resource Owner Password Grant)
  // ---------------------------------------------------------------------------

  public void authenticateWithPasswordGrant() throws Exception {
    final String tokenUrl =
        String.format(
            "%s/auth/realms/%s/protocol/openid-connect/token", keycloakBaseUrl, realm);

    final String formData =
        "grant_type=password"
            + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
            + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
            + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
            + "&scope=openid";

    final HttpResponse<String> response =
        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new RuntimeException(
          String.format(
              "Password grant token request failed with status %d: %s",
              response.statusCode(), response.body()));
    }

    final JsonNode jsonNode = OBJECT_MAPPER.readTree(response.body());
    accessToken = jsonNode.get("access_token").asText();
    final int expiresIn = jsonNode.get("expires_in").asInt();
    tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
    LOG.debug("Successfully authenticated via password grant");
  }

  public void ensureValidToken() throws Exception {
    if (accessToken == null
        || System.currentTimeMillis() >= tokenExpiresAt - TOKEN_REFRESH_BUFFER_MS) {
      LOG.debug("Token expired or missing, re-authenticating");
      authenticateWithPasswordGrant();
    }
  }

  // ---------------------------------------------------------------------------
  // Public API — Dashboard & Report Evaluation
  // ---------------------------------------------------------------------------

  public HomepageResult evaluateHomepage() throws Exception {
    final DashboardEvaluationResult dashboardResult = fetchHomepageDashboard();
    if (!dashboardResult.isSuccess()) {
      throw new RuntimeException(
          String.format(
              "Dashboard evaluation failed with status %d", dashboardResult.getStatusCode()));
    }

    final List<ReportEvaluationResult> reportResults = fetchAllReportMetrics(dashboardResult);
    return new HomepageResult(dashboardResult, reportResults);
  }

  public DetailedPageResult evaluateDetailedPage(final String processDefinitionKey)
      throws Exception {
    LOG.debug("Using process definition key: {}", processDefinitionKey);

    // Step 1: Fetch detailed page dashboard
    final DashboardEvaluationResult dashboardResult =
        fetchDetailedPageDashboard(processDefinitionKey);
    if (!dashboardResult.isSuccess()) {
      throw new RuntimeException(
          String.format(
              "Detailed page dashboard failed with status %d", dashboardResult.getStatusCode()));
    }

    // Step 2: Evaluate each report
    final List<ReportEvaluationResult> reportResults = fetchAllReportMetrics(dashboardResult);

    // Step 3: Call detailed evaluate API for each successful report
    final List<ReportEvaluationResult> detailedResults = new ArrayList<>();
    for (final ReportEvaluationResult reportResult : reportResults) {
      if (!reportResult.isSuccess()) {
        LOG.warn(
            "Skipping detailed evaluate for report {} due to failed evaluation",
            reportResult.getReportId());
        continue;
      }
      try {
        detailedResults.add(
            fetchDetailedReportMetrics(reportResult.getReportId(), reportResult.getResponseBody()));
      } catch (final Exception e) {
        LOG.error("Failed detailed evaluate for report {}", reportResult.getReportId(), e);
        detailedResults.add(
            new ReportEvaluationResult(reportResult.getReportId(), 0, 0, e.getMessage()));
      }
    }

    return new DetailedPageResult(dashboardResult, reportResults, detailedResults);
  }

  public String fetchFirstProcessDefinitionKey() throws Exception {
    ensureValidToken();

    final TimedResponse response = executeGet(API_PROCESS_OVERVIEW);
    if (response.statusCode != 200) {
      throw new RuntimeException(
          String.format(
              "Process overview request failed with status %d: %s",
              response.statusCode, response.body));
    }

    final JsonNode rootNode = OBJECT_MAPPER.readTree(response.body);
    if (!rootNode.isArray() || rootNode.isEmpty()) {
      throw new RuntimeException("No processes found in process overview response");
    }

    final String key = rootNode.get(0).get("processDefinitionKey").asText();
    LOG.debug("Fetched process definition key: {}", key);
    return key;
  }

  public List<String> extractReportIdsFromDashboard(final String dashboardResponseBody)
      throws Exception {
    final List<String> reportIds = new ArrayList<>();
    final JsonNode rootNode = OBJECT_MAPPER.readTree(dashboardResponseBody);

    if (rootNode.has("tiles") && rootNode.get("tiles").isArray()) {
      for (final JsonNode tile : rootNode.get("tiles")) {
        if (tile.has("id")) {
          final String reportId = tile.get("id").asText();
          if (reportId != null && !reportId.trim().isEmpty()) {
            reportIds.add(reportId);
          }
        }
      }
    }

    LOG.debug("Extracted {} report IDs from dashboard", reportIds.size());
    return reportIds;
  }

  // ---------------------------------------------------------------------------
  // Private — HTTP helpers
  // ---------------------------------------------------------------------------

  private HttpRequest.Builder authenticatedRequest(final String apiPath) {
    return HttpRequest.newBuilder()
        .uri(URI.create(optimizeBaseUrl + apiPath))
        .header("Cookie", AUTH_COOKIE_NAME + "=" + accessToken)
        .header("Content-Type", "application/json")
        .header("X-Optimize-Client-Timezone", "UTC")
        .header("X-Optimize-Client-Locale", "en");
  }

  private TimedResponse executeGet(final String apiPath) throws Exception {
    final long startTime = System.currentTimeMillis();
    final HttpResponse<String> response =
        httpClient.send(
            authenticatedRequest(apiPath).GET().build(), HttpResponse.BodyHandlers.ofString());
    return new TimedResponse(
        response.statusCode(), response.body(), System.currentTimeMillis() - startTime);
  }

  private TimedResponse executePost(final String apiPath, final String body) throws Exception {
    final long startTime = System.currentTimeMillis();
    final HttpResponse<String> response =
        httpClient.send(
            authenticatedRequest(apiPath).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    return new TimedResponse(
        response.statusCode(), response.body(), System.currentTimeMillis() - startTime);
  }

  // ---------------------------------------------------------------------------
  // Private — Evaluation logic
  // ---------------------------------------------------------------------------

  private DashboardEvaluationResult fetchHomepageDashboard() throws Exception {
    ensureValidToken();
    LOG.debug("Fetching homepage dashboard");

    try {
      final TimedResponse response = executeGet(API_DASHBOARD_MANAGEMENT);
      if (response.statusCode != 200) {
        LOG.warn("Management dashboard returned non-200 status: {}", response.statusCode);
      }
      return new DashboardEvaluationResult(
          "management", response.statusCode, response.responseTimeMs, response.body);
    } catch (final java.net.ConnectException e) {
      LOG.error(
          "Connection failed to management dashboard. "
              + "Verify that Optimize is running and accessible. Cause: {}",
          e.getMessage());
      throw e;
    }
  }

  private DashboardEvaluationResult fetchDetailedPageDashboard(final String processDefinitionKey)
      throws Exception {
    ensureValidToken();
    LOG.debug("Fetching detailed page dashboard for key: {}", processDefinitionKey);

    final TimedResponse response = executeGet(API_DASHBOARD_INSTANT + processDefinitionKey);
    return new DashboardEvaluationResult(
        "instant_" + processDefinitionKey,
        response.statusCode,
        response.responseTimeMs,
        response.body);
  }

  private ReportEvaluationResult fetchReportMetrics(final String reportId) throws Exception {
    ensureValidToken();

    final TimedResponse response = executePost("/api/report/" + reportId + "/evaluate", "{}");
    return new ReportEvaluationResult(
        reportId, response.statusCode, response.responseTimeMs, response.body);
  }

  private ReportEvaluationResult fetchDetailedReportMetrics(
      final String reportId, final String responseBody) throws Exception {
    ensureValidToken();

    final String transformedBody = transformForDetailedEvaluate(responseBody);
    final TimedResponse response = executePost(API_REPORT_EVALUATE, transformedBody);
    return new ReportEvaluationResult(
        reportId, response.statusCode, response.responseTimeMs, response.body);
  }

  private List<ReportEvaluationResult> fetchAllReportMetrics(
      final DashboardEvaluationResult dashboardResult) throws Exception {
    final List<String> reportIds = extractReportIdsFromDashboard(dashboardResult.getResponseBody());
    final List<ReportEvaluationResult> results = new ArrayList<>();

    for (final String reportId : reportIds) {
      try {
        final ReportEvaluationResult result = fetchReportMetrics(reportId);
        results.add(result);
        LOG.debug(
            "Report {} [{}] evaluated in {}ms — status {}",
            reportId,
            result.getReportName(),
            result.getResponseTimeMs(),
            result.getStatusCode());
      } catch (final Exception e) {
        LOG.error("Failed to evaluate report {}", reportId, e);
        results.add(new ReportEvaluationResult(reportId, 0, 0, e.getMessage()));
      }
    }

    return results;
  }

  // ---------------------------------------------------------------------------
  // Private — JSON transformation for detailed evaluate
  // ---------------------------------------------------------------------------

  String transformForDetailedEvaluate(final String responseBody) throws Exception {
    final JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);
    if (!(rootNode instanceof ObjectNode)) {
      return responseBody;
    }

    final ObjectNode root = (ObjectNode) rootNode;
    root.remove("result");

    final JsonNode data = root.path("data");
    if (data.isMissingNode() || !(data instanceof ObjectNode)) {
      return OBJECT_MAPPER.writeValueAsString(root);
    }

    final ObjectNode dataObj = (ObjectNode) data;

    // view: remove entity, set properties=["rawData"]
    final JsonNode view = dataObj.path("view");
    if (!view.isMissingNode() && view instanceof ObjectNode) {
      final ObjectNode viewObj = (ObjectNode) view;
      viewObj.remove("entity");
      if (viewObj.has("properties")) {
        viewObj.set("properties", OBJECT_MAPPER.createArrayNode().add("rawData"));
      }
    }

    // groupBy: type=none, remove value
    final JsonNode groupBy = dataObj.path("groupBy");
    if (!groupBy.isMissingNode() && groupBy instanceof ObjectNode) {
      ((ObjectNode) groupBy).put("type", "none");
      ((ObjectNode) groupBy).remove("value");
    }

    // configuration.sorting.by=startDate
    final JsonNode sorting = dataObj.path("configuration").path("sorting");
    if (!sorting.isMissingNode() && sorting instanceof ObjectNode) {
      ((ObjectNode) sorting).put("by", "startDate");
    }

    return OBJECT_MAPPER.writeValueAsString(root);
  }

  // ---------------------------------------------------------------------------
  // Inner classes — TimedResponse
  // ---------------------------------------------------------------------------

  @Override
  public void close() {
    httpClient.close();
  }

  // ---------------------------------------------------------------------------
  // Inner classes — Result types
  // ---------------------------------------------------------------------------

  public static class DashboardEvaluationResult {
    private final String dashboardType;
    private final int statusCode;
    private final long responseTimeMs;
    private final String responseBody;

    public DashboardEvaluationResult(
        final String dashboardType,
        final int statusCode,
        final long responseTimeMs,
        final String responseBody) {
      this.dashboardType = dashboardType;
      this.statusCode = statusCode;
      this.responseTimeMs = responseTimeMs;
      this.responseBody = responseBody;
    }

    public String getDashboardType() {
      return dashboardType;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public long getResponseTimeMs() {
      return responseTimeMs;
    }

    public String getResponseBody() {
      return responseBody;
    }

    public boolean isSuccess() {
      return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public String toString() {
      return String.format(
          "DashboardEvaluationResult{type='%s', status=%d, timeMs=%d, success=%b}",
          dashboardType, statusCode, responseTimeMs, isSuccess());
    }
  }

  public static class ReportEvaluationResult {
    private final String reportId;
    private final String reportName;
    private final int statusCode;
    private final long responseTimeMs;
    private final String responseBody;

    public ReportEvaluationResult(
        final String reportId,
        final int statusCode,
        final long responseTimeMs,
        final String responseBody) {
      this.reportId = reportId;
      reportName = extractReportName(responseBody);
      this.statusCode = statusCode;
      this.responseTimeMs = responseTimeMs;
      this.responseBody = responseBody;
    }

    private static String extractReportName(final String responseBody) {
      try {
        final JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);
        if (rootNode.has("name")) {
          return rootNode.get("name").asText();
        }
      } catch (final Exception e) {
        LOG.debug("Failed to extract report name from response", e);
      }
      return null;
    }

    public String getReportId() {
      return reportId;
    }

    public String getReportName() {
      return reportName;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public long getResponseTimeMs() {
      return responseTimeMs;
    }

    public String getResponseBody() {
      return responseBody;
    }

    public boolean isSuccess() {
      return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public String toString() {
      return String.format(
          "ReportEvaluationResult{id='%s', name='%s', status=%d, timeMs=%d, success=%b}",
          reportId, reportName, statusCode, responseTimeMs, isSuccess());
    }
  }

  public static class HomepageResult {
    private final DashboardEvaluationResult dashboardResult;
    private final List<ReportEvaluationResult> reportResults;

    public HomepageResult(
        final DashboardEvaluationResult dashboardResult,
        final List<ReportEvaluationResult> reportResults) {
      this.dashboardResult = dashboardResult;
      this.reportResults = reportResults;
    }

    public DashboardEvaluationResult getDashboardResult() {
      return dashboardResult;
    }

    public List<ReportEvaluationResult> getReportResults() {
      return reportResults;
    }

    public long getTotalResponseTimeMs() {
      return dashboardResult.getResponseTimeMs()
          + reportResults.stream().mapToLong(ReportEvaluationResult::getResponseTimeMs).sum();
    }

    public long getMaxReportTimeMs() {
      return reportResults.stream()
          .mapToLong(ReportEvaluationResult::getResponseTimeMs)
          .max()
          .orElse(0);
    }

    public long getHomepageLoadTimeMs() {
      return dashboardResult.getResponseTimeMs() + getMaxReportTimeMs();
    }

    public boolean isAllSuccess() {
      return dashboardResult.isSuccess()
          && reportResults.stream().allMatch(ReportEvaluationResult::isSuccess);
    }

    @Override
    public String toString() {
      return String.format(
          "HomepageResult{dashboard=%s, reports=%d, totalTimeMs=%d, allSuccess=%b}",
          dashboardResult.getDashboardType(),
          reportResults.size(),
          getTotalResponseTimeMs(),
          isAllSuccess());
    }
  }

  public static class DetailedPageResult {
    private final DashboardEvaluationResult dashboardResult;
    private final List<ReportEvaluationResult> reportEvaluationResults;
    private final List<ReportEvaluationResult> detailedEvaluationResults;

    public DetailedPageResult(
        final DashboardEvaluationResult dashboardResult,
        final List<ReportEvaluationResult> reportEvaluationResults,
        final List<ReportEvaluationResult> detailedEvaluationResults) {
      this.dashboardResult = dashboardResult;
      this.reportEvaluationResults = reportEvaluationResults;
      this.detailedEvaluationResults = detailedEvaluationResults;
    }

    public DashboardEvaluationResult getDashboardResult() {
      return dashboardResult;
    }

    public List<ReportEvaluationResult> getReportEvaluationResults() {
      return reportEvaluationResults;
    }

    public List<ReportEvaluationResult> getDetailedEvaluationResults() {
      return detailedEvaluationResults;
    }

    public long getTotalResponseTimeMs() {
      return dashboardResult.getResponseTimeMs()
          + reportEvaluationResults.stream()
              .mapToLong(ReportEvaluationResult::getResponseTimeMs)
              .sum()
          + detailedEvaluationResults.stream()
              .mapToLong(ReportEvaluationResult::getResponseTimeMs)
              .sum();
    }

    public long getMaxReportEvaluationTimeMs() {
      return reportEvaluationResults.stream()
          .mapToLong(ReportEvaluationResult::getResponseTimeMs)
          .max()
          .orElse(0);
    }

    public long getMaxDetailedEvaluationTimeMs() {
      return detailedEvaluationResults.stream()
          .mapToLong(ReportEvaluationResult::getResponseTimeMs)
          .max()
          .orElse(0);
    }

    public boolean isAllSuccess() {
      return dashboardResult.isSuccess()
          && reportEvaluationResults.stream().allMatch(ReportEvaluationResult::isSuccess)
          && detailedEvaluationResults.stream().allMatch(ReportEvaluationResult::isSuccess);
    }

    @Override
    public String toString() {
      return String.format(
          "DetailedPageResult{dashboard=%s, reports=%d, detailed=%d, totalTimeMs=%d, allSuccess=%b}",
          dashboardResult.getDashboardType(),
          reportEvaluationResults.size(),
          detailedEvaluationResults.size(),
          getTotalResponseTimeMs(),
          isAllSuccess());
    }
  }

  private static class TimedResponse {
    final int statusCode;
    final String body;
    final long responseTimeMs;

    TimedResponse(final int statusCode, final String body, final long responseTimeMs) {
      this.statusCode = statusCode;
      this.body = body;
      this.responseTimeMs = responseTimeMs;
    }
  }
}
