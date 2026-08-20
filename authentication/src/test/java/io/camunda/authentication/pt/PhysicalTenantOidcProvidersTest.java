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
  void shouldFlattenScopedProviderWithoutRootRegistration() {
    // given
    final var environment = rootKeycloakWithScopedAuth0();

    // when
    final var providers = PhysicalTenantOidcProviders.providersByRegistrationId(environment);

    // then
    assertThat(providers).containsOnlyKeys("auth0");
    // the scoped provider keeps its own claim, not the root "preferred_username"
    assertThat(providers.get("auth0").getUsernameClaim()).isEqualTo("iss");
    // the root default slot is not a named provider, so the root keeps the default converter
    assertThat(providers).doesNotContainKey("oidc");
  }

  @Test
  void shouldBuildScopedTokenClaimsConverterOnlyForScopedRegistration() {
    // given
    final var environment = rootKeycloakWithScopedAuth0();

    // when
    final var converters =
        PhysicalTenantOidcProviders.tokenClaimsConvertersByRegistrationId(
            environment, membershipPort, MembershipResolutionContextPropagator.identity());

    // then
    assertThat(converters).containsOnlyKeys("auth0");
    assertThat(converters.get("auth0")).isNotNull();
  }

  @Test
  void shouldCollectScopedIdentityClaimsForUriNormalization() {
    // given
    final var environment = rootKeycloakWithScopedAuth0();

    // when
    final var identityClaims =
        PhysicalTenantOidcProviders.identityClaimsByRegistrationId(environment);

    // then
    assertThat(identityClaims)
        .containsOnlyKeys("auth0")
        .containsEntry("auth0", List.of("iss", "https://camunda.com/claims/client_id"));
  }

  @Test
  void shouldReturnEmptyMapsWhenOnlyRootProviderConfigured() {
    // given
    final var environment = rootKeycloakOnly();

    // when
    final var providers = PhysicalTenantOidcProviders.providersByRegistrationId(environment);
    final var converters =
        PhysicalTenantOidcProviders.tokenClaimsConvertersByRegistrationId(
            environment, membershipPort, MembershipResolutionContextPropagator.identity());
    final var identityClaims =
        PhysicalTenantOidcProviders.identityClaimsByRegistrationId(environment);

    // then
    assertThat(providers).isEmpty();
    assertThat(converters).isEmpty();
    assertThat(identityClaims).isEmpty();
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
