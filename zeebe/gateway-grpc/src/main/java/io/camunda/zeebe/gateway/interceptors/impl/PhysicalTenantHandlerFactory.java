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
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.service.UserServices;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Gateway-side registry of per-physical-tenant {@link AuthenticationHandler}s.
 *
 * <p>Built from the per-PT {@link EngineSecurityConfig} map (one entry per physical tenant,
 * including {@code default}). The authentication method is read off each tenant's {@link
 * AuthenticationConfiguration}.
 *
 * <p>Fail-fast: any per-tenant handler build failure (including {@code default}) throws, failing
 * gateway startup. There is no log-and-skip.
 *
 * <p>The decoder / claims-provider / user-services factories are injected so the caller (the
 * standalone {@code GatewayModuleConfiguration} or the embedded broker path) supplies the per-PT
 * lookups it has access to. They are function-shaped so this module does not need to depend on the
 * concrete CSL factory types.
 */
public final class PhysicalTenantHandlerFactory {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantHandlerFactory.class);

  private PhysicalTenantHandlerFactory() {}

  /**
   * @param securityConfigsByTenantId per-PT security config (one entry per physical tenant)
   * @param authEnabled whether the API is protected (derived from the {@code default} tenant)
   * @param decoderFactory auth config -&gt; {@link JwtDecoder} (CSL {@code
   *     ScopedJwtDecoderFactory#buildIssuerAwareDecoder})
   * @param claimsProviderFactory auth config -&gt; {@link OidcClaimsProvider} (CSL {@code
   *     ScopedOidcClaimsProviderFactory#buildClaimsProvider})
   * @param userServicesForTenant PT id -&gt; {@link UserServices} ({@code
   *     ServiceRegistry#userServices})
   * @param passwordEncoder shared password encoder for BASIC
   */
  public static Map<String, AuthenticationHandler> build(
      final Map<String, EngineSecurityConfig> securityConfigsByTenantId,
      final boolean authEnabled,
      final Function<AuthenticationConfiguration, JwtDecoder> decoderFactory,
      final Function<AuthenticationConfiguration, OidcClaimsProvider> claimsProviderFactory,
      final Function<String, UserServices> userServicesForTenant,
      final PasswordEncoder passwordEncoder) {

    final Map<String, AuthenticationHandler> registry = new LinkedHashMap<>();

    if (!authEnabled) {
      // Unprotected: no handlers needed. The interceptor validates PT ids against the known-ids
      // set, not against this map.
      return registry;
    }

    securityConfigsByTenantId.forEach(
        (tenantId, securityConfig) -> {
          final var authConfig = securityConfig.getAuthentication();
          final var method = authConfig.getMethod();
          final AuthenticationHandler handler;
          try {
            handler =
                switch (method) {
                  case OIDC ->
                      new AuthenticationHandler.Oidc(
                          decoderFactory.apply(authConfig),
                          claimsProviderFactory.apply(authConfig),
                          authConfig.getOidc());
                  case BASIC -> {
                    LOG.atWarn()
                        .log(
                            "Basic Authentication only supports a very small number of API requests per second for physical tenant '{}'. "
                                + "Please refer to the documentation "
                                + "https://docs.camunda.io/docs/next/self-managed/operational-guides/troubleshooting/#basic-authentication-performance",
                            tenantId);
                    yield new AuthenticationHandler.BasicAuth(
                        userServicesForTenant.apply(tenantId), passwordEncoder);
                  }
                };
          } catch (final Exception e) {
            throw new IllegalStateException(
                "Failed to build AuthenticationHandler for physical tenant '" + tenantId + "'", e);
          }
          registry.put(tenantId, handler);
        });

    return registry;
  }
}
