/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.search.schema.SchemaManagerContainer;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link SecondaryStorageReadiness} consulted for request-time rejection and node
 * readiness, backed by the schema-initialization state of the configured secondary storage.
 */
@NullMarked
@Configuration(proxyBeanMethods = false)
public class SecondaryStorageReadinessConfiguration {

  @Bean
  @ConditionalOnSecondaryStorageType({
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public SecondaryStorageReadiness searchEngineSecondaryStorageReadiness(
      final PhysicalTenantIds physicalTenantIds,
      final SchemaManagerContainer schemaManagerContainer) {
    return new SchemaInitializationSecondaryStorageReadiness(
        physicalTenantIds, schemaManagerContainer::isInitialized);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
  public SecondaryStorageReadiness rdbmsSecondaryStorageReadiness(
      final PhysicalTenantIds physicalTenantIds,
      final RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry) {
    return new SchemaInitializationSecondaryStorageReadiness(
        physicalTenantIds, rdbmsSchemaManagerRegistry::isInitialized);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.none)
  public SecondaryStorageReadiness noSecondaryStorageReadiness() {
    return SecondaryStorageReadiness.ALWAYS_READY;
  }

  @Bean
  public SecondaryStorageReadinessMetrics secondaryStorageReadinessMetrics(
      final PhysicalTenantIds physicalTenantIds, final SecondaryStorageReadiness readiness) {
    return new SecondaryStorageReadinessMetrics(physicalTenantIds, readiness);
  }
}
