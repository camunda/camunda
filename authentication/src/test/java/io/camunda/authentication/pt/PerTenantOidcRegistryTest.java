/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Unit tests for {@link PerTenantOidcRegistry}. We use the package-private factory hook to bypass
 * OIDC discovery — calling Keycloak from a unit test is out of scope.
 *
 * <p>Note: {@code SecurityConfiguration.getAuthentication().getProviders()} on the external CSL
 * library does not expose an {@code assigned} field, so the registry accepts the {@code assigned}
 * list as a separate parameter (see the registry's javadoc for the rationale).
 */
class PerTenantOidcRegistryTest {

  @Test
  void shouldResolveAssignedOidcToTheDefaultProviderSlot() {
    // given - authentication.oidc.* is the default provider (registration id "oidc")
    final var security = sec(oidc("oidc"), Map.of());
    // when
    final var repository =
        PerTenantOidcRegistry.buildFor("tenanta", security, List.of("oidc"), stubBuilder());
    // then
    assertThat(repository.findByRegistrationId("oidc")).isNotNull();
  }

  @Test
  void shouldResolveAssignedNamedProviderUnderProvidersOidcMap() {
    final var security = sec(null, Map.of("idpOne", oidc("idpOne")));
    final var repository =
        PerTenantOidcRegistry.buildFor("tenanta", security, List.of("idpOne"), stubBuilder());
    assertThat(repository.findByRegistrationId("idpOne")).isNotNull();
  }

  @Test
  void shouldRegisterMultipleAssignedProvidersAcrossDefaultAndNamedSlots() {
    final var security =
        sec(oidc("oidc"), Map.of("idpOne", oidc("idpOne"), "idpTwo", oidc("idpTwo")));
    final var repository =
        PerTenantOidcRegistry.buildFor(
            "tenanta", security, List.of("oidc", "idpOne"), stubBuilder());
    assertThat(repository.findByRegistrationId("oidc")).isNotNull();
    assertThat(repository.findByRegistrationId("idpOne")).isNotNull();
    assertThat(repository.findByRegistrationId("idpTwo")).isNull();
  }

  @Test
  void shouldRewriteRedirectUriToTenantPath() {
    final var security = sec(null, Map.of("idpOne", oidc("idpOne")));
    final var repository =
        PerTenantOidcRegistry.buildFor("tenanta", security, List.of("idpOne"), stubBuilder());
    assertThat(repository.findByRegistrationId("idpOne").getRedirectUri())
        .isEqualTo("{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}");
  }

  @Test
  void shouldFailWhenAssignedProviderIsMissingFromBothSlots() {
    final var security = sec(null, Map.of("idpOne", oidc("idpOne")));
    assertThatThrownBy(
            () ->
                PerTenantOidcRegistry.buildFor(
                    "tenanta", security, List.of("ghost"), stubBuilder()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ghost");
  }

  @Test
  void shouldFailWhenOidcIsAssignedButDefaultSlotIsNotConfigured() {
    final var security = sec(null, Map.of());
    assertThatThrownBy(
            () ->
                PerTenantOidcRegistry.buildFor("tenanta", security, List.of("oidc"), stubBuilder()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authentication.oidc.*");
  }

  @Test
  void shouldFailWhenAssignedIsEmpty() {
    final var security = sec(oidc("oidc"), Map.of());
    assertThatThrownBy(
            () -> PerTenantOidcRegistry.buildFor("tenanta", security, List.of(), stubBuilder()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("providers.assigned");
  }

  /**
   * Returns a stub {@link PerTenantOidcRegistry.ClientRegistrationBuilderFactory} that constructs a
   * {@link ClientRegistration.Builder} directly via {@link ClientRegistration#withRegistrationId},
   * bypassing OIDC discovery. We pre-populate the minimum fields a built {@link ClientRegistration}
   * requires.
   */
  private static PerTenantOidcRegistry.ClientRegistrationBuilderFactory stubBuilder() {
    return (registrationId, provider) ->
        ClientRegistration.withRegistrationId(registrationId)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationUri(provider.getIssuerUri() + "/protocol/openid-connect/auth")
            .tokenUri(provider.getIssuerUri() + "/protocol/openid-connect/token")
            .jwkSetUri(provider.getIssuerUri() + "/protocol/openid-connect/certs");
  }

  private static OidcConfiguration oidc(final String id) {
    final var c = new OidcConfiguration();
    c.setClientId("client-" + id);
    c.setClientSecret("secret-" + id);
    c.setIssuerUri("http://localhost:8080/realms/" + id);
    c.setRedirectUri("{baseUrl}/login/oauth2/code/{registrationId}");
    return c;
  }

  private static SecurityConfiguration sec(
      final OidcConfiguration defaultProvider,
      final Map<String, OidcConfiguration> namedProviders) {
    final var s = new SecurityConfiguration();
    if (defaultProvider != null) {
      s.getAuthentication().setOidc(defaultProvider);
    }
    // The external CSL OidcProvidersConfiguration is null by default; we instantiate it here so
    // tests can populate the named-providers map.
    final var providers =
        new io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration();
    providers.setOidc(namedProviders);
    s.getAuthentication().setProviders(providers);
    return s;
  }
}
