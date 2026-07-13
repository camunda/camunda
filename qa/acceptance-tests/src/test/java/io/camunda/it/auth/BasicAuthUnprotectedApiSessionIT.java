/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.security.api.model.config.initialization.InitializationConfiguration.DEFAULT_USER_PASSWORD;
import static io.camunda.security.api.model.config.initialization.InitializationConfiguration.DEFAULT_USER_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestWebappClient.TestLoggedInWebappClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.config.CsrfConfiguration;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Verifies that a session created via {@code /login} on the BASIC webapp chain is recognised on the
 * unprotected API chain (active when {@code camunda.security.authentication.unprotected-api=true}),
 * and invalidated there after logout.
 *
 * <p>Uses no persistent-session storage and no physical tenants — the plain, in-memory-session
 * shape of c8Run's default deployment. See {@link PhysicalTenantWebSessionUnprotectedApiIT} for the
 * same guard under persistent sessions with physical tenants configured.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class BasicAuthUnprotectedApiSessionIT {

  private static final String ME_ENDPOINT = "/v2/authentication/me";
  private static final String LOGOUT_ENDPOINT = "/logout";

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA =
      new TestCamundaApplication()
          .withBasicAuth()
          .withSecurityConfig(
              sc -> {
                final var csrf = new CsrfConfiguration();
                csrf.setEnabled(false);
                sc.setCsrf(csrf);
              })
          .withUnauthenticatedAccess();

  @Test
  void shouldRecognizeAndInvalidateSessionOnUnprotectedApiChain() throws Exception {
    try (final var loggedIn =
        CAMUNDA.newWebappClient().logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {
      // given — logging in via POST /login creates a session on the BASIC webapp chain

      // then — it must be recognised on the unprotected API chain, not treated as anonymous
      assertCurrentUserStatus(
          loggedIn,
          HttpStatus.OK,
          "session created on the BASIC webapp chain must be recognised on the unprotected API"
              + " chain");

      logOut(loggedIn);

      // then — logout must invalidate the session on the unprotected chain too
      assertCurrentUserStatus(
          loggedIn,
          HttpStatus.UNAUTHORIZED,
          "logged-out session must not be recognised on the unprotected API chain either");
    }
  }

  private static void assertCurrentUserStatus(
      final TestLoggedInWebappClient client, final HttpStatus expected, final String reason) {
    final var response = client.send(ME_ENDPOINT).get();
    assertThat(response.statusCode()).as(reason).isEqualTo(expected.value());
  }

  private static void logOut(final TestLoggedInWebappClient client)
      throws IOException, InterruptedException {
    try (final var httpClient = HttpClient.newBuilder().build()) {
      final var request =
          HttpRequest.newBuilder()
              .uri(client.getRootEndpoint().resolve(LOGOUT_ENDPOINT))
              .header(HttpHeaders.COOKIE, client.getSessionCookie().toString())
              .POST(BodyPublishers.noBody())
              .build();
      final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
      assertThat(response.statusCode())
          .as("logout should succeed")
          .isEqualTo(HttpStatus.NO_CONTENT.value());
    }
  }
}
