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
import io.camunda.security.api.model.session.PersistentSession;
import io.camunda.security.core.port.out.SessionStorePort;
import io.camunda.security.spring.session.WebSessionAttributeConverter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;

/**
 * Reproduction of the NCSC "Insufficient Session Expiration" finding: an administrator resetting a
 * user's password invalidates the old credentials but leaves that user's already-established
 * browser session authenticating.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class PasswordResetSessionInvalidationIT {

  private static final String ME_ENDPOINT = "/v2/authentication/me";
  private static final String USERS_ENDPOINT = "/v2/users";
  private static final String LOGIN_ENDPOINT = "/login";

  private static final String SPRING_SECURITY_CONTEXT_KEY = "SPRING_SECURITY_CONTEXT";

  private static final String VICTIM = "password-reset-victim";
  private static final String EVICTION_VICTIM = "password-reset-eviction-victim";
  private static final String OLD_PASSWORD = "old-password";
  private static final String NEW_PASSWORD = "new-password";

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA =
      new TestCamundaApplication()
          .withAuthenticatedAccess()
          .withSecurityConfig(
              sc -> {
                final var csrf = new CsrfConfiguration();
                csrf.setEnabled(false);
                sc.setCsrf(csrf);
              })
          .withAdditionalProfile("consolidated-auth")
          .withProperty("camunda.security.session.persistent.enabled", true);

  @Test
  void shouldInvalidateExistingSessionWhenPasswordIsReset() throws Exception {
    final var webappClient = CAMUNDA.newWebappClient();

    try (final var adminSession =
        webappClient.logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {
      createVictim(adminSession);

      // given - the victim holds a logged-in browser session
      try (final var victimSession = webappClient.logIn(VICTIM, OLD_PASSWORD)) {
        assertMeStatus(victimSession, HttpStatus.OK, "the session authenticates before the reset");

        // when - an administrator resets the victim's password
        resetVictimPassword(adminSession);

        // then - the old password stops working, confirming the reset took effect
        final URI root = victimSession.getRootEndpoint();
        Awaitility.await("the old password is rejected at /login")
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(
                () ->
                    assertThat(login(root, VICTIM, OLD_PASSWORD))
                        .isEqualTo(HttpStatus.UNAUTHORIZED.value()));

        // and - the session established before the reset must no longer authenticate
        assertMeStatus(
            victimSession,
            HttpStatus.UNAUTHORIZED,
            "the session established before the reset must not survive it");
      }
    }
  }

  /**
   * Proof of concept for the proposed remediation: the sessions of a given user can already be
   * found and deleted with the APIs that exist today - a full scan over {@link
   * SessionStorePort#getAll()} plus the attribute converter that CSL's expiry sweep already uses -
   * with no schema change and no principal index.
   */
  @Test
  void shouldEvictSessionByPrincipalWithExistingStoreApis() throws Exception {
    final var webappClient = CAMUNDA.newWebappClient();

    try (final var adminSession =
        webappClient.logIn(DEFAULT_USER_USERNAME, DEFAULT_USER_PASSWORD)) {
      createVictim(adminSession, EVICTION_VICTIM);

      try (final var victimSession = webappClient.logIn(EVICTION_VICTIM, OLD_PASSWORD)) {
        assertMeStatus(victimSession, HttpStatus.OK, "the session authenticates before eviction");

        // when - every stored session belonging to the victim is deleted by principal
        final var evicted = evictSessionsOf(EVICTION_VICTIM);

        // then - the scan found the session, and deleting it takes effect immediately
        assertThat(evicted).as("the victim's session is found by principal").isEqualTo(1);
        assertMeStatus(
            victimSession, HttpStatus.UNAUTHORIZED, "the evicted session must stop authenticating");
      }
    }
  }

  private static int evictSessionsOf(final String username) {
    final SessionStorePort store = CAMUNDA.bean(SessionStorePort.class);
    final WebSessionAttributeConverter converter = CAMUNDA.bean(WebSessionAttributeConverter.class);

    final List<PersistentSession> owned =
        store.getAll().stream()
            .filter(session -> username.equals(principalOf(session, converter)))
            .toList();
    owned.forEach(session -> store.delete(session.id()));
    return owned.size();
  }

  private static String principalOf(
      final PersistentSession session, final WebSessionAttributeConverter converter) {
    final var serializedContext = session.attributes().get(SPRING_SECURITY_CONTEXT_KEY);
    if (serializedContext == null) {
      return null;
    }
    final var securityContext = (SecurityContext) converter.deserialize(serializedContext);
    return securityContext == null || securityContext.getAuthentication() == null
        ? null
        : securityContext.getAuthentication().getName();
  }

  private static void createVictim(final TestLoggedInWebappClient adminSession) {
    createVictim(adminSession, VICTIM);
  }

  private static void createVictim(
      final TestLoggedInWebappClient adminSession, final String username) {
    final var body =
        """
        {"username":"%s","password":"%s","name":"Password Reset Victim","email":"%s@test.com"}
        """
            .formatted(username, OLD_PASSWORD, username);
    final var response =
        adminSession
            .send(
                USERS_ENDPOINT,
                builder ->
                    builder
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .POST(BodyPublishers.ofString(body)))
            .get();
    assertThat(response.statusCode())
        .as("the victim user is created: %s", response.body())
        .isEqualTo(HttpStatus.CREATED.value());
  }

  private static void resetVictimPassword(final TestLoggedInWebappClient adminSession) {
    final var body =
        """
        {"password":"%s","name":"Password Reset Victim","email":"victim@test.com"}
        """
            .formatted(NEW_PASSWORD);
    final var response =
        adminSession
            .send(
                USERS_ENDPOINT + "/" + VICTIM,
                builder ->
                    builder
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .PUT(BodyPublishers.ofString(body)))
            .get();
    assertThat(response.statusCode())
        .as("the administrator resets the password: %s", response.body())
        .isEqualTo(HttpStatus.OK.value());
  }

  private static void assertMeStatus(
      final TestLoggedInWebappClient session, final HttpStatus expected, final String reason) {
    final var response = session.send(ME_ENDPOINT).get();
    assertThat(response.statusCode()).as(reason).isEqualTo(expected.value());
  }

  private static int login(final URI root, final String username, final String password)
      throws Exception {
    try (final var httpClient = HttpClient.newBuilder().build()) {
      final var request =
          HttpRequest.newBuilder()
              .uri(root.resolve(LOGIN_ENDPOINT))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(BodyPublishers.ofString("username=" + username + "&password=" + password))
              .build();
      return httpClient.send(request, BodyHandlers.ofString()).statusCode();
    }
  }
}
