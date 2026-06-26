/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantOidcProviderOverlayTest {

  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldInheritRootProviderWhenTenantHasNoOverride() {
    // given
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://localhost:8082/realms/tenanta",
                    "camunda.security.authentication.providers.oidc.tenanta.username-claim",
                    "preferred_username",
                    "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "root-client")));

    // when
    final AuthenticationConfiguration auth =
        PhysicalTenantOidcProviderConfigurations.forPhysicalTenant("tenanta", environment);

    // then all root fields survive unchanged
    final var provider = auth.getProviders().getOidc().get("tenanta");
    assertThat(provider.getIssuerUri()).isEqualTo("http://localhost:8082/realms/tenanta");
    assertThat(provider.getUsernameClaim()).isEqualTo("preferred_username");
    assertThat(provider.getClientId()).isEqualTo("root-client");
  }

  @Test
  void shouldOverrideOnlyTheFieldsTenantSets() {
    // given root declares a full tenanta provider; the PT overlay overrides only
    // client-id/secret/audiences — omitting issuer-uri, username-claim, client-id-claim,
    // and redirect-uri. Without the snapshot-then-rebind strategy those root fields would be lost.
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://localhost:8082/realms/tenanta",
                    "camunda.security.authentication.providers.oidc.tenanta.username-claim",
                    "preferred_username",
                    "camunda.security.authentication.providers.oidc.tenanta.client-id-claim",
                    "client_id",
                    "camunda.security.authentication.providers.oidc.tenanta.redirect-uri",
                    "{baseUrl}/sso-callback",
                    "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "root-client",
                    "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-id",
                    "pt-tenanta-client",
                    "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.client-secret",
                    "tenanta-secret",
                    "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.audiences[0]",
                    "pt-tenanta-aud")));

    // when
    final AuthenticationConfiguration auth =
        PhysicalTenantOidcProviderConfigurations.forPhysicalTenant("tenanta", environment);

    // then root fields are preserved despite the PT overlay
    final var provider = auth.getProviders().getOidc().get("tenanta");
    assertThat(provider.getIssuerUri()).isEqualTo("http://localhost:8082/realms/tenanta");
    assertThat(provider.getUsernameClaim()).isEqualTo("preferred_username");
    assertThat(provider.getClientIdClaim()).isEqualTo("client_id");
    assertThat(provider.getRedirectUri()).isEqualTo("{baseUrl}/sso-callback");

    // and the PT override fields win
    assertThat(provider.getClientId()).isEqualTo("pt-tenanta-client");
    assertThat(provider.getClientSecret()).isEqualTo("tenanta-secret");
    assertThat(provider.getAudiences()).containsExactly("pt-tenanta-aud");
  }

  @Test
  void shouldAddTenantPrivateProviderNotInRoot() {
    // given root declares "sharedprovider"; tenant adds a new "privateprovider" not in root
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.security.authentication.providers.oidc.sharedprovider.issuer-uri",
                    "http://localhost:8081/realms/default",
                    "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.privateprovider.issuer-uri",
                    "http://localhost:8082/realms/private",
                    "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.privateprovider.client-id",
                    "private-client")));

    // when
    final AuthenticationConfiguration auth =
        PhysicalTenantOidcProviderConfigurations.forPhysicalTenant("tenanta", environment);

    // then both root and PT-private providers are present
    assertThat(auth.getProviders().getOidc()).containsKey("sharedprovider");
    assertThat(auth.getProviders().getOidc()).containsKey("privateprovider");
    assertThat(auth.getProviders().getOidc().get("privateprovider").getClientId())
        .isEqualTo("private-client");
  }

  @Test
  void shouldPreserveRootFlatOidcSlotUnchanged() {
    // given root sets the flat oidc slot (not a named provider)
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.security.authentication.oidc.issuer-uri",
                    "http://localhost:8081/realms/default",
                    "camunda.security.authentication.oidc.username-claim",
                    "preferred_username")));

    // when
    final AuthenticationConfiguration auth =
        PhysicalTenantOidcProviderConfigurations.forPhysicalTenant("tenanta", environment);

    // then the flat slot survives
    assertThat(auth.getOidc().getIssuerUri()).isEqualTo("http://localhost:8081/realms/default");
    assertThat(auth.getOidc().getUsernameClaim()).isEqualTo("preferred_username");
  }
}
