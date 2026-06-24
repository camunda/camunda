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
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.service.UserServices;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Execution(ExecutionMode.CONCURRENT)
final class PhysicalTenantHandlerRegistryTest {

  private static final String DEFAULT_TENANT = "default";
  private static final String TENANT_A = "tenanta";

  private JwtDecoder defaultDecoder;
  private JwtDecoder tenantADecoder;
  private OidcClaimsProvider defaultClaimsProvider;
  private OidcClaimsProvider tenantAClaimsProvider;
  private UserServices defaultUserServices;
  private UserServices tenantAUserServices;
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    defaultDecoder = mock(JwtDecoder.class);
    tenantADecoder = mock(JwtDecoder.class);
    defaultClaimsProvider = mock(OidcClaimsProvider.class);
    tenantAClaimsProvider = mock(OidcClaimsProvider.class);
    defaultUserServices = mock(UserServices.class);
    tenantAUserServices = mock(UserServices.class);
    passwordEncoder = mock(PasswordEncoder.class);
  }

  @Test
  void shouldReturnEmptyMapWhenAuthDisabled() {
    // given
    final var configs = oidcConfigs(DEFAULT_TENANT, TENANT_A);

    // when
    final var registry =
        PhysicalTenantHandlerRegistry.build(
            configs,
            false,
            cfg -> defaultDecoder,
            cfg -> defaultClaimsProvider,
            id -> null,
            passwordEncoder);

    // then
    assertThat(registry).isEmpty();
  }

  @Test
  void shouldBuildOidcHandlerPerTenant() {
    // given
    final var configs = new LinkedHashMap<String, AuthenticationConfiguration>();
    final var defaultConfig = oidcConfig();
    final var tenantAConfig = oidcConfig();
    configs.put(DEFAULT_TENANT, defaultConfig);
    configs.put(TENANT_A, tenantAConfig);

    // when
    final var registry =
        PhysicalTenantHandlerRegistry.build(
            configs,
            true,
            cfg -> cfg == defaultConfig ? defaultDecoder : tenantADecoder,
            cfg -> cfg == defaultConfig ? defaultClaimsProvider : tenantAClaimsProvider,
            id -> null,
            passwordEncoder);

    // then
    assertThat(registry).containsOnlyKeys(DEFAULT_TENANT, TENANT_A);
    assertThat(registry.get(DEFAULT_TENANT)).isInstanceOf(AuthenticationHandler.Oidc.class);
    assertThat(registry.get(TENANT_A)).isInstanceOf(AuthenticationHandler.Oidc.class);
  }

  @Test
  void shouldBuildBasicHandlerPerTenant() {
    // given
    final var configs = new LinkedHashMap<String, AuthenticationConfiguration>();
    configs.put(DEFAULT_TENANT, basicConfig());
    configs.put(TENANT_A, basicConfig());

    // when
    final var registry =
        PhysicalTenantHandlerRegistry.build(
            configs,
            true,
            cfg -> null,
            cfg -> null,
            id -> id.equals(DEFAULT_TENANT) ? defaultUserServices : tenantAUserServices,
            passwordEncoder);

    // then
    assertThat(registry).containsOnlyKeys(DEFAULT_TENANT, TENANT_A);
    assertThat(registry.get(DEFAULT_TENANT)).isInstanceOf(AuthenticationHandler.BasicAuth.class);
    assertThat(registry.get(TENANT_A)).isInstanceOf(AuthenticationHandler.BasicAuth.class);
  }

  @Test
  void shouldPassPerTenantDecoderToOidcHandler() {
    // given
    final var configs = new LinkedHashMap<String, AuthenticationConfiguration>();
    final var defaultConfig = oidcConfig();
    configs.put(DEFAULT_TENANT, defaultConfig);

    // when
    final var registry =
        PhysicalTenantHandlerRegistry.build(
            configs,
            true,
            cfg -> defaultDecoder,
            cfg -> defaultClaimsProvider,
            id -> null,
            passwordEncoder);

    // then — the Oidc handler must carry the decoder supplied for this PT's config
    final var handler = (AuthenticationHandler.Oidc) registry.get(DEFAULT_TENANT);
    assertThat(handler).isNotNull();
  }

  @Test
  void shouldPassPerTenantOidcConfigurationToOidcHandler() {
    // given
    final var configs = new LinkedHashMap<String, AuthenticationConfiguration>();
    final var defaultConfig = oidcConfig();
    configs.put(DEFAULT_TENANT, defaultConfig);

    // when
    final var registry =
        PhysicalTenantHandlerRegistry.build(
            configs,
            true,
            cfg -> defaultDecoder,
            cfg -> defaultClaimsProvider,
            id -> null,
            passwordEncoder);

    // then
    assertThat(registry.get(DEFAULT_TENANT)).isInstanceOf(AuthenticationHandler.Oidc.class);
  }

  @Test
  void shouldPassPerTenantUserServicesToBasicHandler() {
    // given
    final var configs = new LinkedHashMap<String, AuthenticationConfiguration>();
    configs.put(TENANT_A, basicConfig());

    // when
    final var registry =
        PhysicalTenantHandlerRegistry.build(
            configs, true, cfg -> null, cfg -> null, id -> tenantAUserServices, passwordEncoder);

    // then
    assertThat(registry.get(TENANT_A)).isInstanceOf(AuthenticationHandler.BasicAuth.class);
  }

  @Test
  void shouldIncludeDefaultTenantInRegistry() {
    // given — configs that include "default" explicitly
    final var configs = new LinkedHashMap<String, AuthenticationConfiguration>();
    configs.put(DEFAULT_TENANT, basicConfig());
    configs.put(TENANT_A, basicConfig());

    // when
    final var registry =
        PhysicalTenantHandlerRegistry.build(
            configs,
            true,
            cfg -> null,
            cfg -> null,
            id -> mock(UserServices.class),
            passwordEncoder);

    // then
    assertThat(registry).containsKey(DEFAULT_TENANT);
  }

  @Test
  void shouldThrowIllegalStateWhenDecoderFactoryFails() {
    // given
    final var configs = new LinkedHashMap<String, AuthenticationConfiguration>();
    configs.put(TENANT_A, oidcConfig());
    final Function<AuthenticationConfiguration, JwtDecoder> failingDecoderFactory =
        cfg -> {
          throw new RuntimeException("decoder build failed");
        };

    // when / then
    assertThatThrownBy(
            () ->
                PhysicalTenantHandlerRegistry.build(
                    configs,
                    true,
                    failingDecoderFactory,
                    cfg -> defaultClaimsProvider,
                    id -> null,
                    passwordEncoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TENANT_A);
  }

  @Test
  void shouldThrowIllegalStateNamingTenantWhenDefaultFails() {
    // given
    final var configs = new LinkedHashMap<String, AuthenticationConfiguration>();
    configs.put(DEFAULT_TENANT, oidcConfig());
    final Function<AuthenticationConfiguration, JwtDecoder> failingDecoderFactory =
        cfg -> {
          throw new RuntimeException("default decoder failed");
        };

    // when / then
    assertThatThrownBy(
            () ->
                PhysicalTenantHandlerRegistry.build(
                    configs,
                    true,
                    failingDecoderFactory,
                    cfg -> defaultClaimsProvider,
                    id -> null,
                    passwordEncoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(DEFAULT_TENANT);
  }

  // helpers

  private static Map<String, AuthenticationConfiguration> oidcConfigs(final String... tenantIds) {
    final var configs = new LinkedHashMap<String, AuthenticationConfiguration>();
    for (final String id : tenantIds) {
      configs.put(id, oidcConfig());
    }
    return configs;
  }

  private static AuthenticationConfiguration oidcConfig() {
    final var config = new AuthenticationConfiguration();
    config.setMethod(AuthenticationMethod.OIDC);
    config.setOidc(new OidcConfiguration());
    return config;
  }

  private static AuthenticationConfiguration basicConfig() {
    final var config = new AuthenticationConfiguration();
    config.setMethod(AuthenticationMethod.BASIC);
    return config;
  }
}
