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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizeReportLoadTester {
  private static final Logger LOG = LoggerFactory.getLogger(OptimizeReportLoadTester.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
  private String cookies = "";

  public OptimizeReportLoadTester(
      final String optimizeBaseUrl,
      final String keycloakBaseUrl,
      final String realm,
      final String clientId,
      final String username,
      final String password,
      final String clientSecret) {
    this.optimizeBaseUrl = optimizeBaseUrl;
    this.keycloakBaseUrl = keycloakBaseUrl;
    this.realm = realm;
    this.clientId = clientId;
    this.username = username;
    this.password = password;
    this.clientSecret = clientSecret;
    httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(120))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  /**
   * Authenticates with Keycloak by simulating the browser authorization code flow. This is more
   * complex but works when Direct Access Grant is disabled.
   *
   * @throws Exception if authentication fails
   */
  public void authenticateWithAuthorizationCodeFlow() throws Exception {
    cookies = "";

    // Step 1: Get authorization URL - this will redirect to login page
    final String authUrl =
        String.format(
            "%s/auth/realms/%s/protocol/openid-connect/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=openid+email",
            keycloakBaseUrl,
            realm,
            URLEncoder.encode(clientId, StandardCharsets.UTF_8),
            URLEncoder.encode(
                optimizeBaseUrl + "/api/authentication/callback", StandardCharsets.UTF_8));

    LOG.info("Getting Keycloak login page...");
    final HttpRequest authRequest = HttpRequest.newBuilder().uri(URI.create(authUrl)).GET().build();

    final HttpResponse<String> authResponse =
        httpClient.send(authRequest, HttpResponse.BodyHandlers.ofString());

    updateCookies(authResponse);
    LOG.debug("Auth page status: {}", authResponse.statusCode());

    // Extract the form action URL from the login page
    final String loginActionUrl = extractFormAction(authResponse.body());
    if (loginActionUrl == null) {
      throw new RuntimeException("Failed to parse Keycloak login page");
    }

    LOG.debug("Login action URL: {}", loginActionUrl);

    // Step 2: Submit login credentials with cookies
    final String loginFormData =
        String.format(
            "username=%s&password=%s",
            URLEncoder.encode(username, StandardCharsets.UTF_8),
            URLEncoder.encode(password, StandardCharsets.UTF_8));

    LOG.debug("Submitting login credentials...");
    final String fullLoginUrl =
        loginActionUrl.startsWith("http") ? loginActionUrl : keycloakBaseUrl + loginActionUrl;
    LOG.debug("Full login URL: {}", fullLoginUrl);

    final HttpRequest.Builder loginRequestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(fullLoginUrl))
            .header("Content-Type", "application/x-www-form-urlencoded");

    if (!cookies.isEmpty()) {
      loginRequestBuilder.header("Cookie", cookies);
    }

    final HttpRequest loginRequest =
        loginRequestBuilder.POST(HttpRequest.BodyPublishers.ofString(loginFormData)).build();

    final HttpResponse<String> loginResponse =
        httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());

    updateCookies(loginResponse);
    LOG.debug("Login response status: {}", loginResponse.statusCode());

    // Step 3: Extract authorization code from redirect
    String authorizationCode = null;
    HttpResponse<String> currentResponse = loginResponse;

    // Follow redirects manually to get the authorization code
    for (int i = 0;
        i < 10 && (currentResponse.statusCode() == 302 || currentResponse.statusCode() == 303);
        i++) {
      final var locationHeader = currentResponse.headers().firstValue("Location");
      if (locationHeader.isEmpty()) {
        break;
      }

      final String location = locationHeader.get();
      LOG.debug("Following redirect to: {}", location);

      // Check if this redirect contains the authorization code
      final Pattern codePattern = Pattern.compile("code=([^&]+)");
      final Matcher codeMatcher = codePattern.matcher(location);
      if (codeMatcher.find()) {
        authorizationCode = codeMatcher.group(1);
        LOG.info("Got authorization code");
        break;
      }

      // Follow the redirect
      final HttpRequest.Builder redirectRequestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(location.startsWith("http") ? location : keycloakBaseUrl + location))
              .GET();

      if (!cookies.isEmpty()) {
        redirectRequestBuilder.header("Cookie", cookies);
      }

      currentResponse =
          httpClient.send(redirectRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      updateCookies(currentResponse);
    }

    if (authorizationCode == null) {
      throw new RuntimeException(
          "Failed to get authorization code. Last status: " + currentResponse.statusCode());
    }

    // Step 4: Exchange authorization code for access token
    exchangeCodeForToken(authorizationCode);
    LOG.info("Successfully authenticated with Keycloak");
  }

  private void updateCookies(final HttpResponse<String> response) {
    final var setCookieHeaders = response.headers().allValues("Set-Cookie");
    if (!setCookieHeaders.isEmpty()) {
      final StringBuilder cookieBuilder = new StringBuilder(cookies);
      for (final String setCookie : setCookieHeaders) {
        final String cookieName = setCookie.split("=")[0];
        final String cookieValue = setCookie.split(";")[0];

        // Remove old cookie with same name if exists
        final String cookiePrefix = cookieName + "=";
        if (cookieBuilder.toString().contains(cookiePrefix)) {
          final Pattern pattern = Pattern.compile(cookiePrefix + "[^;]*;?\\s*");
          final String cleaned = pattern.matcher(cookieBuilder.toString()).replaceAll("");
          cookieBuilder.setLength(0);
          cookieBuilder.append(cleaned);
        }

        // Add new cookie
        if (cookieBuilder.length() > 0 && !cookieBuilder.toString().endsWith("; ")) {
          cookieBuilder.append("; ");
        }
        cookieBuilder.append(cookieValue);
      }
      cookies = cookieBuilder.toString();
    }
  }

  private String extractFormAction(final String html) {
    final Pattern pattern = Pattern.compile("action=\"([^\"]+)\"");
    final Matcher matcher = pattern.matcher(html);
    return matcher.find() ? matcher.group(1).replace("&amp;", "&") : null;
  }

  private void exchangeCodeForToken(final String authorizationCode) throws Exception {
    final String tokenUrl =
        String.format("%s/auth/realms/%s/protocol/openid-connect/token", keycloakBaseUrl, realm);

    // Build form data with client secret
    final StringBuilder formDataBuilder = new StringBuilder();
    formDataBuilder.append("grant_type=authorization_code");
    formDataBuilder
        .append("&client_id=")
        .append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
    formDataBuilder
        .append("&code=")
        .append(URLEncoder.encode(authorizationCode, StandardCharsets.UTF_8));
    formDataBuilder
        .append("&redirect_uri=")
        .append(
            URLEncoder.encode(
                optimizeBaseUrl + "/api/authentication/callback", StandardCharsets.UTF_8));
    formDataBuilder
        .append("&client_secret=")
        .append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formDataBuilder.toString()))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new RuntimeException(
          String.format(
              "Token exchange failed with status %d: %s", response.statusCode(), response.body()));
    }

    parseTokenResponse(response.body());
  }

  private void parseTokenResponse(final String responseBody) throws Exception {
    final JsonNode jsonNode = OBJECT_MAPPER.readTree(responseBody);
    accessToken = jsonNode.get("access_token").asText();
    final int expiresIn = jsonNode.get("expires_in").asInt();
    tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
  }

  /** Refreshes the access token if it's expired or about to expire. */
  public void ensureValidToken() throws Exception {
    if (accessToken == null || System.currentTimeMillis() >= tokenExpiresAt - 30000) {
      LOG.info("Token expired or missing, re-authenticating...");
      // Use authorization code flow (browser simulation)
      authenticateWithAuthorizationCodeFlow();
    }
  }

  /**
   * Evaluates a report by ID and returns the response time in milliseconds.
   *
   * @param reportId the report ID to evaluate
   * @return the response time in milliseconds
   * @throws Exception if the request fails
   */
  public ReportEvaluationResult evaluateReport(final String reportId) throws Exception {
    return evaluateReport(reportId, "{}");
  }

  /**
   * Evaluates a report by ID with additional filters and returns the response time in milliseconds.
   *
   * @param reportId the report ID to evaluate
   * @param filterJson JSON string containing additional filters (can be empty object "{}")
   * @return the evaluation result containing response time and status
   * @throws Exception if the request fails
   */
  public ReportEvaluationResult evaluateReport(final String reportId, final String filterJson)
      throws Exception {
    ensureValidToken();

    final String url = String.format("%s/api/report/%s/evaluate", optimizeBaseUrl, reportId);

    final long startTime = System.currentTimeMillis();

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Cookie", "X-Optimize-Authorization_0=" + accessToken)
            .header("Content-Type", "application/json")
            .header("X-Optimize-Client-Timezone", "UTC")
            .POST(HttpRequest.BodyPublishers.ofString(filterJson))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    final long responseTime = System.currentTimeMillis() - startTime;

    return new ReportEvaluationResult(
        reportId, response.statusCode(), responseTime, response.body());
  }

  /**
   * Calls the detailed evaluate API for a report with the given request body.
   *
   * @param reportId the report ID to evaluate
   * @param requestBody the request body obtained from the evaluate API call
   * @return the evaluation result containing response time and status
   * @throws Exception if the request fails
   */
  public ReportEvaluationResult evaluateReportDetailed(
      final String reportId, final String requestBody) throws Exception {
    ensureValidToken();

    // Transform the request body: remove result object, remove entity field from data.view, rename
    // properties to rawData
    final String transformedBody = transformRequestBodyForDetailedEvaluate(requestBody);

    final String url = String.format("%s/api/report/evaluate", optimizeBaseUrl);

    final long startTime = System.currentTimeMillis();

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Cookie", "X-Optimize-Authorization_0=" + accessToken)
            .header("Content-Type", "application/json")
            .header("X-Optimize-Client-Timezone", "UTC")
            .POST(HttpRequest.BodyPublishers.ofString(transformedBody))
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    final long responseTime = System.currentTimeMillis() - startTime;

    return new ReportEvaluationResult(
        reportId, response.statusCode(), responseTime, response.body());
  }

  /**
   * Transforms the request body for detailed evaluate API by: 1. Removing the result object 2.
   * Removing the entity field from data.view 3. Renaming properties to rawData in data.view
   *
   * @param responseBody the original response body from evaluate API
   * @return the transformed request body
   * @throws Exception if JSON parsing fails
   */
  private String transformRequestBodyForDetailedEvaluate(final String responseBody)
      throws Exception {
    final JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);

    if (!(rootNode instanceof ObjectNode)) {
      return responseBody;
    }

    final ObjectNode objectNode = (ObjectNode) rootNode;

    // Remove result object
    objectNode.remove("result");

    // Transform data section
    transformDataSection(objectNode);

    return OBJECT_MAPPER.writeValueAsString(objectNode);
  }

  /**
   * Transforms the data section of the report for detailed evaluate API.
   *
   * @param objectNode the root object node containing the data section
   */
  private void transformDataSection(final ObjectNode objectNode) {
    if (!objectNode.has("data")) {
      return;
    }

    final JsonNode dataNode = objectNode.get("data");
    if (!(dataNode instanceof ObjectNode)) {
      return;
    }

    final ObjectNode dataObjectNode = (ObjectNode) dataNode;

    transformViewSection(dataObjectNode);
    transformGroupBySection(dataObjectNode);
    transformSortingSection(dataObjectNode);
  }

  /**
   * Transforms the view section by removing entity and setting properties to ["rawData"].
   *
   * @param dataObjectNode the data object node containing the view section
   */
  private void transformViewSection(final ObjectNode dataObjectNode) {
    if (!dataObjectNode.has("view")) {
      return;
    }

    final JsonNode viewNode = dataObjectNode.get("view");
    if (!(viewNode instanceof ObjectNode)) {
      return;
    }

    final ObjectNode viewObjectNode = (ObjectNode) viewNode;

    // Remove entity field
    viewObjectNode.remove("entity");

    // Replace properties array with ["rawData"]
    if (viewObjectNode.has("properties")) {
      viewObjectNode.remove("properties");
      final ArrayNode rawDataArray = OBJECT_MAPPER.createArrayNode();
      rawDataArray.add("rawData");
      viewObjectNode.set("properties", rawDataArray);
    }
  }

  /**
   * Transforms the groupBy section by setting type to "none".
   *
   * @param dataObjectNode the data object node containing the groupBy section
   */
  private void transformGroupBySection(final ObjectNode dataObjectNode) {
    if (!dataObjectNode.has("groupBy")) {
      return;
    }

    final JsonNode groupByNode = dataObjectNode.get("groupBy");
    if (!(groupByNode instanceof ObjectNode)) {
      return;
    }

    final ObjectNode groupByObjectNode = (ObjectNode) groupByNode;
    groupByObjectNode.put("type", "none");
    groupByObjectNode.remove("value");
  }

  /**
   * Transforms the sorting section by setting the "by" value to "startDate".
   *
   * @param dataObjectNode the data object node containing the configuration section
   */
  private void transformSortingSection(final ObjectNode dataObjectNode) {
    if (!dataObjectNode.has("configuration")) {
      return;
    }

    final JsonNode configurationNode = dataObjectNode.get("configuration");
    if (!(configurationNode instanceof ObjectNode)) {
      return;
    }

    final ObjectNode configurationObjectNode = (ObjectNode) configurationNode;

    if (!configurationObjectNode.has("sorting")) {
      return;
    }

    final JsonNode sortingNode = configurationObjectNode.get("sorting");
    if (!(sortingNode instanceof ObjectNode)) {
      return;
    }

    final ObjectNode sortingObjectNode = (ObjectNode) sortingNode;
    sortingObjectNode.put("by", "startDate");
  }

  /**
   * Evaluates the management dashboard and returns the response time in milliseconds.
   *
   * @return the evaluation result containing response time and status
   * @throws Exception if the request fails
   */
  public DashboardEvaluationResult evaluateManagementDashboard() throws Exception {
    ensureValidToken();

    final String url = String.format("%s/api/dashboard/management", optimizeBaseUrl);
    LOG.info("Evaluating management dashboard at URL: {}", url);

    final long startTime = System.currentTimeMillis();

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Cookie", "X-Optimize-Authorization_0=" + accessToken)
            .header("Content-Type", "application/json")
            .header("X-Optimize-Client-Timezone", "UTC")
            .header("X-Optimize-Client-Locale", "en")
            .GET()
            .build();

    try {
      LOG.debug("Sending management dashboard request to {}", url);
      final HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      final long responseTime = System.currentTimeMillis() - startTime;
      LOG.info(
          "Management dashboard response received - Status: {}, Time: {}ms",
          response.statusCode(),
          responseTime);

      if (response.statusCode() != 200) {
        LOG.warn(
            "Management dashboard returned non-200 status: {}, Body: {}",
            response.statusCode(),
            response.body());
      }

      return new DashboardEvaluationResult(
          "management", response.statusCode(), responseTime, response.body());
    } catch (final java.net.ConnectException e) {
      final long responseTime = System.currentTimeMillis() - startTime;
      LOG.error(
          "Connection failed to management dashboard at {} after {}ms. "
              + "Verify that Optimize is running and accessible at the configured URL. "
              + "Cause: {}",
          url,
          responseTime,
          e.getMessage(),
          e);
      throw e;
    }
  }

  /**
   * Fetches the instant benchmark dashboard for a specific process definition key.
   *
   * @param processDefinitionKey the process definition key
   * @return the evaluation result containing response time and status
   * @throws Exception if the request fails
   */
  public DashboardEvaluationResult evaluateInstantBenchmarkDashboard(
      final String processDefinitionKey) throws Exception {
    ensureValidToken();

    final String url =
        String.format("%s/api/dashboard/instant/%s", optimizeBaseUrl, processDefinitionKey);

    final long startTime = System.currentTimeMillis();

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Cookie", "X-Optimize-Authorization_0=" + accessToken)
            .header("Content-Type", "application/json")
            .header("X-Optimize-Client-Timezone", "UTC")
            .header("X-Optimize-Client-Locale", "en")
            .GET()
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    final long responseTime = System.currentTimeMillis() - startTime;

    return new DashboardEvaluationResult(
        "instant_" + processDefinitionKey, response.statusCode(), responseTime, response.body());
  }

  /**
   * Fetches the instant benchmark dashboard and returns the response.
   *
   * @return the evaluation result containing response time and status
   * @throws Exception if the request fails
   * @deprecated Use evaluateInstantBenchmarkDashboard(String processDefinitionKey) instead
   */
  @Deprecated
  public DashboardEvaluationResult evaluateInstantBenchmarkDashboard() throws Exception {
    ensureValidToken();

    final String url = String.format("%s/api/dashboard/instant/benchmark", optimizeBaseUrl);

    final long startTime = System.currentTimeMillis();

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Cookie", "X-Optimize-Authorization_0=" + accessToken)
            .header("Content-Type", "application/json")
            .header("X-Optimize-Client-Timezone", "UTC")
            .header("X-Optimize-Client-Locale", "en")
            .GET()
            .build();

    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    final long responseTime = System.currentTimeMillis() - startTime;

    return new DashboardEvaluationResult(
        "instant_benchmark", response.statusCode(), responseTime, response.body());
  }

  /**
   * Parses the dashboard response body to extract report IDs from tiles.
   *
   * @param dashboardResponseBody the JSON response body from the dashboard API
   * @return a list of report IDs found in the dashboard tiles
   * @throws Exception if JSON parsing fails
   */
  public List<String> extractReportIdsFromDashboard(final String dashboardResponseBody)
      throws Exception {
    final List<String> reportIds = new ArrayList<>();
    final JsonNode rootNode = OBJECT_MAPPER.readTree(dashboardResponseBody);

    // Check if tiles array exists
    if (rootNode.has("tiles") && rootNode.get("tiles").isArray()) {
      for (final JsonNode tile : rootNode.get("tiles")) {
        // Extract reportId from tile.id
        if (tile.has("id")) {
          final String reportId = tile.get("id").asText();
          if (reportId != null && !reportId.trim().isEmpty()) {
            reportIds.add(reportId);
            LOG.debug("Found report ID in dashboard: {}", reportId);
          }
        }
      }
    }

    LOG.debug("Extracted {} report IDs from dashboard", reportIds.size());
    return reportIds;
  }

  /**
   * Evaluates the management dashboard and then evaluates all reports contained within it.
   *
   * @return a result containing dashboard metrics and all report evaluation results
   * @throws Exception if any request fails
   */
  public DashboardWithReportsResult evaluateDashboardWithReports() throws Exception {
    // Step 1: Evaluate the dashboard
    final DashboardEvaluationResult dashboardResult = evaluateManagementDashboard();

    if (!dashboardResult.isSuccess()) {
      throw new RuntimeException(
          String.format(
              "Dashboard evaluation failed with status %d", dashboardResult.getStatusCode()));
    }

    // Step 2: Extract report IDs from dashboard response
    final List<String> reportIds = extractReportIdsFromDashboard(dashboardResult.getResponseBody());

    // Step 3: Evaluate each report
    final List<ReportEvaluationResult> reportResults = new ArrayList<>();
    for (final String reportId : reportIds) {
      LOG.info("Evaluating report: {}", reportId);
      try {
        final ReportEvaluationResult reportResult = evaluateReport(reportId);
        reportResults.add(reportResult);
        LOG.info(
            "Report {} [{}] evaluated in {}ms - Status: {}",
            reportId,
            reportResult.getReportName(),
            reportResult.getResponseTimeMs(),
            reportResult.getStatusCode());
      } catch (final Exception e) {
        LOG.error("Failed to evaluate report {}", reportId, e);
        // Create a failed result
        reportResults.add(new ReportEvaluationResult(reportId, 0, 0, e.getMessage()));
      }
    }

    return new DashboardWithReportsResult(dashboardResult, reportResults);
  }

  /**
   * Evaluates the instant benchmark dashboard, then evaluates all reports and finally calls
   * detailed evaluate API for each report.
   *
   * @param processDefinitionKey the process definition key to use for the instant benchmark
   * @return a result containing dashboard metrics, report evaluations, and detailed evaluations
   * @throws Exception if any request fails
   */
  public InstantBenchmarkResult evaluateInstantBenchmark(final String processDefinitionKey)
      throws Exception {
    // Step 0: Use the provided process definition key
    LOG.info("Using process definition key: {}", processDefinitionKey);

    // Step 1: Fetch instant benchmark dashboard for the process definition
    final DashboardEvaluationResult dashboardResult =
        evaluateInstantBenchmarkDashboard(processDefinitionKey);

    if (!dashboardResult.isSuccess()) {
      throw new RuntimeException(
          String.format(
              "Instant benchmark dashboard evaluation failed with status %d",
              dashboardResult.getStatusCode()));
    }

    // Step 2: Extract report IDs from dashboard response
    final List<String> reportIds = extractReportIdsFromDashboard(dashboardResult.getResponseBody());

    // Step 3: Evaluate each report using the standard evaluate API
    final List<ReportEvaluationResult> reportEvaluationResults = new ArrayList<>();
    for (final String reportId : reportIds) {
      LOG.info("Evaluating report: {}", reportId);
      try {
        final ReportEvaluationResult reportResult = evaluateReport(reportId);
        reportEvaluationResults.add(reportResult);
        LOG.info(
            "Report {} [{}] evaluated in {}ms - Status: {}",
            reportId,
            reportResult.getReportName(),
            reportResult.getResponseTimeMs(),
            reportResult.getStatusCode());
      } catch (final Exception e) {
        LOG.error("Failed to evaluate report {}", reportId, e);
        reportEvaluationResults.add(new ReportEvaluationResult(reportId, 0, 0, e.getMessage()));
      }
    }

    // Step 4: Call detailed evaluate API for each report using the response body from Step 3
    final List<ReportEvaluationResult> detailedEvaluationResults = new ArrayList<>();
    for (final ReportEvaluationResult reportEvalResult : reportEvaluationResults) {
      if (reportEvalResult.isSuccess()) {
        LOG.info(
            "Calling detailed evaluate API for report: {} [{}]",
            reportEvalResult.getReportId(),
            reportEvalResult.getReportName());
        try {
          final ReportEvaluationResult detailedResult =
              evaluateReportDetailed(
                  reportEvalResult.getReportId(), reportEvalResult.getResponseBody());
          detailedEvaluationResults.add(detailedResult);
          LOG.info(
              "Detailed evaluation for report {} [{}] completed in {}ms - Status: {}",
              reportEvalResult.getReportId(),
              reportEvalResult.getReportName(),
              detailedResult.getResponseTimeMs(),
              detailedResult.getStatusCode());
        } catch (final Exception e) {
          LOG.error(
              "Failed to call detailed evaluate for report {} [{}]",
              reportEvalResult.getReportId(),
              reportEvalResult.getReportName(),
              e);
          detailedEvaluationResults.add(
              new ReportEvaluationResult(reportEvalResult.getReportId(), 0, 0, e.getMessage()));
        }
      } else {
        LOG.warn(
            "Skipping detailed evaluate for report {} [{}] due to failed evaluation",
            reportEvalResult.getReportId(),
            reportEvalResult.getReportName());
      }
    }

    return new InstantBenchmarkResult(
        dashboardResult, reportEvaluationResults, detailedEvaluationResults);
  }

  /** Result object containing dashboard evaluation metrics. */
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
          "DashboardEvaluationResult{dashboardType='%s', statusCode=%d, responseTimeMs=%d, success=%b}",
          dashboardType, statusCode, responseTimeMs, isSuccess());
    }
  }

  /** Result object containing report evaluation metrics. */
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

    private String extractReportName(final String responseBody) {
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
          "ReportEvaluationResult{reportId='%s', reportName='%s', statusCode=%d, responseTimeMs=%d, success=%b}",
          reportId, reportName, statusCode, responseTimeMs, isSuccess());
    }
  }

  /** Result object containing dashboard and all its reports evaluation metrics. */
  public static class DashboardWithReportsResult {
    private final DashboardEvaluationResult dashboardResult;
    private final List<ReportEvaluationResult> reportResults;

    public DashboardWithReportsResult(
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
      long total = dashboardResult.getResponseTimeMs();
      for (final ReportEvaluationResult reportResult : reportResults) {
        total += reportResult.getResponseTimeMs();
      }
      return total;
    }

    /**
     * Returns the maximum report response time (slowest report). This represents the bottleneck
     * when reports are loaded in parallel.
     *
     * @return maximum report response time in milliseconds, or 0 if no reports
     */
    public long getMaxReportTimeMs() {
      long max = 0;
      for (final ReportEvaluationResult reportResult : reportResults) {
        if (reportResult.getResponseTimeMs() > max) {
          max = reportResult.getResponseTimeMs();
        }
      }
      return max;
    }

    /**
     * Returns the homepage load time (user-perceived). This is dashboard load time + maximum report
     * time (since reports load in parallel in UI).
     *
     * @return homepage load time in milliseconds
     */
    public long getHomepageLoadTimeMs() {
      return dashboardResult.getResponseTimeMs() + getMaxReportTimeMs();
    }

    public boolean isAllSuccess() {
      if (!dashboardResult.isSuccess()) {
        return false;
      }
      for (final ReportEvaluationResult reportResult : reportResults) {
        if (!reportResult.isSuccess()) {
          return false;
        }
      }
      return true;
    }

    @Override
    public String toString() {
      return String.format(
          "DashboardWithReportsResult{dashboard=%s, reports=%d, totalTimeMs=%d, allSuccess=%b}",
          dashboardResult.getDashboardType(),
          reportResults.size(),
          getTotalResponseTimeMs(),
          isAllSuccess());
    }
  }

  /**
   * Result object containing instant benchmark dashboard, report evaluations, and detailed
   * evaluations.
   */
  public static class InstantBenchmarkResult {
    private final DashboardEvaluationResult dashboardResult;
    private final List<ReportEvaluationResult> reportEvaluationResults;
    private final List<ReportEvaluationResult> detailedEvaluationResults;

    public InstantBenchmarkResult(
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
      long total = dashboardResult.getResponseTimeMs();
      for (final ReportEvaluationResult result : reportEvaluationResults) {
        total += result.getResponseTimeMs();
      }
      for (final ReportEvaluationResult result : detailedEvaluationResults) {
        total += result.getResponseTimeMs();
      }
      return total;
    }

    public long getMaxReportEvaluationTimeMs() {
      long max = 0;
      for (final ReportEvaluationResult result : reportEvaluationResults) {
        if (result.getResponseTimeMs() > max) {
          max = result.getResponseTimeMs();
        }
      }
      return max;
    }

    public long getMaxDetailedEvaluationTimeMs() {
      long max = 0;
      for (final ReportEvaluationResult result : detailedEvaluationResults) {
        if (result.getResponseTimeMs() > max) {
          max = result.getResponseTimeMs();
        }
      }
      return max;
    }

    public boolean isAllSuccess() {
      if (!dashboardResult.isSuccess()) {
        return false;
      }
      for (final ReportEvaluationResult result : reportEvaluationResults) {
        if (!result.isSuccess()) {
          return false;
        }
      }
      for (final ReportEvaluationResult result : detailedEvaluationResults) {
        if (!result.isSuccess()) {
          return false;
        }
      }
      return true;
    }

    @Override
    public String toString() {
      return String.format(
          "InstantBenchmarkResult{dashboard=%s, reportEvaluations=%d, detailedEvaluations=%d, totalTimeMs=%d, allSuccess=%b}",
          dashboardResult.getDashboardType(),
          reportEvaluationResults.size(),
          detailedEvaluationResults.size(),
          getTotalResponseTimeMs(),
          isAllSuccess());
    }
  }
}
