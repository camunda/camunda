/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.csrf;

import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_PASSWORD;
import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.CsrfConfiguration;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class CsrfTokenIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          .withAuthenticatedAccess()
          .withSecurityConfig(
              sc -> {
                final var csrfConfig = new CsrfConfiguration();
                csrfConfig.setEnabled(true);
                sc.setCsrf(csrfConfig);
              })
          .withAdditionalProfile("consolidated-auth");

  // Test endpoints
  private static final List<String> PROTECTED_ENDPOINTS =
      List.of("api/processes/grouped", "v2/process-instances/search", "v1/tasks/search");

  @Test
  public void visitProtectedEndpointSuccessfulWhenCsrfTokenPresent()
      throws URISyntaxException, IOException, InterruptedException {

    final var testContext = setupTestContext();

    // Test all protected endpoints with CSRF token
    for (final String endpoint : PROTECTED_ENDPOINTS) {
      final var response = sendRequestWithCsrfToken(testContext, endpoint);
      assertThat(response.statusCode())
          .as("Endpoint %s should return OK when CSRF token is present", endpoint)
          .isEqualTo(HttpStatus.OK.value());
    }
  }

  @Test
  public void visitProtectedEndpointSuccessfulWhenBasicAuthWithoutCsrfToken()
      throws URISyntaxException, IOException, InterruptedException {

    final var testContext = setupTestContext();

    // Test all protected endpoints with CSRF token
    for (final String endpoint : PROTECTED_ENDPOINTS) {
      final var response = sendRequestWithoutCsrfTokenWithBasicAuth(testContext, endpoint);
      assertThat(response.statusCode())
          .as("Endpoint %s should return OK when CSRF token is present", endpoint)
          .isEqualTo(HttpStatus.OK.value());
    }
  }

  @Test
  public void visitProtectedEndpointNotAuthorizedWhenCsrfTokenAbsent()
      throws URISyntaxException, IOException, InterruptedException {

    final var testContext = setupTestContext();

    // Test all protected endpoints without CSRF token
    for (final String endpoint : PROTECTED_ENDPOINTS) {
      final var response = sendRequestWithoutCsrfToken(testContext, endpoint);
      assertThat(response.statusCode())
          .as("Endpoint %s should return UNAUTHORIZED when CSRF token is absent", endpoint)
          .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
  }

  private TestContext setupTestContext()
      throws URISyntaxException, IOException, InterruptedException {

    final var operateClient = CAMUNDA_APPLICATION.newOperateClient();
    final var uri = operateClient.getEndpoint();
    final var cookieHandler = new CookieManager();
    final var httpClient = HttpClient.newBuilder().cookieHandler(cookieHandler).build();

    final var csrfToken = loginWithCamundaService(httpClient, uri);
    final var cookies = extractCookies(cookieHandler);

    return new TestContext(uri, csrfToken, cookies.sessionCookie, cookies.csrfCookie);
  }

  private CookieContext extractCookies(final CookieManager cookieHandler) {
    final var cookies = cookieHandler.getCookieStore().getCookies();

    assertThat(cookies.stream().map(HttpCookie::getName))
        .hasSize(2)
        .contains(WebSecurityConfig.X_CSRF_TOKEN, WebSecurityConfig.SESSION_COOKIE);

    final var sessionCookie = findCookie(cookies, WebSecurityConfig.SESSION_COOKIE);
    final var csrfCookie = findCookie(cookies, WebSecurityConfig.X_CSRF_TOKEN);

    return new CookieContext(sessionCookie, csrfCookie);
  }

  private HttpCookie findCookie(final List<HttpCookie> cookies, final String cookieName) {
    return cookies.stream()
        .filter(c -> cookieName.equals(c.getName()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Cookie not found: " + cookieName));
  }

  private HttpResponse<String> sendRequestWithCsrfToken(
      final TestContext context, final String endpoint)
      throws URISyntaxException, IOException, InterruptedException {
    return sendRequest(context, endpoint, true, false);
  }

  private HttpResponse<String> sendRequestWithoutCsrfToken(
      final TestContext context, final String endpoint)
      throws URISyntaxException, IOException, InterruptedException {
    return sendRequest(context, endpoint, false, false);
  }

  private HttpResponse<String> sendRequestWithoutCsrfTokenWithBasicAuth(
      final TestContext context, final String endpoint)
      throws URISyntaxException, IOException, InterruptedException {
    return sendRequest(context, endpoint, false, true);
  }

  private HttpResponse<String> sendRequest(
      final TestContext context,
      final String endpoint,
      final boolean includeCsrfToken,
      final boolean useBasicAuth)
      throws URISyntaxException, IOException, InterruptedException {

    final var httpClient = HttpClient.newBuilder().build();
    final var requestBuilder =
        HttpRequest.newBuilder()
            .uri(new URI(context.uri + endpoint))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .POST(BodyPublishers.ofString("{}"));

    if (useBasicAuth) {
      requestBuilder.header(
          HttpHeaders.AUTHORIZATION,
          "Basic "
              + Base64.getEncoder()
                  .encodeToString(
                      (DEFAULT_USER_USERNAME + ":" + DEFAULT_USER_PASSWORD).getBytes()));
    } else {
      requestBuilder.header(HttpHeaders.COOKIE, context.sessionCookie.toString());
    }

    if (includeCsrfToken) {
      requestBuilder.header(WebSecurityConfig.X_CSRF_TOKEN, context.csrfToken);
      requestBuilder.header(HttpHeaders.COOKIE, context.csrfCookie.toString());
    }

    return httpClient.send(requestBuilder.build(), BodyHandlers.ofString());
  }

  private String loginWithCamundaService(final HttpClient httpClient, final URI uri)
      throws IOException, InterruptedException, URISyntaxException {

    final var authRequest =
        HttpRequest.newBuilder()
            .uri(new URI(uri.toString() + "login"))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED.toString())
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "username=" + DEFAULT_USER_USERNAME + "&password=" + DEFAULT_USER_PASSWORD))
            .build();

    // Test public API
    final var response = httpClient.send(authRequest, BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());

    final var rootServletRequest =
        httpClient.send(
            HttpRequest.newBuilder().uri(new URI(uri.toString())).GET().build(),
            BodyHandlers.ofString());

    return rootServletRequest.headers().firstValue(WebSecurityConfig.X_CSRF_TOKEN).orElse(null);
  }

  // Helper classes to organize test data
  private static class TestContext {
    final URI uri;
    final String csrfToken;
    final HttpCookie sessionCookie;
    final HttpCookie csrfCookie;

    TestContext(
        final URI uri,
        final String csrfToken,
        final HttpCookie sessionCookie,
        final HttpCookie csrfCookie) {
      this.uri = uri;
      this.csrfToken = csrfToken;
      this.sessionCookie = sessionCookie;
      this.csrfCookie = csrfCookie;
    }
  }

  private static class CookieContext {
    final HttpCookie sessionCookie;
    final HttpCookie csrfCookie;

    CookieContext(final HttpCookie sessionCookie, final HttpCookie csrfCookie) {
      this.sessionCookie = sessionCookie;
      this.csrfCookie = csrfCookie;
    }
  }
}
