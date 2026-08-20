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
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
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
    final OidcProvidersConfiguration providers =
        PhysicalTenantAuthenticationProviderConfigurations.forPhysicalTenant(
            "tenanta", environment);

    // then all root fields survive unchanged
    final var provider = providers.getOidc().get("tenanta");
    assertThat(provider.getIssuerUri()).isEqualTo("http://localhost:8082/realms/tenanta");
    assertThat(provider.getUsernameClaim()).isEqualTo("preferred_username");
    assertThat(provider.getClientId()).isEqualTo("root-client");
  }

  @Test
  void shouldResolveWithoutErrorWhenNoNamedProvidersAreConfigured() {
    // given a config that uses only the default slot, so no providers map exists
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.security.authentication.oidc.issuer-uri",
                    "http://localhost:8081/realms/default",
                    "camunda.security.authentication.oidc.client-id",
                    "default-client")));

    // when / then resolving a tenant does not throw and yields no named providers
    // (the flat default slot itself is outside this overlay — covered by the resolver test)
    final OidcProvidersConfiguration providers =
        PhysicalTenantAuthenticationProviderConfigurations.forPhysicalTenant(
            "tenanta", environment);
    assertThat(providers.getOidc()).isEmpty();
  }

  @Test
  void shouldKeepRootProvidersWhenTenantOverlayHasEmptyProvidersMap() {
    // given root declares two named providers, and the tenant's only overlay is an empty
    // `providers` map. Spring Boot 4.1 surfaces an empty YAML map (`providers: {}`) as an empty
    // property value; binding it onto the root instance must not reset the inherited providers.
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.security.authentication.providers.oidc.tenanta.issuer-uri",
                    "http://localhost:8082/realms/tenanta",
                    "camunda.security.authentication.providers.oidc.tenanta.client-id",
                    "tenanta-client",
                    "camunda.security.authentication.providers.oidc.tenantb.issuer-uri",
                    "http://localhost:8083/realms/tenantb",
                    "camunda.security.authentication.providers.oidc.tenantb.client-id",
                    "tenantb-client",
                    "camunda.physical-tenants.default.security.authentication.providers",
                    "")));

    // when
    final OidcProvidersConfiguration providers =
        PhysicalTenantAuthenticationProviderConfigurations.forPhysicalTenant(
            "default", environment);

    // then both inherited providers survive the empty overlay
    assertThat(providers.getOidc()).containsKeys("tenanta", "tenantb");
    assertThat(providers.getOidc().get("tenanta").getIssuerUri())
        .isEqualTo("http://localhost:8082/realms/tenanta");
    assertThat(providers.getOidc().get("tenantb").getIssuerUri())
        .isEqualTo("http://localhost:8083/realms/tenantb");
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
    final OidcProvidersConfiguration providers =
        PhysicalTenantAuthenticationProviderConfigurations.forPhysicalTenant(
            "tenanta", environment);

    // then root fields are preserved despite the PT overlay
    final var provider = providers.getOidc().get("tenanta");
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
    final OidcProvidersConfiguration providers =
        PhysicalTenantAuthenticationProviderConfigurations.forPhysicalTenant(
            "tenanta", environment);

    // then both root and PT-private providers are present
    assertThat(providers.getOidc()).containsKey("sharedprovider");
    assertThat(providers.getOidc()).containsKey("privateprovider");
    assertThat(providers.getOidc().get("privateprovider").getClientId())
        .isEqualTo("private-client");
  }
}
