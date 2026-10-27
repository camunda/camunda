/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PhysicalTenantGroupsClaimsTest {

  private static final String ISSUER = "https://idp.example.com/realms/camunda";

  @AfterEach
  void clearRequestScope() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldUseDefaultSlotForIssuerItSharesWithProvider() {
    // given
    final var config = config(defaultSlot(ISSUER, "camunda", "groups"));
    providers(config).put("okta", provider(ISSUER, "roles"));

    // when
    final var groups = inDefaultTenant(config, token(ISSUER));

    // then
    assertThat(groups).containsExactly("group");
  }

  @Test
  void shouldUseFirstProviderForIssuerSharedByProviders() {
    // given
    final var config = config(defaultSlot(null, null, "groups"));
    providers(config).put("okta", provider(ISSUER, "roles"));
    providers(config).put("other", provider(ISSUER, "teams"));

    // when
    final var groups = inDefaultTenant(config, token(ISSUER));

    // then
    assertThat(groups).containsExactly("role");
  }

  @Test
  void shouldUseProviderForIssuerOfDefaultSlotWithoutClientId() {
    // given
    // without a client id, the default slot is not a provider, so it does not claim its issuer
    final var config = config(defaultSlot(ISSUER, null, "groups"));
    providers(config).put("okta", provider(ISSUER, "roles"));

    // when
    final var groups = inDefaultTenant(config, token(ISSUER));

    // then
    assertThat(groups).containsExactly("role");
  }

  @Test
  void shouldMatchTokenIssuerWithoutTrailingSlashToIssuerUriWithIt() {
    // given
    final var config = config(defaultSlot(null, null, "groups"));
    providers(config).put("okta", provider(ISSUER + "/", "roles"));

    // when
    final var groups = inDefaultTenant(config, token(ISSUER));

    // then
    assertThat(groups).containsExactly("role");
  }

  @Test
  void shouldMatchTokenIssuerWithTrailingSlashToIssuerUriWithoutIt() {
    // given
    final var config = config(defaultSlot(null, null, "groups"));
    providers(config).put("okta", provider(ISSUER, "roles"));

    // when
    final var groups = inDefaultTenant(config, token(ISSUER + "/"));

    // then
    assertThat(groups).containsExactly("role");
  }

  @Test
  void shouldUseFirstProviderForIssuersThatDifferByTrailingSlash() {
    // given
    final var config = config(defaultSlot(null, null, "groups"));
    providers(config).put("okta", provider(ISSUER + "/", "roles"));
    providers(config).put("other", provider(ISSUER, "teams"));

    // when
    final var groups = inDefaultTenant(config, token(ISSUER));

    // then
    assertThat(groups).containsExactly("role");
  }

  @Test
  void shouldUseDefaultTenantSettingsForUnconfiguredPhysicalTenant() {
    // given
    final var config = config(defaultSlot(null, null, "groups"));

    // when
    final var groups = inPhysicalTenant("unconfigured", config, token(ISSUER));

    // then
    assertThat(groups).containsExactly("group");
  }

  @Test
  void shouldFailWithoutPhysicalTenant() {
    // given
    final var groupsClaims =
        new PhysicalTenantGroupsClaims(
            properties(config(defaultSlot(null, null, "groups"))), new MockEnvironment());

    // when / then
    // falling back to another tenant could read token groups for a tenant that uses Camunda
    // groups
    assertThatThrownBy(groupsClaims::current).isInstanceOf(IllegalStateException.class);
  }

  private static List<String> inDefaultTenant(
      final AuthenticationConfiguration config, final Map<String, Object> token) {
    return inPhysicalTenant("default", config, token);
  }

  private static List<String> inPhysicalTenant(
      final String physicalTenantId,
      final AuthenticationConfiguration config,
      final Map<String, Object> token) {
    final var groupsClaims =
        new PhysicalTenantGroupsClaims(properties(config), new MockEnvironment());
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    return groupsClaims.current().extract(token);
  }

  private static Map<String, Object> token(final String issuer) {
    return Map.of(
        "iss",
        issuer,
        "groups",
        List.of("group"),
        "roles",
        List.of("role"),
        "teams",
        List.of("team"));
  }

  private static AuthenticationConfiguration config(final OidcConfiguration defaultSlot) {
    final var config = new AuthenticationConfiguration();
    config.setMethod(AuthenticationMethod.OIDC);
    config.setOidc(defaultSlot);
    final var providers = new OidcProvidersConfiguration();
    // insertion-ordered, so the test controls which provider comes first
    providers.setOidc(new LinkedHashMap<>());
    config.setProviders(providers);
    return config;
  }

  private static Map<String, OidcConfiguration> providers(
      final AuthenticationConfiguration config) {
    return config.getProviders().getOidc();
  }

  private static OidcConfiguration defaultSlot(
      final String issuerUri, final String clientId, final String groupsClaim) {
    final var slot = new OidcConfiguration();
    slot.setIssuerUri(issuerUri);
    slot.setClientId(clientId);
    slot.setGroupsClaim(groupsClaim);
    return slot;
  }

  private static OidcConfiguration provider(final String issuerUri, final String groupsClaim) {
    return defaultSlot(issuerUri, "client", groupsClaim);
  }

  private static CamundaSecurityLibraryProperties properties(
      final AuthenticationConfiguration config) {
    final var properties = new CamundaSecurityLibraryProperties();
    properties.setAuthentication(config);
    return properties;
  }
}
