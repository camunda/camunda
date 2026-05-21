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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Unit tests for {@link PerTenantClientRegistrations}. We use the package-private factory hook to
 * bypass OIDC discovery — calling Keycloak from a unit test is out of scope.
 *
 * <p>Note: {@code SecurityConfiguration.getAuthentication().getProviders()} on the external CSL
 * library does not expose an {@code assigned} field, so the registry accepts the {@code assigned}
 * list as a separate parameter (see the registry's javadoc for the rationale).
 */
class PerTenantClientRegistrationsTest {

  @Test
  void shouldResolveAssignedOidcToTheDefaultProviderSlot() {
    // given - authentication.oidc.* is the default provider (registration id "oidc")
    final var root = sec(oidc("oidc"), Map.of());
    // when
    final var repository =
        PerTenantClientRegistrations.buildFor(
            "tenanta", root, emptySec(), List.of("oidc"), stubBuilder());
    // then
    assertThat(repository.findByRegistrationId("oidc")).isNotNull();
  }

  @Test
  void shouldResolveAssignedNamedProviderUnderProvidersOidcMap() {
    final var root = sec(null, Map.of("idpOne", oidc("idpOne")));
    final var repository =
        PerTenantClientRegistrations.buildFor(
            "tenanta", root, emptySec(), List.of("idpOne"), stubBuilder());
    assertThat(repository.findByRegistrationId("idpOne")).isNotNull();
  }

  @Test
  void shouldRegisterMultipleAssignedProvidersAcrossDefaultAndNamedSlots() {
    final var root = sec(oidc("oidc"), Map.of("idpOne", oidc("idpOne"), "idpTwo", oidc("idpTwo")));
    final var repository =
        PerTenantClientRegistrations.buildFor(
            "tenanta", root, emptySec(), List.of("oidc", "idpOne"), stubBuilder());
    assertThat(repository.findByRegistrationId("oidc")).isNotNull();
    assertThat(repository.findByRegistrationId("idpOne")).isNotNull();
    assertThat(repository.findByRegistrationId("idpTwo")).isNull();
  }

  @Test
  void shouldRewriteRedirectUriToTenantPath() {
    final var root = sec(null, Map.of("idpOne", oidc("idpOne")));
    final var repository =
        PerTenantClientRegistrations.buildFor(
            "tenanta", root, emptySec(), List.of("idpOne"), stubBuilder());
    assertThat(repository.findByRegistrationId("idpOne").getRedirectUri())
        .isEqualTo("{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}");
  }

  @Test
  void shouldFailWhenAssignedProviderIsMissingFromBothSlots() {
    final var root = sec(null, Map.of("idpOne", oidc("idpOne")));
    assertThatThrownBy(
            () ->
                PerTenantClientRegistrations.buildFor(
                    "tenanta", root, emptySec(), List.of("ghost"), stubBuilder()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ghost");
  }

  @Test
  void shouldFailWhenOidcIsAssignedButDefaultSlotIsNotConfigured() {
    final var root = sec(null, Map.of());
    assertThatThrownBy(
            () ->
                PerTenantClientRegistrations.buildFor(
                    "tenanta", root, emptySec(), List.of("oidc"), stubBuilder()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authentication.oidc.*");
  }

  @Test
  void shouldFailWhenAssignedIsEmpty() {
    final var root = sec(oidc("oidc"), Map.of());
    assertThatThrownBy(
            () ->
                PerTenantClientRegistrations.buildFor(
                    "tenanta", root, emptySec(), List.of(), stubBuilder()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("providers.assigned");
  }

  @Test
  void shouldOverrideRootAudiencesWithPtSideOverlay() {
    // given - root declares idpOne with audiences [root-aud]; the PT overlay overrides audiences
    // (and only audiences) to [pt-aud]. clientId/secret/issuer must inherit from root.
    final var rootProvider = oidc("idpOne");
    rootProvider.setAudiences(Set.of("root-aud"));
    final var root = sec(null, Map.of("idpOne", rootProvider));

    final var ptOverlay = new OidcConfiguration();
    ptOverlay.setAudiences(Set.of("pt-aud"));
    final var ptSec = sec(null, Map.of("idpOne", ptOverlay));

    // when
    final var merged = PerTenantClientRegistrations.resolveMergedProvider("idpOne", root, ptSec);

    // then - audiences came from PT overlay; the rest from root
    assertThat(merged).isNotNull();
    assertThat(merged.getAudiences()).containsExactly("pt-aud");
    assertThat(merged.getClientId()).isEqualTo("client-idpOne");
    assertThat(merged.getClientSecret()).isEqualTo("secret-idpOne");
    assertThat(merged.getIssuerUri()).isEqualTo("http://localhost:8080/realms/idpOne");

    // and - the root config is NOT mutated by the merge
    assertThat(rootProvider.getAudiences()).containsExactly("root-aud");
  }

  /**
   * Returns a stub {@link PerTenantClientRegistrations.ClientRegistrationBuilderFactory} that
   * constructs a {@link ClientRegistration.Builder} directly via {@link
   * ClientRegistration#withRegistrationId}, bypassing OIDC discovery. We pre-populate the minimum
   * fields a built {@link ClientRegistration} requires.
   */
  private static PerTenantClientRegistrations.ClientRegistrationBuilderFactory stubBuilder() {
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

  private static SecurityConfiguration emptySec() {
    return sec(null, Map.of());
  }
}
