/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestWebappClient;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Regression guard for per-physical-tenant session-cookie isolation with persistent web sessions.
 *
 * <p>When persistent web sessions are enabled, a global Spring Session filter handles the session
 * cookie for every request ahead of the per-tenant security chains. It must honour the per-tenant
 * cookie scope: logging in under {@code /physical-tenants/<id>} has to set a session cookie named
 * {@code camunda-session-physical-tenants-<id>} bound to {@code Path=/physical-tenants/<id>}, and
 * must not set the unscoped default {@code camunda-session} at {@code Path=/}. An unscoped cookie
 * would be sent across every tenant's path and collapse session isolation.
 *
 * <p>Physical-tenant secondary storage is RDBMS-only, so this test is gated to RDBMS backends. Each
 * tenant is provisioned with isolated storage and a seeded {@code <id>-admin} user.
 */
@MultiDbTest
@MultiDbPhysicalTenants({
  PhysicalTenantSessionCookieIsolationIT.TENANT_A,
  PhysicalTenantSessionCookieIsolationIT.TENANT_B
})
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason = "Per-physical-tenant secondary storage is only supported on RDBMS backends")
public class PhysicalTenantSessionCookieIsolationIT {

  static final String TENANT_A = "tenanta";
  static final String TENANT_B = "tenantb";

  private static final String PT_ADMIN_PASSWORD = "ptadmin";
  private static final String DEFAULT_SESSION_COOKIE = "camunda-session";
  private static final String ROOT_PATH = "/";

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withAdditionalProfile("consolidated-auth")
          .withProperty("camunda.security.session.persistent.enabled", true);

  @Test
  void shouldScopeSessionCookieToPhysicalTenantOnLogin() {
    // given a broker hosting two physical tenants with persistent web sessions enabled

    // when logging in through each tenant's prefixed webapp path
    final List<HttpCookie> tenantACookies = logInUnderPhysicalTenant(TENANT_A);
    final List<HttpCookie> tenantBCookies = logInUnderPhysicalTenant(TENANT_B);

    // then each login sets only that tenant's scoped session cookie, never the unscoped default
    assertSessionCookieScopedTo(tenantACookies, TENANT_A);
    assertSessionCookieScopedTo(tenantBCookies, TENANT_B);
  }

  private static void assertSessionCookieScopedTo(
      final List<HttpCookie> cookies, final String tenantId) {
    final String expectedName = "camunda-session-physical-tenants-" + tenantId;
    final String expectedPath = "/physical-tenants/" + tenantId;

    assertThat(cookies)
        .as("login under %s must set the per-tenant session cookie scoped to its path", tenantId)
        .anySatisfy(
            cookie -> {
              assertThat(cookie.getName()).isEqualTo(expectedName);
              assertThat(cookie.getPath()).isEqualTo(expectedPath);
            });

    assertThat(cookies)
        .as("login under %s must not set the unscoped default session cookie at the root", tenantId)
        .noneSatisfy(
            cookie -> {
              assertThat(cookie.getName()).isEqualTo(DEFAULT_SESSION_COOKIE);
              assertThat(cookie.getPath()).isEqualTo(ROOT_PATH);
            });
  }

  private static List<HttpCookie> logInUnderPhysicalTenant(final String tenantId) {
    final String base = CAMUNDA.restAddress().toString().replaceAll("/+$", "");
    final URI tenantRoot = URI.create(base + "/physical-tenants/" + tenantId + "/");
    try (final var loggedIn =
        new TestWebappClient(tenantRoot).logIn(tenantId + "-admin", PT_ADMIN_PASSWORD)) {
      return loggedIn.getCookies();
    }
  }
}
