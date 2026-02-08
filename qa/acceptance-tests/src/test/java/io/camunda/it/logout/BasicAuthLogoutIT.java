/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.logout;

import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_PASSWORD;
import static io.camunda.security.configuration.InitializationConfiguration.DEFAULT_USER_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestWebappClient.TestLoggedInWebappClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.CsrfConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BasicAuthLogoutIT {

  private static final String TENANTS_SEARCH_ENDPOINT = "/v2/tenants/search";
  private static final String TENANTS_SEARCH_JSON_BODY =
      "{\"filter\": {\"tenantId\": \"testTenant1\"}}";
  private static final String LOGOUT_ENDPOINT = "/logout";

  // Tomcat 11+ omits Max-Age=0 when Expires is set to a past date, as per RFC 6265
  // the Expires attribute alone is sufficient for cookie invalidation
  private static final String EXPECTED_INVALIDATED_SESSION_COOKIE_HEADER =
      "camunda-session=; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/; SameSite=Lax";
  private static final String EXPECTED_INVALIDATED_CSRF_COOKIE_HEADER =
      "X-CSRF-TOKEN=; Expires=Thu, 01 Jan 1970 00:00:10 GMT; Path=/; SameSite=Lax";

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          .withAuthenticatedAccess()
          .withSecurityConfig(
              sc -> {
                final var csrfConfig = new CsrfConfiguration();
                csrfConfig.setEnabled(false);
                sc.setCsrf(csrfConfig);
              })
          .withAdditionalProfile("consolidated-auth");

  @Test
  public void logout() throws IOException, InterruptedException {
    final var webappClient = CAMUNDA_APPLICATION.newWebappClient();

    try (final var loggedInClient =
        webappClient.logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {

      // Verify access to secure endpoint before logout
      verifySecureEndpointAccessible(loggedInClient);

      // Perform logout
      performLogout(loggedInClient);

      // Verify access to secure endpoint is denied after logout
      verifySecureEndpointInaccessible(loggedInClient);
    }
  }

  private void verifySecureEndpointAccessible(final TestLoggedInWebappClient loggedInClient) {
    final var response = makeSecureEndpointRequest(loggedInClient);
    assertThat(response.statusCode())
        .as("Secure endpoint should be accessible before logout")
        .isEqualTo(HttpStatus.OK.value());
  }

  private void performLogout(final TestLoggedInWebappClient loggedInClient)
      throws IOException, InterruptedException {
    try (final var httpClient = HttpClient.newBuilder().build()) {
      final var logoutRequest = createLogoutRequest(loggedInClient);
      final var logoutResponse = httpClient.send(logoutRequest, BodyHandlers.ofString());

      assertThat(logoutResponse.statusCode())
          .as("Logout should return NO_CONTENT status")
          .isEqualTo(HttpStatus.NO_CONTENT.value());

      verifyLogoutCookies(logoutResponse);
    }
  }

  private void verifyLogoutCookies(final HttpResponse<String> logoutResponse) {
    assertThat(logoutResponse.headers().allValues(HttpHeaders.SET_COOKIE))
        .as("Logout should clear session and CSRF cookies")
        .contains(
            EXPECTED_INVALIDATED_SESSION_COOKIE_HEADER, EXPECTED_INVALIDATED_CSRF_COOKIE_HEADER);
  }

  private void verifySecureEndpointInaccessible(final TestLoggedInWebappClient loggedInClient) {
    final var response = makeSecureEndpointRequest(loggedInClient);
    assertThat(response.statusCode())
        .as("Secure endpoint should be inaccessible after logout")
        .isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  private HttpResponse<String> makeSecureEndpointRequest(
      final TestLoggedInWebappClient loggedInClient) {
    return loggedInClient.send(TENANTS_SEARCH_ENDPOINT, this::createSecureEndpointRequest).get();
  }

  private void createSecureEndpointRequest(final Builder httpRequestBuilder) {
    httpRequestBuilder
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .POST(BodyPublishers.ofString(TENANTS_SEARCH_JSON_BODY));
  }

  private HttpRequest createLogoutRequest(final TestLoggedInWebappClient loggedInClient) {
    final URI endpoint = loggedInClient.getRootEndpoint().resolve(LOGOUT_ENDPOINT);

    return HttpRequest.newBuilder()
        .uri(endpoint)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .header(HttpHeaders.COOKIE, loggedInClient.getSessionCookie().toString())
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
  }
}
