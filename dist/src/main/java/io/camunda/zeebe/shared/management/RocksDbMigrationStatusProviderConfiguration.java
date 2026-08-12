/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.MigrationStatusProvider;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.admin.ClusterRocksDbMigrationStatusProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the RocksDB {@link MigrationStatusProvider} for the upgrade-readiness endpoint
 * (camunda/product-hub#3067), collected by {@link UpgradeReadinessEndpoint} alongside every other
 * provider bean.
 *
 * <p>{@link PhysicalTenantIds} is injected as the interface, not the concrete {@code
 * PhysicalTenantResolver} (which implements it) — this keeps {@code zeebe/gateway} from depending
 * on the {@code configuration} module, mirroring how {@code db/rdbms} stays decoupled from it too.
 */
@Configuration(proxyBeanMethods = false)
public class RocksDbMigrationStatusProviderConfiguration {

  @Bean
  public MigrationStatusProvider rocksDbMigrationStatusProvider(
      final BrokerClient brokerClient, final PhysicalTenantIds physicalTenantIds) {
    return new ClusterRocksDbMigrationStatusProvider(brokerClient, physicalTenantIds);
  }
}
