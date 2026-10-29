/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.zeebe.broker.PhysicalTenantLifecycleService;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the per-physical-tenant secondary storage type, consulted by {@code
 * SecondaryStorageInterceptor} to reject requests for tenants with no secondary storage. Resolved
 * from the configuration a tenant currently runs with (the tenant lifecycle on a broker, else
 * {@link PhysicalTenantResolver}) rather than from any storage-specific configuration, so a bean
 * exists for every secondary storage type, including rdbms and none, and a tenant added at runtime
 * is answered like one the node started with.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseTypeProviderConfiguration {

  @Bean
  public Function<String, DatabaseType> databaseTypeProvider(
      final PhysicalTenantResolver physicalTenantResolver,
      final ObjectProvider<PhysicalTenantLifecycleService> lifecycle) {
    return tenantId -> {
      final var current = lifecycle.getIfAvailable();
      final var camunda =
          current != null
              ? current
                  .configuration(tenantId)
                  .orElseGet(() -> physicalTenantResolver.forPhysicalTenant(tenantId))
              : physicalTenantResolver.forPhysicalTenant(tenantId);
      return DatabaseType.from(camunda.getData().getSecondaryStorage().getType().name());
    };
  }
}
