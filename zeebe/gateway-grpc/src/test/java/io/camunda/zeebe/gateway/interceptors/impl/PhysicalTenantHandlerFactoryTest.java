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

import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.service.UserServices;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
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

  private static EngineSecurityConfig securityConfig(final AuthenticationMethod method) {
    final var config = EngineSecurityConfigurations.defaultConfig();
    config.getAuthentication().setMethod(method);
    config.getAuthentication().setUnprotectedApi(false);
    return config;
  }
}
