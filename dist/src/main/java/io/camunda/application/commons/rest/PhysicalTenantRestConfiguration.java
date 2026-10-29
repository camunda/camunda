/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.beanoverrides.GatewayRestPropertiesOverride;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class PhysicalTenantRestConfiguration {

  /**
   * The REST view of the configuration a tenant currently runs with: on a broker, the one the
   * tenant lifecycle applied last, so a tenant added or changed at runtime is served with its own
   * values; elsewhere, the configuration the node started with.
   */
  @Bean
  public PhysicalTenantRestConfigProvider physicalTenantRestConfigProvider(
      final PhysicalTenantResolver physicalTenantResolver,
      final ObjectProvider<PhysicalTenantLifecycleService> lifecycle) {
    final Map<String, Camunda> boot = physicalTenantResolver.mapValues(Function.identity());
    return provider(
        tenantId -> {
          final var current = lifecycle.getIfAvailable();
          return current != null
              ? current.configuration(tenantId)
              : Optional.ofNullable(boot.get(tenantId));
        });
  }

  private static PhysicalTenantRestConfigProvider provider(
      final Function<String, Optional<Camunda>> configurations) {
    final Map<String, Converted> converted = new ConcurrentHashMap<>();
    return tenantId -> {
      final var camunda = configurations.apply(tenantId).orElse(null);
      if (camunda == null) {
        converted.remove(tenantId);
        throw new IllegalArgumentException("No REST config for physical tenant: " + tenantId);
      }
      return converted
          .compute(
              tenantId,
              (id, previous) ->
                  previous != null && previous.source() == camunda
                      ? previous
                      : new Converted(
                          camunda, new GatewayRestPropertiesOverride.Converter(camunda).convert()))
          .configuration();
    };
  }

  private record Converted(Camunda source, GatewayRestConfiguration configuration) {}
}
