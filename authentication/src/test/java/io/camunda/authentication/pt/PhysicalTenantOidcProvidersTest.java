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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies that the scoped OIDC claim wiring (issue #57776) is derived from the merged
 * physical-tenant configuration: a physical tenant with its own named provider and provider-scoped
 * identity claims yields a converter and an identity-claim list keyed by that registration id,
 * while the root registration keeps the default converter.
 */
@ExtendWith(MockitoExtension.class)
class PhysicalTenantOidcProvidersTest {

  private static final String PREFIX =
      "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.auth0";

  @Mock private MembershipPort membershipPort;

  @Test
  void shouldFlattenNamedPhysicalTenantProvidersByRegistrationId() {
    // given
    final var environment = environmentWithScopedAuth0Provider();

    // when
    final var providers = PhysicalTenantOidcProviders.providersByRegistrationId(environment);

    // then
    assertThat(providers).containsKey("auth0");
    assertThat(providers.get("auth0").getUsernameClaim()).isEqualTo("iss");
    // the unnamed default slot ("oidc") is not a named provider, so the root keeps its converter
    assertThat(providers).doesNotContainKey("oidc");
  }

  @Test
  void shouldBuildScopedTokenClaimsConverterPerRegistration() {
    // given
    final var environment = environmentWithScopedAuth0Provider();

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
    final var environment = environmentWithScopedAuth0Provider();

    // when
    final var identityClaims =
        PhysicalTenantOidcProviders.identityClaimsByRegistrationId(environment);

    // then
    assertThat(identityClaims)
        .containsEntry("auth0", java.util.List.of("iss", "https://camunda.com/claims/client_id"));
  }

  @Test
  void shouldReturnEmptyMapsWhenNoPhysicalTenantProvidersConfigured() {
    // given
    final var environment = new MockEnvironment();
    environment.setProperty("camunda.security.authentication.method", "oidc");

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

  private static MockEnvironment environmentWithScopedAuth0Provider() {
    final var environment = new MockEnvironment();
    environment.setProperty("camunda.security.authentication.method", "oidc");
    // Auth0-like scoped provider: iss as the username claim and a custom client-id claim.
    environment.setProperty(PREFIX + ".username-claim", "iss");
    environment.setProperty(PREFIX + ".client-id-claim", "https://camunda.com/claims/client_id");
    environment.setProperty(PREFIX + ".prefer-username-claim", "true");
    return environment;
  }
}
