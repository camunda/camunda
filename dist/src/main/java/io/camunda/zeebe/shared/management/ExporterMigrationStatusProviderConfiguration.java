/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.admin.ClusterExporterMigrationStatusProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the exporter {@link MigrationStatusProvider} for the upgrade-readiness endpoint,
 * collected by {@link UpgradeReadinessEndpoint} alongside every other provider bean. {@link
 * PhysicalTenantIds} is injected as the interface, not the concrete {@code PhysicalTenantResolver}
 * that implements it, so {@code zeebe/gateway} doesn't depend on the {@code configuration} module.
 */
@Configuration(proxyBeanMethods = false)
public class ExporterMigrationStatusProviderConfiguration {

  @Bean
  public MigrationStatusProvider exporterMigrationStatusProvider(
      final BrokerClient brokerClient, final PhysicalTenantIds physicalTenantIds) {
    return new ClusterExporterMigrationStatusProvider(brokerClient, physicalTenantIds);
  }
}
