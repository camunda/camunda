/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.service.MigrationStatusAggregator;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the single {@link MigrationStatusAggregator} instance shared by the {@code
 * upgradeReadiness} actuator endpoint ({@link UpgradeReadinessEndpoint}) and the public {@code GET
 * /cluster/v2/status/upgrade} endpoint (camunda/camunda#61619). Both must read from one instance so
 * their caching/backfill behavior can never diverge.
 *
 * <p>Deliberately not conditional on any HTTP gateway being enabled: the actuator endpoint must
 * keep working on broker-only nodes with no REST gateway, matching the {@link
 * MigrationStatusProvider} bean configurations it depends on (none of which are gateway-gated
 * either).
 */
@Configuration(proxyBeanMethods = false)
public class MigrationStatusAggregatorConfiguration {

  @Bean
  public MigrationStatusAggregator migrationStatusAggregator(
      final List<MigrationStatusProvider> providers) {
    return new MigrationStatusAggregator(providers);
  }
}
