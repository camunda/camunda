/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies the scoped OIDC claim wiring (issue #57776) for the reported topology: a root Keycloak
 * provider (the unnamed default slot) configured alongside a physical-tenant scoped Auth0 provider
 * with its own identity claims.
 *
 * <p>The scoped provider must yield a converter and an identity-claim list keyed by its
 * registration id, using its own claims and not the root claims. The root registration ({@code
 * oidc}, the default slot) must stay absent from the derived maps so interactive root login keeps
 * the default converter.
 */
class PhysicalTenantOidcProvidersTest {

  private static final String ROOT_PREFIX = "camunda.security.authentication.oidc";
  private static final String SCOPED_PREFIX =
      "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.auth0";

  @Test
  void shouldResolveScopedProviderForItsTenantWithoutRootRegistration() {
    // given
    final var environment = rootKeycloakWithScopedAuth0();

    // when
    final var providers =
        PhysicalTenantOidcProviders.providersByPhysicalTenant(environment).get("tenanta");

    // then
    assertThat(providers).containsOnlyKeys("auth0");
    // the scoped provider keeps its own claim, not the root "preferred_username"
    assertThat(providers.get("auth0").getUsernameClaim()).isEqualTo("iss");
  }

  @Test
  void shouldNotResolveTenantProvidersWhenOnlyRootProviderConfigured() {
    // given
    final var environment = rootKeycloakOnly();

    // when / then
    assertThat(PhysicalTenantOidcProviders.providersByPhysicalTenant(environment)).isEmpty();
    assertThat(PhysicalTenantOidcProviders.defaultTenantNamedProviders(environment)).isEmpty();
  }

  @Test
  void shouldKeepRootSettingsOfNamedProviderForDefaultTenantWhenATenantOverridesIt() {
    // given
    final var environment = rootKeycloakOnly();
    environment.setProperty(
        "camunda.security.authentication.providers.oidc.okta.client-id", "okta-client");
    environment.setProperty(
        "camunda.security.authentication.providers.oidc.okta.username-claim", "root_user");
    environment.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.okta.username-claim",
        "user_a");

    // when
    final var defaultTenantProviders =
        PhysicalTenantOidcProviders.defaultTenantNamedProviders(environment);

    // then
    assertThat(defaultTenantProviders.get("okta").getUsernameClaim()).isEqualTo("root_user");
    // the root default slot is not a named provider, so the root keeps the default converter
    assertThat(defaultTenantProviders).doesNotContainKey("oidc");
  }

  @Test
  void shouldKeepDistinctSettingsForTenantsSharingARegistrationId() {
    // given
    final var environment = rootKeycloakOnly();
    environment.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.oidc.issuer-uri",
        "http://idp-a.local/realms/a");
    environment.setProperty(
        "camunda.physical-tenants.tenantb.security.authentication.oidc.issuer-uri",
        "http://idp-b.local/realms/b");

    // when
    final var providers = PhysicalTenantOidcProviders.providersByPhysicalTenant(environment);

    // then
    assertThat(providers.get("tenanta").get("oidc").getIssuerUri())
        .isEqualTo("http://idp-a.local/realms/a");
    assertThat(providers.get("tenantb").get("oidc").getIssuerUri())
        .isEqualTo("http://idp-b.local/realms/b");
  }

  private static MockEnvironment rootKeycloakOnly() {
    final var environment = new MockEnvironment();
    environment.setProperty("camunda.security.authentication.method", "oidc");
    // Root Keycloak provider in the unnamed default slot.
    environment.setProperty(ROOT_PREFIX + ".client-id", "keycloak-client");
    environment.setProperty(ROOT_PREFIX + ".issuer-uri", "http://keycloak.local/realms/camunda");
    environment.setProperty(ROOT_PREFIX + ".username-claim", "preferred_username");
    return environment;
  }

  private static MockEnvironment rootKeycloakWithScopedAuth0() {
    final var environment = rootKeycloakOnly();
    // Auth0-like scoped provider assigned to tenant "tenanta": iss as the username claim and a
    // custom client-id claim, both differing from the root Keycloak claims.
    environment.setProperty(SCOPED_PREFIX + ".username-claim", "iss");
    environment.setProperty(
        SCOPED_PREFIX + ".client-id-claim", "https://camunda.com/claims/client_id");
    environment.setProperty(SCOPED_PREFIX + ".prefer-username-claim", "true");
    environment.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]", "auth0");
    return environment;
  }
}
