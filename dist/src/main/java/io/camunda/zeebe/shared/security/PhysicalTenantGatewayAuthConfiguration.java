/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.authentication.pt.PhysicalTenantAuthConfigurations;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.ScopedOidcClaimsProviderFactory;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler;
import io.camunda.zeebe.gateway.interceptors.impl.PhysicalTenantHandlerRegistry;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Exposes a single shared {@link Map}&lt;String, AuthenticationHandler&gt; bean that both the
 * standalone {@code GatewayModuleConfiguration} (profile {@code gateway}) and the embedded broker
 * path ({@code BrokerModuleConfiguration}, profile {@code broker}) can inject, avoiding per-site
 * duplication of the factory wiring.
 *
 * <p>Lives in {@code io.camunda.zeebe.shared} because that is the only package component-scanned by
 * <em>both</em> module configurations; placing it under {@code io.camunda.zeebe.gateway} would make
 * it invisible to the broker profile.
 *
 * <p>The bean is {@link Lazy} so it is not materialised until first injection (#55754/#55755),
 * keeping startup safe for any deployment that does not yet wire the registry into the {@code
 * Gateway} or {@code Broker} constructor.
 *
 * <p>Activated only under the {@code gateway} or {@code broker} profiles; REST-only and other
 * application profiles do not load this configuration.
 */
@Configuration(proxyBeanMethods = false)
@Profile("gateway | broker")
public class PhysicalTenantGatewayAuthConfiguration {

  /**
   * Per-physical-tenant {@link AuthenticationHandler} registry for the gRPC gateway.
   *
   * <p>Returns an empty map when the API is unprotected so no OIDC or BASIC factory calls are made
   * in that case.
   */
  @Bean
  @Lazy
  public Map<String, AuthenticationHandler> ptHandlerRegistry(
      final Environment environment,
      final CamundaSecurityLibraryProperties securityProperties,
      @Autowired(required = false) final ScopedJwtDecoderFactory scopedJwtDecoderFactory,
      @Autowired(required = false)
          final ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory,
      @Autowired(required = false) final ServiceRegistry serviceRegistry,
      final PasswordEncoder passwordEncoder) {

    final var authConfigsByTenantId =
        PhysicalTenantAuthConfigurations.forAllPhysicalTenants(environment);
    final boolean authEnabled = !securityProperties.getAuthentication().isUnprotectedApi();

    return PhysicalTenantHandlerRegistry.build(
        authConfigsByTenantId,
        authEnabled,
        cfg -> scopedJwtDecoderFactory.buildIssuerAwareDecoder(cfg),
        cfg -> scopedOidcClaimsProviderFactory.buildClaimsProvider(cfg),
        ptId -> serviceRegistry.userServices(ptId),
        passwordEncoder);
  }
}
