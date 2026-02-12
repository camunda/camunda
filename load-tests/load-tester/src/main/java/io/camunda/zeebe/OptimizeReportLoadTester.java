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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
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
  private String codeVerifier;
  private String codeChallenge;

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
            .connectTimeout(Duration.ofSeconds(30))
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

    // Only generate PKCE if no client secret (public client)
    final boolean usePKCE = (clientSecret == null || clientSecret.isEmpty());
    if (usePKCE) {
      generatePKCE();
    }

    // Step 1: Get authorization URL - this will redirect to login page
    final String authUrlFormat = usePKCE
        ? "%s/auth/realms/%s/protocol/openid-connect/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=openid+email&code_challenge=%s&code_challenge_method=S256"
        : "%s/auth/realms/%s/protocol/openid-connect/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=openid+email";

    final String authUrl = usePKCE
        ? String.format(
            authUrlFormat,
            keycloakBaseUrl,
            realm,
            URLEncoder.encode(clientId, StandardCharsets.UTF_8),
            URLEncoder.encode(
                optimizeBaseUrl + "/api/authentication/callback", StandardCharsets.UTF_8),
            URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8))
        : String.format(
            authUrlFormat,
            keycloakBaseUrl,
            realm,
            URLEncoder.encode(clientId, StandardCharsets.UTF_8),
            URLEncoder.encode(
                optimizeBaseUrl + "/api/authentication/callback", StandardCharsets.UTF_8));

    LOG.info("Getting Keycloak login page...");
    final HttpRequest authRequest =
        HttpRequest.newBuilder().uri(URI.create(authUrl)).GET().build();

    final HttpResponse<String> authResponse =
        httpClient.send(authRequest, HttpResponse.BodyHandlers.ofString());

    updateCookies(authResponse);
    LOG.debug("Auth page status: {}", authResponse.statusCode());

    // Extract the form action URL from the login page
    final String loginActionUrl = extractFormAction(authResponse.body());
    if (loginActionUrl == null) {
      throw new RuntimeException("Failed to parse Keycloak login page");
    }

    LOG.info("Login action URL: {}", loginActionUrl);

    // Step 2: Submit login credentials with cookies
    final String loginFormData =
        String.format(
            "username=%s&password=%s",
            URLEncoder.encode(username, StandardCharsets.UTF_8),
            URLEncoder.encode(password, StandardCharsets.UTF_8));

    LOG.info("Submitting login credentials...");
    final String fullLoginUrl = loginActionUrl.startsWith("http") ? loginActionUrl : keycloakBaseUrl + loginActionUrl;
    LOG.info("Full login URL: {}", fullLoginUrl);

    final HttpRequest.Builder loginRequestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(fullLoginUrl))
            .header("Content-Type", "application/x-www-form-urlencoded");

    if (!cookies.isEmpty()) {
      loginRequestBuilder.header("Cookie", cookies);
    }

    final HttpRequest loginRequest = loginRequestBuilder
        .POST(HttpRequest.BodyPublishers.ofString(loginFormData))
        .build();

    final HttpResponse<String> loginResponse =
        httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());

    updateCookies(loginResponse);
    LOG.debug("Login response status: {}", loginResponse.statusCode());

    // Step 3: Extract authorization code from redirect
    String authorizationCode = null;
    HttpResponse<String> currentResponse = loginResponse;

    // Follow redirects manually to get the authorization code
    for (int i = 0; i < 10 && (currentResponse.statusCode() == 302 || currentResponse.statusCode() == 303); i++) {
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
      final HttpRequest.Builder redirectRequestBuilder = HttpRequest.newBuilder()
          .uri(URI.create(location.startsWith("http") ? location : keycloakBaseUrl + location))
          .GET();

      if (!cookies.isEmpty()) {
        redirectRequestBuilder.header("Cookie", cookies);
      }

      currentResponse = httpClient.send(redirectRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
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

  private void generatePKCE() throws Exception {
    // Generate code verifier (43-128 characters)
    final SecureRandom secureRandom = new SecureRandom();
    final byte[] codeVerifierBytes = new byte[32];
    secureRandom.nextBytes(codeVerifierBytes);
    codeVerifier =
        Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes);

    // Generate code challenge (SHA256 hash of verifier)
    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    final byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
    codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
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

    // Build form data - include client_secret if provided, otherwise use PKCE
    final StringBuilder formDataBuilder = new StringBuilder();
    formDataBuilder.append("grant_type=authorization_code");
    formDataBuilder.append("&client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
    formDataBuilder.append("&code=").append(URLEncoder.encode(authorizationCode, StandardCharsets.UTF_8));
    formDataBuilder.append("&redirect_uri=").append(
        URLEncoder.encode(optimizeBaseUrl + "/api/authentication/callback", StandardCharsets.UTF_8));

    if (clientSecret != null && !clientSecret.isEmpty()) {
      // Confidential client - use client secret
      formDataBuilder.append("&client_secret=").append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));
      LOG.info("Using client secret for token exchange");
    } else {
      // Public client - use PKCE
      formDataBuilder.append("&code_verifier=").append(URLEncoder.encode(codeVerifier, StandardCharsets.UTF_8));
      LOG.info("Using PKCE for token exchange");
    }

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
   * Evaluates a report by ID with additional filters and returns the response time in
   * milliseconds.
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
   * Evaluates the management dashboard and returns the response time in milliseconds.
   *
   * @return the evaluation result containing response time and status
   * @throws Exception if the request fails
   */
  public DashboardEvaluationResult evaluateManagementDashboard() throws Exception {
    ensureValidToken();

    final String url = String.format("%s/api/dashboard/management", optimizeBaseUrl);

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
        "management", response.statusCode(), responseTime, response.body());
  }

  /** Example usage. */
  public static void main(final String[] args) {
    try {
      final OptimizeReportLoadTester tester =
          new OptimizeReportLoadTester(
              "http://localhost:8083",
              "http://localhost:18080",
              "camunda-platform",
              "optimize",
              "demo",
              "demo",
              "demo-optimize-secret");

      // Authenticate using authorization code flow (simulates browser login)
      tester.authenticateWithAuthorizationCodeFlow();

      // Evaluate management dashboard
      final DashboardEvaluationResult dashboardResult = tester.evaluateManagementDashboard();
      System.out.println("Dashboard result: " + dashboardResult);
      System.out.println("Dashboard response time: " + dashboardResult.getResponseTimeMs() + "ms");

      // Evaluate a report
      final String reportId = "184e2de4-492d-47e5-a24a-68bce83381b8";
      final ReportEvaluationResult reportResult = tester.evaluateReport(reportId);
      System.out.println("Report result: " + reportResult);
      System.out.println("Report response time: " + reportResult.getResponseTimeMs() + "ms");

    } catch (final Exception e) {
      LOG.error("Load test failed", e);
    }
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
    private final int statusCode;
    private final long responseTimeMs;
    private final String responseBody;

    public ReportEvaluationResult(
        final String reportId,
        final int statusCode,
        final long responseTimeMs,
        final String responseBody) {
      this.reportId = reportId;
      this.statusCode = statusCode;
      this.responseTimeMs = responseTimeMs;
      this.responseBody = responseBody;
    }

    public String getReportId() {
      return reportId;
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
          "ReportEvaluationResult{reportId='%s', statusCode=%d, responseTimeMs=%d, success=%b}",
          reportId, statusCode, responseTimeMs, isSuccess());
    }
  }
}
