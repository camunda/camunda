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
import io.camunda.qa.util.cluster.TestWebappClient.TestLoggedInWebappClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.CsrfConfiguration;
import java.io.IOException;
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
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
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

    // given
    final var webappClient = CAMUNDA_APPLICATION.newWebappClient();

    try (final var loggedInClient =
        webappClient.logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {
      // Test all protected endpoints with CSRF token
      for (final String endpoint : PROTECTED_ENDPOINTS) {
        final var response = sendRequestWithCsrfToken(loggedInClient, endpoint);
        assertThat(response.statusCode())
            .as("Endpoint %s should return OK when CSRF token is present", endpoint)
            .isEqualTo(HttpStatus.OK.value());
      }
    }
  }

  @Test
  public void visitProtectedEndpointSuccessfulWhenBasicAuthWithoutCsrfToken()
      throws URISyntaxException, IOException, InterruptedException {

    // given
    final var webappClient = CAMUNDA_APPLICATION.newWebappClient();

    try (final var loggedInClient =
        webappClient.logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {
      // Test all protected endpoints with CSRF token
      for (final String endpoint : PROTECTED_ENDPOINTS) {
        final var response = sendRequestWithoutCsrfTokenWithBasicAuth(loggedInClient, endpoint);
        assertThat(response.statusCode())
            .as("Endpoint %s should return OK when CSRF token is present", endpoint)
            .isEqualTo(HttpStatus.OK.value());
      }
    }
  }

  @Test
  public void visitProtectedEndpointNotAuthorizedWhenCsrfTokenAbsent()
      throws URISyntaxException, IOException, InterruptedException {

    // given
    final var webappClient = CAMUNDA_APPLICATION.newWebappClient();

    try (final var loggedInClient =
        webappClient.logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {
      // Test all protected endpoints without CSRF token
      for (final String endpoint : PROTECTED_ENDPOINTS) {
        final var response = sendRequestWithoutCsrfToken(loggedInClient, endpoint);
        assertThat(response.statusCode())
            .as("Endpoint %s should return UNAUTHORIZED when CSRF token is absent", endpoint)
            .isEqualTo(HttpStatus.UNAUTHORIZED.value());
      }
    }
  }

  @Test
  public void shouldSetCsrfCookiesOnLogin() {

    // given
    final var webappClient = CAMUNDA_APPLICATION.newWebappClient();

    // when
    try (final var loggedInClient =
        webappClient.logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {
      // then
      assertThat(loggedInClient.getCookies())
          .extracting(c -> c.getName())
          .containsExactlyInAnyOrder(
              WebSecurityConfig.SESSION_COOKIE, WebSecurityConfig.X_CSRF_TOKEN);
    }
  }

  private HttpResponse<String> sendRequestWithCsrfToken(
      final TestLoggedInWebappClient webappClient, final String endpoint)
      throws URISyntaxException, IOException, InterruptedException {
    return sendRequest(webappClient, endpoint, true, false);
  }

  private HttpResponse<String> sendRequestWithoutCsrfToken(
      final TestLoggedInWebappClient webappClient, final String endpoint)
      throws URISyntaxException, IOException, InterruptedException {
    return sendRequest(webappClient, endpoint, false, false);
  }

  private HttpResponse<String> sendRequestWithoutCsrfTokenWithBasicAuth(
      final TestLoggedInWebappClient webappClient, final String endpoint)
      throws URISyntaxException, IOException, InterruptedException {
    return sendRequest(webappClient, endpoint, false, true);
  }

  private HttpResponse<String> sendRequest(
      final TestLoggedInWebappClient webappClient,
      final String endpoint,
      final boolean includeCsrfToken,
      final boolean useBasicAuth)
      throws URISyntaxException, IOException, InterruptedException {

    try (final var httpClient = HttpClient.newBuilder().build()) {

      final var requestBuilder =
          HttpRequest.newBuilder()
              .uri(webappClient.getRootEndpoint().resolve(endpoint))
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
        requestBuilder.header(HttpHeaders.COOKIE, webappClient.getSessionCookie().toString());
      }

      if (includeCsrfToken) {
        requestBuilder.header(WebSecurityConfig.X_CSRF_TOKEN, webappClient.getCsrfToken());
        requestBuilder.header(HttpHeaders.COOKIE, webappClient.getCsrfCookie().toString());
      }

      return httpClient.send(requestBuilder.build(), BodyHandlers.ofString());
    }
  }
}
