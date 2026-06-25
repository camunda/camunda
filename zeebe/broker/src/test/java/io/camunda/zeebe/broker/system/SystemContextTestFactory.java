/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import io.atomix.cluster.AtomixCluster;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.zeebe.broker.system.PhysicalTenantEngineContext;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.FeatureFlags;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Test helper for constructing a {@link SystemContext} with a single security configuration. */
public final class SystemContextTestFactory {

  private SystemContextTestFactory() {}

  /**
   * Builds a {@link SystemContext} where every known physical tenant (plus the {@code default}
   * tenant) maps to the same single {@link EngineSecurityConfig} and {@link
   * BrokerRequestAuthorizationConverter}.
   *
   * <p>{@code securityConfiguration} must be non-null: {@link SystemContext} validates the
   * initialization config during construction, so a null value would fail. Other collaborators —
   * notably {@code brokerRequestAuthorizationConverter} — may be {@code null} when a test does not
   * exercise them.
   */
  public static SystemContext singleTenant(
      final Duration shutdownTimeout,
      final BrokerCfg brokerCfg,
      final IdentityConfiguration identityConfiguration,
      final ActorScheduler scheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final MeterRegistry meterRegistry,
      final EngineSecurityConfig securityConfiguration,
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final JwtDecoder jwtDecoder,
      final OidcClaimsProvider oidcClaimsProvider,
      final SearchClientsProxy searchClientsProxy,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final NodeIdProvider nodeIdProvider,
      final PhysicalTenantIds physicalTenantIds) {
    return singleTenant(
        shutdownTimeout,
        brokerCfg,
        identityConfiguration,
        scheduler,
        cluster,
        brokerClient,
        meterRegistry,
        securityConfiguration,
        userServices,
        passwordEncoder,
        jwtDecoder,
        oidcClaimsProvider,
        searchClientsProxy,
        brokerRequestAuthorizationConverter,
        featureFlagsFrom(brokerCfg),
        nodeIdProvider,
        physicalTenantIds);
  }

  public static SystemContext singleTenant(
      final Duration shutdownTimeout,
      final BrokerCfg brokerCfg,
      final IdentityConfiguration identityConfiguration,
      final ActorScheduler scheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final MeterRegistry meterRegistry,
      final EngineSecurityConfig securityConfiguration,
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final JwtDecoder jwtDecoder,
      final OidcClaimsProvider oidcClaimsProvider,
      final SearchClientsProxy searchClientsProxy,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final FeatureFlags featureFlags,
      final NodeIdProvider nodeIdProvider,
      final PhysicalTenantIds physicalTenantIds) {
    return new SystemContext(
        shutdownTimeout,
        brokerCfg,
        identityConfiguration,
        scheduler,
        cluster,
        brokerClient,
        meterRegistry,
        singleValueMap(
            physicalTenantIds,
            new PhysicalTenantEngineContext(
                securityConfiguration, brokerRequestAuthorizationConverter, featureFlags)),
        tenantId -> userServices,
        passwordEncoder,
        authConfig -> jwtDecoder,
        authConfig -> oidcClaimsProvider,
        searchClientsProxy,
        nodeIdProvider,
        physicalTenantIds);
  }

  private static <T> Map<String, T> singleValueMap(
      final PhysicalTenantIds physicalTenantIds, final T value) {
    final Map<String, T> map = new HashMap<>();
    physicalTenantIds.known().forEach(id -> map.put(id, value));
    map.put(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, value);
    return map;
  }

  private static FeatureFlags featureFlagsFrom(final BrokerCfg brokerCfg) {
    return brokerCfg.getExperimental().getFeatures().toFeatureFlags();
  }
}
