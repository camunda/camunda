/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.service.MigrationStatusAggregator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Reports whether this cluster has completed every condition required before the next minor-version
 * upgrade can safely be triggered (see camunda/product-hub#3067).
 *
 * <p>Collects every {@link MigrationStatusProvider} bean registered in this process, via the shared
 * {@link MigrationStatusAggregator} bean; see {@link UpgradeReadinessResponse}.
 */
@Component
@RestControllerEndpoint(id = "upgradeReadiness")
public class UpgradeReadinessEndpoint {

  private final MigrationStatusAggregator aggregator;

  @Autowired
  public UpgradeReadinessEndpoint(final MigrationStatusAggregator aggregator) {
    this.aggregator = aggregator;
  }

  @GetMapping(produces = "application/json")
  public UpgradeReadinessResponse getUpgradeReadiness() {
    final var physicalTenants = aggregator.aggregate();
    return new UpgradeReadinessResponse(isUpgradeable(physicalTenants), physicalTenants);
  }

  /**
   * {@code true} only once every registered condition reports {@code MIGRATED} for every known
   * physical tenant; {@code false} whenever nothing is known at all (see {@link
   * UpgradeReadinessResponse}).
   */
  private static boolean isUpgradeable(
      final Map<String, Map<String, MigrationConditionStatus>> physicalTenants) {
    return !physicalTenants.isEmpty()
        && physicalTenants.values().stream()
            .allMatch(
                conditions ->
                    !conditions.isEmpty()
                        && conditions.values().stream()
                            .allMatch(status -> status.state() == MigrationState.MIGRATED));
  }
}
