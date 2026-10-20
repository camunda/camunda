/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.grpc.Context;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * gRPC regression tests for the scenarios of camunda/camunda#65633: a named provider uses its own
 * username, client-id and prefer-username claims, and for each claim that it does not set, the
 * claim of the tenant's default slot. 8.9 used the default slot for every token.
 */
final class PhysicalTenantHandlerFactoryClaimInheritanceTest {

  private static final String TENANT = "tenanta";
  private static final String ISSUER = "https://idp.example.com";

  private final Function<AuthenticationConfiguration, OidcClaimsProvider> claimsProviderFactory =
      authConfig -> (jwtClaims, tokenValue) -> jwtClaims;
  private final Function<String, UserServices> userServicesForTenant =
      tenantId -> mock(UserServices.class);
  private final JwtDecoder decoder = mock(JwtDecoder.class);

  @Test
  void shouldUseTheGlobalClaimsForAProviderWithoutClaims() throws Exception {
    // given scenario 1: claims only in the default slot, the named provider sets none
    final var config = config(slotWithGlobalClaims(), new OidcConfiguration());
    token("user", Map.of("sub", "4f2a", "preferred_username", "alice", "azp", "camunda-web"));
    token("m2m", Map.of("sub", "9c1b", "azp", "my-app"));

    // when / then
    assertUser(authenticate(config, "user"), "alice");
    assertClient(authenticate(config, "m2m"), "my-app");
  }

  @Test
  void shouldUseTheClaimsThatTheProviderSets() throws Exception {
    // given scenario 2: the provider sets its own username claim
    final var provider = new OidcConfiguration();
    provider.setUsernameClaim("upn");
    final var config = config(slotWithGlobalClaims(), provider);
    token("user", Map.of("sub", "1a2b", "upn", "bob@example.com"));

    // when / then
    assertUser(authenticate(config, "user"), "bob@example.com");
  }

  @Test
  void shouldResolveEachClaimOnItsOwn() throws Exception {
    // given scenario 3: the provider sets only its username claim; client-id-claim and
    // prefer-username-claim come from the default slot
    final var provider = new OidcConfiguration();
    provider.setUsernameClaim("upn");
    final var config = config(slotWithGlobalClaims(), provider);
    token("user", Map.of("sub", "1a2b", "upn", "bob@example.com", "azp", "camunda-web"));
    token("m2m", Map.of("sub", "9c1b", "azp", "my-app"));

    // when / then the user token is a user, because prefer-username-claim is inherited
    assertUser(authenticate(config, "user"), "bob@example.com");
    assertClient(authenticate(config, "m2m"), "my-app");
  }

  @Test
  void shouldUseAProviderClaimAlsoWhenItIsTheDefaultValue() throws Exception {
    // given scenario 4: the provider sets username-claim to sub on purpose
    final var provider = new OidcConfiguration();
    provider.setUsernameClaim(OidcConfiguration.DEFAULT_USERNAME_CLAIM);
    final var slot = new OidcConfiguration();
    slot.setUsernameClaim("preferred_username");
    final var config = config(slot, provider);
    token("user", Map.of("sub", "7d3e", "preferred_username", "bob"));

    // when / then
    assertUser(authenticate(config, "user"), "7d3e");
  }

  @Test
  void shouldUseTheTenantClaimForAProviderWithoutClaims() throws Exception {
    // given scenario 5: the tenant overrides the username claim of its default slot, and the
    // assigned provider sets none
    final var slot = new OidcConfiguration();
    slot.setUsernameClaim("upn");
    final var config = config(slot, new OidcConfiguration());
    token("user", Map.of("sub", "00u1", "preferred_username", "carol", "upn", "carol@example.com"));

    // when / then
    assertUser(authenticate(config, "user"), "carol@example.com");
  }

  @Test
  void shouldKeepTheClaimsOfASingleProviderInTheDefaultSlot() throws Exception {
    // given scenario 6: one IdP in the default slot, no named provider
    final var slot = slotWithGlobalClaims();
    slot.setIssuerUri(ISSUER);
    slot.setClientId("camunda");
    final var config = securityConfig();
    config.getAuthentication().setOidc(slot);
    token("user", Map.of("sub", "4f2a", "preferred_username", "alice", "azp", "camunda-web"));
    token("m2m", Map.of("sub", "9c1b", "azp", "my-app"));

    // when / then
    assertUser(authenticate(config, "user"), "alice");
    assertClient(authenticate(config, "m2m"), "my-app");
  }

  /** The global claims as the multi-IdP docs describe them. */
  private static OidcConfiguration slotWithGlobalClaims() {
    final var slot = new OidcConfiguration();
    slot.setUsernameClaim("preferred_username");
    slot.setClientIdClaim("azp");
    slot.setPreferUsernameClaim(true);
    return slot;
  }

  /** A tenant with {@code slot} as its default slot, and {@code provider} as named provider. */
  private static EngineSecurityConfig config(
      final OidcConfiguration slot, final OidcConfiguration provider) {
    provider.setIssuerUri(ISSUER);
    provider.setClientId("named-client");
    final var config = securityConfig();
    config.getAuthentication().setOidc(slot);
    final var providers = new OidcProvidersConfiguration();
    providers.setOidc(new LinkedHashMap<>(Map.of("named", provider)));
    config.getAuthentication().setProviders(providers);
    return config;
  }

  private static EngineSecurityConfig securityConfig() {
    final var config = EngineSecurityConfigurations.defaultConfig();
    config.getAuthentication().setMethod(AuthenticationMethod.OIDC);
    config.getAuthentication().setUnprotectedApi(false);
    return config;
  }

  private void token(final String value, final Map<String, Object> claims) {
    final var jwt = Jwt.withTokenValue(value).header("alg", "RS256").claim("iss", ISSUER);
    claims.forEach(jwt::claim);
    when(decoder.decode(value)).thenReturn(jwt.build());
  }

  private Context authenticate(final EngineSecurityConfig config, final String token) {
    final var handler =
        PhysicalTenantHandlerFactory.build(
                Map.of(TENANT, config),
                true,
                authConfig -> decoder,
                claimsProviderFactory,
                userServicesForTenant,
                mock(PasswordEncoder.class))
            .get(TENANT);
    final var result = handler.authenticate("Bearer " + token);
    assertThat(result.isRight()).as("token %s is accepted", token).isTrue();
    return result.get();
  }

  private static void assertUser(final Context context, final String username) throws Exception {
    assertThat(context.call(() -> AuthenticationHandler.USERNAME.get())).isEqualTo(username);
    assertThat(context.call(() -> AuthenticationHandler.CLIENT_ID.get())).isNull();
  }

  private static void assertClient(final Context context, final String clientId) throws Exception {
    assertThat(context.call(() -> AuthenticationHandler.CLIENT_ID.get())).isEqualTo(clientId);
    assertThat(context.call(() -> AuthenticationHandler.USERNAME.get())).isNull();
  }
}
