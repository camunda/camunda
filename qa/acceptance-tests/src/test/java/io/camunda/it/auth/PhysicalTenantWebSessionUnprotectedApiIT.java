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
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Verifies that a session created via {@code /login} on the default path is recognised on the
 * unprotected API chain, and invalidated there after logout, with persistent sessions and a
 * physical tenant configured alongside it. See {@link BasicAuthUnprotectedApiSessionIT} for the
 * plain, in-memory-session scenario.
 *
 * <p>Only exercises the default (non-PT) path. The tenant is provisioned to prove that behaviour
 * holds when a physical tenant is configured alongside it; whether a physical tenant's own scoped
 * API chain (which also has an unprotected variant when {@code unprotected-api=true}, built via
 * {@code ScopedApiSecurityChainBuilder#buildUnprotectedScopedApiChain}) recognises sessions the
 * same way is a separate, unverified surface not covered here.
 */
@MultiDbTest
@MultiDbPhysicalTenants({PhysicalTenantWebSessionUnprotectedApiIT.TENANT_A})
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason = "Per-physical-tenant secondary storage is only supported on RDBMS backends")
public class PhysicalTenantWebSessionUnprotectedApiIT {

  static final String TENANT_A = "tenanta";

  private static final String ME_ENDPOINT = "/v2/authentication/me";
  private static final String LOGOUT_ENDPOINT = "/logout";

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA =
      new TestCamundaApplication()
          .withBasicAuth()
          .withProperty("camunda.security.session.persistent.enabled", true)
          .withSecurityConfig(
              sc -> {
                final var csrf = new CsrfConfiguration();
                csrf.setEnabled(false);
                sc.setCsrf(csrf);
              })
          .withUnauthenticatedAccess();

  @Test
  void shouldRecognizeAndInvalidateDefaultPathSessionOnUnprotectedApiChain() throws Exception {
    try (final var loggedIn =
        CAMUNDA.newWebappClient().logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {
      // given/when — a session created via /login on the default path, with persistent sessions
      // and a physical tenant configured alongside it

      // then — it must be recognised on the unprotected API chain, not treated as anonymous
      assertCurrentUserStatus(
          loggedIn,
          HttpStatus.OK,
          "session created on the default webapp chain must be recognised on the unprotected API"
              + " chain with persistent sessions and physical tenants configured");

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
