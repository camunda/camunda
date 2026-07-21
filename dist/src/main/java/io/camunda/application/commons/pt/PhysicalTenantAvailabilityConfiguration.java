/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.cluster.PhysicalTenantAvailability;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.search.schema.SchemaManagerContainer;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link PhysicalTenantAvailability} consulted for request-time rejection and node
 * readiness, backed by the schema-initialization state of the configured secondary storage.
 */
@NullMarked
@Configuration(proxyBeanMethods = false)
public class PhysicalTenantAvailabilityConfiguration {

  @Bean
  @ConditionalOnSecondaryStorageType({
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public PhysicalTenantAvailability searchEnginePhysicalTenantAvailability(
      final PhysicalTenantIds physicalTenantIds,
      final SchemaManagerContainer schemaManagerContainer) {
    return new SchemaInitializationPhysicalTenantAvailability(
        physicalTenantIds, schemaManagerContainer::isInitialized);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
  public PhysicalTenantAvailability rdbmsPhysicalTenantAvailability(
      final PhysicalTenantIds physicalTenantIds,
      final RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry) {
    return new SchemaInitializationPhysicalTenantAvailability(
        physicalTenantIds, rdbmsSchemaManagerRegistry::isInitialized);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.none)
  public PhysicalTenantAvailability noSecondaryStoragePhysicalTenantAvailability() {
    return PhysicalTenantAvailability.ALWAYS_SERVICEABLE;
  }

  @Bean
  public PhysicalTenantAvailabilityMetrics physicalTenantAvailabilityMetrics(
      final PhysicalTenantIds physicalTenantIds, final PhysicalTenantAvailability availability) {
    return new PhysicalTenantAvailabilityMetrics(physicalTenantIds, availability);
  }
}
