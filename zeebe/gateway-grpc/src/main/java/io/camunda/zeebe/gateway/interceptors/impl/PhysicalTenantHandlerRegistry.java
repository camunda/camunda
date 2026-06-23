/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.service.UserServices;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Gateway-side registry of per-physical-tenant {@link AuthenticationHandler}s (#55753).
 *
 * <p>Built from the per-PT {@link AuthenticationConfiguration} map produced by {@code
 * PhysicalTenantAuthConfigurations.forAllPhysicalTenants(Environment)} (#55751). Lives in the
 * gateway module because {@link AuthenticationHandler} is a gateway-grpc type — the authentication
 * module must not depend on gateway-grpc.
 *
 * <p>Fail-fast: any PT handler build failure (including {@code default}) throws, failing startup.
 * No log-and-skip.
 *
 * <p>The OIDC and BASIC factory functions are injected so the caller (the standalone {@code
 * GatewayModuleConfiguration} or the embedded broker path) supplies the per-PT decoder /
 * claims-provider / user-services lookups it has access to.
 */
public final class PhysicalTenantHandlerRegistry {

  private PhysicalTenantHandlerRegistry() {}

  /**
   * @param authConfigsByTenantId per-PT merged auth config (from #55751)
   * @param authEnabled whether the (cluster-global) API is protected
   * @param decoderFactory PT id + config -> JwtDecoder (CSL ScopedJwtDecoderFactory in real impl)
   * @param claimsProviderFactory PT id + config -> OidcClaimsProvider (NEW CSL factory in real impl
   *     — see #55752; stubbed in the spike)
   * @param userServicesForTenant PT id -> UserServices (ServiceRegistry.userServices(ptId))
   * @param passwordEncoder shared password encoder for BASIC
   */
  public static Map<String, AuthenticationHandler> build(
      final Map<String, AuthenticationConfiguration> authConfigsByTenantId,
      final boolean authEnabled,
      final Function<AuthenticationConfiguration, JwtDecoder> decoderFactory,
      final Function<AuthenticationConfiguration, OidcClaimsProvider> claimsProviderFactory,
      final Function<String, UserServices> userServicesForTenant,
      final PasswordEncoder passwordEncoder) {

    final Map<String, AuthenticationHandler> registry = new LinkedHashMap<>();

    if (!authEnabled) {
      // Unprotected: no handlers needed. The interceptor validates PT ids against the known-ids
      // set (the authConfigsByTenantId key set), not against this map.
      return registry;
    }

    authConfigsByTenantId.forEach(
        (tenantId, authConfig) -> {
          final AuthenticationHandler handler;
          // Auth method is cluster-global; we read it off each PT's merged config (it is
          // re-asserted from root per #55751, so all PTs carry the same method).
          final var method = authConfig.getMethod();
          try {
            handler =
                switch (method) {
                  case OIDC ->
                      new AuthenticationHandler.Oidc(
                          decoderFactory.apply(authConfig),
                          claimsProviderFactory.apply(authConfig),
                          authConfig.getOidc());
                  case BASIC ->
                      new AuthenticationHandler.BasicAuth(
                          userServicesForTenant.apply(tenantId), passwordEncoder);
                };
          } catch (final Exception e) {
            // Fail-fast: a single PT (incl. default) failing fails startup.
            throw new IllegalStateException(
                "Failed to build AuthenticationHandler for physical tenant '" + tenantId + "'", e);
          }
          registry.put(tenantId, handler);
        });

    return registry;
  }
}
