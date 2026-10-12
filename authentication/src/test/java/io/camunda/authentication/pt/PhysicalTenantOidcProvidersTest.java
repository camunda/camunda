/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.core.port.out.MembershipPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
@ExtendWith(MockitoExtension.class)
class PhysicalTenantOidcProvidersTest {

  private static final String ROOT_PREFIX = "camunda.security.authentication.oidc";
  private static final String SCOPED_PREFIX =
      "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.auth0";

  @Mock private MembershipPort membershipPort;

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
  void shouldBuildTokenClaimsConverterForEachResolvedRegistration() {
    // given
    final var providers =
        PhysicalTenantOidcProviders.providersByPhysicalTenant(rootKeycloakWithScopedAuth0())
            .get("tenanta");

    // when
    final var converters =
        PhysicalTenantOidcProviders.tokenClaimsConverters(
            providers, membershipPort, MembershipResolutionContextPropagator.identity());

    // then
    assertThat(converters).containsOnlyKeys("auth0");
    assertThat(converters.get("auth0")).isNotNull();
  }

  @Test
  void shouldCollectIdentityClaimsForUriNormalization() {
    // given
    final var providers =
        PhysicalTenantOidcProviders.providersByPhysicalTenant(rootKeycloakWithScopedAuth0())
            .get("tenanta");

    // when
    final var identityClaims =
        PhysicalTenantOidcProviders.identityClaimsByRegistrationId(providers);

    // then
    assertThat(identityClaims)
        .containsOnlyKeys("auth0")
        .containsEntry("auth0", List.of("iss", "https://camunda.com/claims/client_id"));
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
  void shouldResolveTenantOverrideOfDefaultSlotIssuerPerTenant() {
    // given
    final var environment = rootKeycloakOnly();
    environment.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.oidc.issuer-uri",
        "http://idp-a.local/realms/a");

    // when
    final var providers = PhysicalTenantOidcProviders.providersByPhysicalTenant(environment);

    // then
    assertThat(providers).containsOnlyKeys("tenanta");
    assertThat(providers.get("tenanta").get("oidc").getIssuerUri())
        .isEqualTo("http://idp-a.local/realms/a");
  }

  @Test
  void shouldResolveTenantOnlyProviderSettingsPerTenant() {
    // given
    final var environment = rootKeycloakWithScopedAuth0();
    environment.setProperty(SCOPED_PREFIX + ".issuer-uri", "https://auth0.example.com/");
    environment.setProperty(SCOPED_PREFIX + ".id-token-algorithm", "ES256");
    environment.setProperty(SCOPED_PREFIX + ".prefer-id-token-claims", "true");

    // when
    final var providers = PhysicalTenantOidcProviders.providersByPhysicalTenant(environment);

    // then
    final var auth0 = providers.get("tenanta").get("auth0");
    assertThat(auth0.getIssuerUri()).isEqualTo("https://auth0.example.com/");
    assertThat(auth0.getIdTokenAlgorithm()).isEqualTo("ES256");
    assertThat(auth0.isPreferIdTokenClaims()).isTrue();
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
