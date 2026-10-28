/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetrics;
import io.camunda.zeebe.gateway.interceptors.impl.PhysicalTenantAuthentication;
import io.camunda.zeebe.gateway.interceptors.impl.PhysicalTenantHandlerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The gateway module of every physical tenant this gRPC gateway serves: the security config, the
 * authorization converter and, on a protected API, the authentication handler of each tenant.
 * Request handling looks a tenant up per call, so a tenant put, marked unavailable or removed at
 * runtime takes effect for the next call.
 */
@NullMarked
public final class GatewayPhysicalTenants {

  private final Map<String, Module> modules = new ConcurrentHashMap<>();
  private final boolean authEnabled;
  private final Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory;
  private final Function<AuthenticationConfiguration, OidcClaimsProvider> oidcClaimsProviderFactory;
  private final Function<String, UserServices> userServicesForTenant;
  private final @Nullable PasswordEncoder passwordEncoder;
  private final MeterRegistry meterRegistry;

  /**
   * @param authEnabled whether the API is protected; uniform across tenants, so it is decided once
   *     from the {@code default} tenant
   */
  public GatewayPhysicalTenants(
      final Map<String, EngineSecurityConfig> securityConfigurationsByPhysicalTenant,
      final boolean authEnabled,
      final Function<AuthenticationConfiguration, JwtDecoder> jwtDecoderFactory,
      final Function<AuthenticationConfiguration, OidcClaimsProvider> oidcClaimsProviderFactory,
      final Function<String, UserServices> userServicesForTenant,
      final @Nullable PasswordEncoder passwordEncoder,
      final MeterRegistry meterRegistry) {
    this.authEnabled = authEnabled;
    this.jwtDecoderFactory = jwtDecoderFactory;
    this.oidcClaimsProviderFactory = oidcClaimsProviderFactory;
    this.userServicesForTenant = userServicesForTenant;
    this.passwordEncoder = passwordEncoder;
    this.meterRegistry = meterRegistry;
    securityConfigurationsByPhysicalTenant.forEach(this::put);
  }

  /** Adds the gateway module of a tenant, or replaces it with one built from a new config. */
  public void put(final String physicalTenantId, final EngineSecurityConfig securityConfig) {
    modules.put(physicalTenantId, build(physicalTenantId, securityConfig));
  }

  /**
   * Keeps the tenant known but rejects its calls with {@code UNAVAILABLE}, e.g. while its broker
   * module restarts, until the next {@link #put}.
   */
  public void markUnavailable(final String physicalTenantId, final String reason) {
    modules.computeIfPresent(
        physicalTenantId,
        (id, module) ->
            new Module(
                module.securityConfig(),
                module.authorizationConverter(),
                new PhysicalTenantAuthentication(
                    module.authentication().handler(), module.authentication().metrics(), reason)));
  }

  public void remove(final String physicalTenantId) {
    modules.remove(physicalTenantId);
  }

  public Set<String> known() {
    return Set.copyOf(modules.keySet());
  }

  public boolean isAuthEnabled() {
    return authEnabled;
  }

  public @Nullable PhysicalTenantAuthentication authentication(final String physicalTenantId) {
    final var module = modules.get(physicalTenantId);
    return module == null ? null : module.authentication();
  }

  public @Nullable BrokerRequestAuthorizationConverter authorizationConverter(
      final String physicalTenantId) {
    final var module = modules.get(physicalTenantId);
    return module == null ? null : module.authorizationConverter();
  }

  public @Nullable EngineSecurityConfig securityConfig(final String physicalTenantId) {
    final var module = modules.get(physicalTenantId);
    return module == null ? null : module.securityConfig();
  }

  private Module build(final String physicalTenantId, final EngineSecurityConfig securityConfig) {
    final var handler =
        PhysicalTenantHandlerFactory.build(
                Map.of(physicalTenantId, securityConfig),
                authEnabled,
                jwtDecoderFactory,
                oidcClaimsProviderFactory,
                userServicesForTenant,
                passwordEncoder)
            .get(physicalTenantId);
    final var metrics =
        authEnabled
            ? new AuthenticationMetrics(
                meterRegistry, securityConfig.getAuthentication().getMethod())
            : null;
    return new Module(
        securityConfig,
        new BrokerRequestAuthorizationConverter(securityConfig),
        new PhysicalTenantAuthentication(handler, metrics, null));
  }

  private record Module(
      EngineSecurityConfig securityConfig,
      BrokerRequestAuthorizationConverter authorizationConverter,
      PhysicalTenantAuthentication authentication) {

    private Module {
      Objects.requireNonNull(securityConfig);
      Objects.requireNonNull(authorizationConverter);
      Objects.requireNonNull(authentication);
    }
  }
}
