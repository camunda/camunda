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
import io.camunda.qa.util.cluster.TestWebappClient;
import io.camunda.qa.util.cluster.TestWebappClient.TestLoggedInWebappClient;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.config.CsrfConfiguration;
import io.camunda.security.spring.security.CamundaSecurityFilterChainConstants;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
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
 * End-to-end regression guard for #55852: a physical tenant's persistent web session must be
 * written to and read from <em>that tenant's</em> store at Spring Session's commit — which runs in
 * a {@code finally} block <em>after</em> {@code DispatcherServlet} has already torn down the
 * request scope that per-request context (e.g. the resolved physical tenant) would normally live
 * in.
 *
 * <p>Drives real HTTP requests through the actual filter chain — no MockMvc, no direct store access
 * — so the commit phase genuinely executes: login (session created and committed) then a second
 * request reuses it (only possible if the commit landed in the store the same scope reads from),
 * then logout deletes it. Covers the non-PT/default path and the multi-PT paths, and asserts a
 * session minted under one physical tenant is rejected under a different physical tenant's chain
 * and under the default chain — proving genuine per-store isolation, not accidental pass-through.
 *
 * <p>{@code /v2/authentication/me} is used as the probe: a GET (so CSRF, disabled here anyway,
 * never enters into it) that only requires a valid authenticated principal. It is auto-registered
 * under each physical tenant's prefix by {@code PhysicalTenantRequestMappingHandlerMapping}, so
 * {@code /physical-tenants/<id>/v2/authentication/me} reaches the same controller as the unprefixed
 * route.
 *
 * <p>Physical-tenant secondary storage is RDBMS-only, so this test is gated to RDBMS backends.
 */
@MultiDbTest
@MultiDbPhysicalTenants({
  PhysicalTenantWebSessionCommitRoutingIT.TENANT_A,
  PhysicalTenantWebSessionCommitRoutingIT.TENANT_B
})
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason = "Per-physical-tenant secondary storage is only supported on RDBMS backends")
public class PhysicalTenantWebSessionCommitRoutingIT {

  static final String TENANT_A = "tenanta";
  static final String TENANT_B = "tenantb";

  private static final String PT_ADMIN_PASSWORD = "ptadmin";
  private static final String ME_ENDPOINT = "v2/authentication/me";
  private static final String LOGOUT_ENDPOINT = "logout";
  private static final String DEFAULT_SESSION_COOKIE =
      CamundaSecurityFilterChainConstants.SESSION_COOKIE;

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withAdditionalProfile("consolidated-auth")
          .withProperty("camunda.security.session.persistent.enabled", true)
          .withSecurityConfig(
              sc -> {
                // CSRF is orthogonal to what this test verifies (session-store commit routing);
                // disabling it keeps the login/logout/reuse flow to plain GET/POST + Cookie header,
                // matching BasicAuthLogoutIT's precedent.
                final var csrf = new CsrfConfiguration();
                csrf.setEnabled(false);
                sc.setCsrf(csrf);
              });

