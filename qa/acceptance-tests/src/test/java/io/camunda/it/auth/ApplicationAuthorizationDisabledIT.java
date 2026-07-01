/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Reproducer for the "webapp still enforces authorization when authorizations are disabled" bug.
 *
 * <p>With {@code camunda.security.authorizations.enabled=false} but authentication enabled (the
 * SaaS/OIDC default, mirrored here with basic auth), the server-side endpoints that serve the
 * webapps ({@code /operate}, {@code /admin}, ...) must not perform a component-access check: the
 * APIs allow all (via {@code DisabledResourceAccessProvider}) and the frontend does not enforce, so
 * a logged-in user without any {@code COMPONENT} grant must reach the webapp rather than being
 * redirected to {@code /<webapp>/forbidden}.
 *
 * <p>Before the fix, CSL's {@code WebAppAuthorizationCheckFilter} consulted the live authorization
 * records regardless of the flag, so only principals carrying a {@code COMPONENT}/{@code ACCESS}
 * grant (e.g. an admin/owner) passed while everyone else got a 302 to the forbidden page. Contrast
 * with {@link ApplicationAuthorizationIT}, which asserts that same redirect when authorizations are
 * enabled.
 *
 * <p>Runs as a {@link MultiDbTest} even though no authorization records are read when
 * authorizations are disabled. The webapp authorization filter under test is only wired when
 * secondary storage is enabled: the host {@code resourcePermissionPort} and {@code
 * authorizationRepositoryPort} beans are {@code @ConditionalOnSecondaryStorageEnabled}, and CSL's
 * {@code WebAppAuthorizationCheckFilter} is {@code @ConditionalOnBean(ResourcePermissionPort)}.
 * Without a real backend the filter would never be created and this test would pass for the wrong
 * reason (filter absent rather than correctly passing through), missing a future regression where
 * the filter is present but stops honouring the flag. A real backend wires the security chain
 * exactly as in production; {@code LOCAL} is the lightest such backend.
 *
 * <p>This test can only be run against RDBMS when all applications, such as Operate, are also
 * running on RDBMS.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ApplicationAuthorizationDisabledIT {

  private static final String WEBAPP_USER_PATH = "/user";

  @MultiDbTestApplication
  private static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withBasicAuth()
          .withAuthorizationsDisabled()
          // withAuthenticatedAccess must be done after disabling authorizations as the latter
          // implicitly sets withUnauthenticatedAccess(); we still want login to be required so the
          // webapp authorization filter runs against an authenticated principal.
          .withAuthenticatedAccess();

  private static final String RESTRICTED = "restricted";
  private static final String DEFAULT_PASSWORD = "password";

  // A user without any COMPONENT access grant — the "Sergi" case: no admin/owner role.
  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @ParameterizedTest
  @ValueSource(strings = {"operate", "admin"})
  void accessComponentUserWithoutComponentAccessAllowedWhenAuthorizationsDisabled(
      final String appName) throws IOException, URISyntaxException, InterruptedException {
    // given
    final var webappClient = STANDALONE_CAMUNDA.newWebappClient();

    try (final var loggedInClient = webappClient.logIn(RESTRICTED, DEFAULT_PASSWORD)) {
      // when
      final Either<Exception, HttpResponse<String>> result =
          loggedInClient.send(appName + WEBAPP_USER_PATH);

      // then
      assertThat(result.isLeft()).isFalse();
      final HttpResponse<String> response = result.get();
      assertAccessAllowed(response);
    }
  }

  private static void assertAccessAllowed(final HttpResponse<String> response) {
    // Assert a successful (2xx) response rather than merely "not forbidden": the webapp endpoint
    // serves the SPA via a server-side forward on success, so a 2xx is expected. A weaker
    // "not 401/403/302" check would also pass on a 500 or a stray redirect and hide a real
    // breakage unrelated to authorization.
    assertThat(response.statusCode()).isBetween(200, 299);
  }
}
