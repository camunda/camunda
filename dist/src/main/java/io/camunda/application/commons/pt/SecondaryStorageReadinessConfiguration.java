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
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link SecondaryStorageReadiness} consulted for request-time rejection and node
 * readiness, backed by the schema-initialization state of the configured secondary storage. A
 * tenant in recovery mode is not ready, even once its schema is initialized.
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
      final SchemaManagerContainer schemaManagerContainer,
      final ObjectProvider<BrokerTopologyManager> brokerTopologyManager) {
    return new SchemaInitializationSecondaryStorageReadiness(
        physicalTenantIds,
        initializedAndNotRecovering(schemaManagerContainer::isInitialized, brokerTopologyManager));
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
  public SecondaryStorageReadiness rdbmsSecondaryStorageReadiness(
      final PhysicalTenantIds physicalTenantIds,
      final RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry,
      final ObjectProvider<BrokerTopologyManager> brokerTopologyManager) {
    return new SchemaInitializationSecondaryStorageReadiness(
        physicalTenantIds,
        initializedAndNotRecovering(
            rdbmsSchemaManagerRegistry::isInitialized, brokerTopologyManager));
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.none)
  public SecondaryStorageReadiness noSecondaryStorageReadiness() {
    return SecondaryStorageReadiness.ALWAYS_READY;
  }

  /** Whether the tenant is in recovery mode; never, on a node without a topology manager. */
  public static Predicate<String> isRecovering(
      final ObjectProvider<BrokerTopologyManager> brokerTopologyManager) {
    return physicalTenantId -> {
      final var topologyManager = brokerTopologyManager.getIfAvailable();
      return topologyManager != null && topologyManager.isRecoveringOrUnknown(physicalTenantId);
    };
  }

  private static Predicate<String> initializedAndNotRecovering(
      final Predicate<String> schemaInitialized,
      final ObjectProvider<BrokerTopologyManager> brokerTopologyManager) {
    return schemaInitialized.and(isRecovering(brokerTopologyManager).negate());
  }

  @Bean
  public SecondaryStorageReadinessMetrics secondaryStorageReadinessMetrics(
      final PhysicalTenantIds physicalTenantIds, final SecondaryStorageReadiness readiness) {
    return new SecondaryStorageReadinessMetrics(physicalTenantIds, readiness);
  }
}