  @Test
  void defaultPathSessionIsCommittedReusedAndInvalidatedOnLogout() {
    try (final var loggedIn =
        CAMUNDA.newWebappClient().logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {
      assertAuthenticated(loggedIn, "first request must commit the session to the default store");
      assertAuthenticated(
          loggedIn, "second request must reuse the committed default-store session");

      logOut(loggedIn, DEFAULT_SESSION_COOKIE);

      assertUnauthenticated(loggedIn, "session must be gone from the default store after logout");
    }
  }

  @Test
  void physicalTenantSessionIsCommittedReusedAndInvalidatedOnLogout() {
    assertPhysicalTenantSessionLifecycle(TENANT_A);
    assertPhysicalTenantSessionLifecycle(TENANT_B);
  }

  private void assertPhysicalTenantSessionLifecycle(final String tenantId) {
    try (final var loggedIn = logInUnderPhysicalTenant(tenantId)) {
      assertAuthenticated(
          loggedIn, "first request under " + tenantId + " must commit the session to its store");
      assertAuthenticated(
          loggedIn,
          "second request under " + tenantId + " must reuse the session committed to its store");

      logOut(loggedIn, cookieNameFor(tenantId));

      assertUnauthenticated(
          loggedIn, "session must be gone from " + tenantId + "'s store after logout");
    }
  }

  @Test
  void sessionFromOnePhysicalTenantIsRejectedOnAnotherPhysicalTenant() {
    try (final var loggedInA = logInUnderPhysicalTenant(TENANT_A)) {
      assertAuthenticated(loggedInA, "sanity check: tenant A's session must work under tenant A");

      final var sessionIdValue = findCookie(loggedInA, cookieNameFor(TENANT_A)).getValue();
      final var response = sendWithForgedCookie(TENANT_B, cookieNameFor(TENANT_B), sessionIdValue);

      assertThat(response.statusCode())
          .as(
              "tenant A's session id must not be honoured under tenant B's store — they are"
                  + " separate stores, not a shared one routed by request context")
          .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
  }

  @Test
  void sessionFromPhysicalTenantIsRejectedOnDefaultPath() {
    try (final var loggedInA = logInUnderPhysicalTenant(TENANT_A)) {
      final var sessionIdValue = findCookie(loggedInA, cookieNameFor(TENANT_A)).getValue();
      final var response = sendWithForgedCookie(null, DEFAULT_SESSION_COOKIE, sessionIdValue);

      assertThat(response.statusCode())
          .as("tenant A's session id must not be honoured under the default store")
          .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
  }

  private static String cookieNameFor(final String tenantId) {
    return DEFAULT_SESSION_COOKIE + "-physical-tenants-" + tenantId;
  }

  private static HttpResponse<String> sendWithForgedCookie(
      final String tenantIdOrNull, final String cookieName, final String cookieValue) {
    final var root = tenantRoot(tenantIdOrNull);
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var request =
          HttpRequest.newBuilder()
              .uri(root.resolve(ME_ENDPOINT))
              .header(HttpHeaders.COOKIE, cookieName + "=" + cookieValue)
              .build();
      return httpClient.send(request, BodyHandlers.ofString());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void assertAuthenticated(
      final TestLoggedInWebappClient client, final String reason) {
    final var response = client.send(ME_ENDPOINT).get();
    assertThat(response.statusCode()).as(reason).isEqualTo(HttpStatus.OK.value());
  }

  private static void assertUnauthenticated(
      final TestLoggedInWebappClient client, final String reason) {
    final var response = client.send(ME_ENDPOINT).get();
    assertThat(response.statusCode()).as(reason).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  private static void logOut(
      final TestLoggedInWebappClient client, final String sessionCookieName) {
    try (final var httpClient = HttpClient.newBuilder().build()) {
      final var request =
          HttpRequest.newBuilder()
              .uri(client.getRootEndpoint().resolve(LOGOUT_ENDPOINT))
              .header(HttpHeaders.COOKIE, findCookie(client, sessionCookieName).toString())
              .POST(BodyPublishers.noBody())
              .build();
      final var response = httpClient.send(request, BodyHandlers.ofString());
      assertThat(response.statusCode()).as("logout should succeed").isEqualTo(204);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static HttpCookie findCookie(final TestLoggedInWebappClient client, final String name) {
    return client.getCookies().stream()
        .filter(c -> name.equals(c.getName()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Cookie not found: " + name));
  }

  private static TestLoggedInWebappClient logInUnderPhysicalTenant(final String tenantId) {
    return new TestWebappClient(tenantRoot(tenantId)).logIn(tenantId + "-admin", PT_ADMIN_PASSWORD);
  }

  private static URI tenantRoot(final String tenantIdOrNull) {
    final String base = CAMUNDA.restAddress().toString().replaceAll("/+$", "");
    return tenantIdOrNull == null
        ? URI.create(base + "/")
        : URI.create(base + "/physical-tenants/" + tenantIdOrNull + "/");
  }
}
