/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.service.UserServices;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class PhysicalTenantHandlerFactoryTest {

  private final Function<AuthenticationConfiguration, JwtDecoder> decoderFactory =
      authConfig -> mock(JwtDecoder.class);
  private final Function<AuthenticationConfiguration, OidcClaimsProvider> claimsProviderFactory =
      authConfig -> (jwtClaims, tokenValue) -> jwtClaims;
  private final Function<String, UserServices> userServicesForTenant =
      tenantId -> mock(UserServices.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

  @Test
  void shouldBuildOneHandlerPerTenantIncludingDefault() {
    // given
    final Map<String, EngineSecurityConfig> configs = new LinkedHashMap<>();
    configs.put("default", securityConfig(AuthenticationMethod.OIDC));
    configs.put("tenant-a", securityConfig(AuthenticationMethod.BASIC));

    // when
    final var registry =
        PhysicalTenantHandlerFactory.build(
            configs,
            true,
            decoderFactory,
            claimsProviderFactory,
            userServicesForTenant,
            passwordEncoder);

    // then
    assertThat(registry).containsOnlyKeys("default", "tenant-a");
    assertThat(registry.get("default")).isInstanceOf(AuthenticationHandler.Oidc.class);
    assertThat(registry.get("tenant-a")).isInstanceOf(AuthenticationHandler.BasicAuth.class);
  }

  @Test
  void shouldReturnEmptyRegistryWhenAuthDisabled() {
    // given
    final Map<String, EngineSecurityConfig> configs =
        Map.of("default", securityConfig(AuthenticationMethod.OIDC));

    // when
    final var registry =
        PhysicalTenantHandlerFactory.build(
            configs,
            false,
            decoderFactory,
            claimsProviderFactory,
            userServicesForTenant,
            passwordEncoder);

    // then
    assertThat(registry).isEmpty();
  }

  @Test
  void shouldFailFastWhenHandlerBuildFails() {
    // given
    final Map<String, EngineSecurityConfig> configs =
        Map.of("tenant-a", securityConfig(AuthenticationMethod.OIDC));
    final Function<AuthenticationConfiguration, JwtDecoder> failingDecoderFactory =
        authConfig -> {
          throw new IllegalStateException("boom");
        };

    // when / then
    assertThatThrownBy(
            () ->
                PhysicalTenantHandlerFactory.build(
                    configs,
                    true,
                    failingDecoderFactory,
                    claimsProviderFactory,
                    userServicesForTenant,
                    passwordEncoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant-a")
        .hasRootCauseMessage("boom");
  }

  @Test
  void shouldResolveTokenWithClaimSettingsOfIssuingProvider() throws Exception {
    // given a tenant whose default slot and named provider use different issuers and username
    // claims
    final var config = securityConfig(AuthenticationMethod.OIDC);
    final var defaultSlot = config.getAuthentication().getOidc();
    defaultSlot.setIssuerUri("https://keycloak.example.com");
    defaultSlot.setClientId("keycloak-client");
    defaultSlot.setUsernameClaim("preferred_username");
    final var okta = new OidcConfiguration();
    okta.setIssuerUri("https://okta.example.com");
    okta.setClientId("okta-client");
    okta.setUsernameClaim("email");
    final var providers = new OidcProvidersConfiguration();
    providers.setOidc(new LinkedHashMap<>(Map.of("okta", okta)));
    config.getAuthentication().setProviders(providers);

    final var decoder = mock(JwtDecoder.class);
    when(decoder.decode("okta-token"))
        .thenReturn(token("https://okta.example.com", "email", "alice@example.com"));
    when(decoder.decode("keycloak-token"))
        .thenReturn(token("https://keycloak.example.com", "preferred_username", "bob"));

    final var handler =
        PhysicalTenantHandlerFactory.build(
                Map.of("tenanta", config),
                true,
                authConfig -> decoder,
                claimsProviderFactory,
                userServicesForTenant,
                passwordEncoder)
            .get("tenanta");

    // when
    final var oktaResult = handler.authenticate("Bearer okta-token");
    final var keycloakResult = handler.authenticate("Bearer keycloak-token");

    // then each token is resolved with the username claim of the provider that issued it
    assertThat(oktaResult.isRight()).isTrue();
    assertThat(oktaResult.get().call(() -> AuthenticationHandler.USERNAME.get()))
        .isEqualTo("alice@example.com");
    assertThat(keycloakResult.isRight()).isTrue();
    assertThat(keycloakResult.get().call(() -> AuthenticationHandler.USERNAME.get()))
        .isEqualTo("bob");
  }

  @Test
  void shouldResolveGroupsAndPrincipalTypeWithSettingsOfIssuingProvider() throws Exception {
    // given the default slot prefers the username and reads groups from "roles", while the named
    // provider prefers the client id and reads groups from "groups"
    final var config = securityConfig(AuthenticationMethod.OIDC);
    final var defaultSlot = config.getAuthentication().getOidc();
    defaultSlot.setIssuerUri("https://keycloak.example.com");
    defaultSlot.setClientId("keycloak-client");
    defaultSlot.setUsernameClaim("preferred_username");
    defaultSlot.setClientIdClaim("azp");
    defaultSlot.setPreferUsernameClaim(true);
    defaultSlot.setGroupsClaim("roles");
    final var okta = new OidcConfiguration();
    okta.setIssuerUri("https://okta.example.com");
    okta.setClientId("okta-client");
    okta.setUsernameClaim("preferred_username");
    okta.setClientIdClaim("azp");
    okta.setPreferUsernameClaim(false);
    okta.setGroupsClaim("groups");
    final var providers = new OidcProvidersConfiguration();
    providers.setOidc(new LinkedHashMap<>(Map.of("okta", okta)));
    config.getAuthentication().setProviders(providers);

    final var decoder = mock(JwtDecoder.class);
    when(decoder.decode("okta-token")).thenReturn(tokenWithAllClaims("https://okta.example.com"));
    when(decoder.decode("keycloak-token"))
        .thenReturn(tokenWithAllClaims("https://keycloak.example.com"));

    final var handler =
        PhysicalTenantHandlerFactory.build(
                Map.of("tenanta", config),
                true,
                authConfig -> decoder,
                claimsProviderFactory,
                userServicesForTenant,
                passwordEncoder)
            .get("tenanta");

    // when
    final var oktaContext = handler.authenticate("Bearer okta-token").get();
    final var keycloakContext = handler.authenticate("Bearer keycloak-token").get();

    // then each token gets the principal type and groups of the provider that issued it
    assertThat(oktaContext.call(() -> AuthenticationHandler.CLIENT_ID.get()))
        .isEqualTo("some-client");
    assertThat(oktaContext.call(() -> AuthenticationHandler.USERNAME.get())).isNull();
    assertThat(oktaContext.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get()))
        .containsExactly("okta-team");
    assertThat(keycloakContext.call(() -> AuthenticationHandler.USERNAME.get())).isEqualTo("alice");
    assertThat(keycloakContext.call(() -> AuthenticationHandler.CLIENT_ID.get())).isNull();
    assertThat(keycloakContext.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get()))
        .containsExactly("keycloak-team");
  }

  @Test
  void shouldReadGroupsWithTenantGroupsClaimWhenProviderSetsNone() throws Exception {
    // given the tenant reads groups from "roles", and the named provider sets no groups claim
    final var handler = handlerWithGroupsClaims("roles", null);

    // when
    final var oktaContext = handler.authenticate("Bearer okta-token").get();

    // then the provider's tokens inherit the tenant's groups claim
    assertThat(oktaContext.call(() -> AuthenticationHandler.IS_CAMUNDA_GROUPS_ENABLED.get()))
        .isFalse();
    assertThat(oktaContext.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get()))
        .containsExactly("keycloak-team");
  }

  @Test
  void shouldNotReadTokenGroupsWhenTenantUsesCamundaGroups() throws Exception {
    // given the tenant uses Camunda groups (empty groups claim), and the named provider sets one
    final var handler = handlerWithGroupsClaims("", "groups");

    // when
    final var oktaContext = handler.authenticate("Bearer okta-token").get();

    // then the tenant's groups mode wins and no token groups are sent
    assertThat(oktaContext.call(() -> AuthenticationHandler.IS_CAMUNDA_GROUPS_ENABLED.get()))
        .isTrue();
    assertThat(oktaContext.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get())).isNull();
  }

  private AuthenticationHandler handlerWithGroupsClaims(
      final String tenantGroupsClaim, final String oktaGroupsClaim) {
    final var config = securityConfig(AuthenticationMethod.OIDC);
    final var defaultSlot = config.getAuthentication().getOidc();
    defaultSlot.setIssuerUri("https://keycloak.example.com");
    defaultSlot.setClientId("keycloak-client");
    defaultSlot.setGroupsClaim(tenantGroupsClaim);
    final var okta = new OidcConfiguration();
    okta.setIssuerUri("https://okta.example.com");
    okta.setClientId("okta-client");
    okta.setUsernameClaim("preferred_username");
    okta.setGroupsClaim(oktaGroupsClaim);
    final var providers = new OidcProvidersConfiguration();
    providers.setOidc(new LinkedHashMap<>(Map.of("okta", okta)));
    config.getAuthentication().setProviders(providers);

    final var decoder = mock(JwtDecoder.class);
    when(decoder.decode("okta-token")).thenReturn(tokenWithAllClaims("https://okta.example.com"));
    return PhysicalTenantHandlerFactory.build(
            Map.of("tenanta", config),
            true,
            authConfig -> decoder,
            claimsProviderFactory,
            userServicesForTenant,
            passwordEncoder)
        .get("tenanta");
  }

  /** A token carrying both principal claims and both groups claims, so only settings decide. */
  private static Jwt tokenWithAllClaims(final String issuer) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("iss", issuer)
        .claim("preferred_username", "alice")
        .claim("azp", "some-client")
        .claim("groups", List.of("okta-team"))
        .claim("roles", List.of("keycloak-team"))
        .build();
  }

  private static Jwt token(final String issuer, final String claim, final String value) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("iss", issuer)
        .claim(claim, value)
        .build();
  }

  private static EngineSecurityConfig securityConfig(final AuthenticationMethod method) {
    final var config = EngineSecurityConfigurations.defaultConfig();
    config.getAuthentication().setMethod(method);
    config.getAuthentication().setUnprotectedApi(false);
    return config;
  }
}
