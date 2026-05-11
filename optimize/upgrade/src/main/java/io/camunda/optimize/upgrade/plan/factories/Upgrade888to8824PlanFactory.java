/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;

/**
 * Explicit no-op upgrade plan bridging the version gap between Optimize {@code 8.8.8} and {@code
 * 8.8.24}.
 *
 * <p>Optimize 8.8.9 through 8.8.23 were never released: when Optimize was integrated into the
 * Camunda monorepo (see PR #51445), its patch version was realigned with the rest of the monorepo
 * and jumped directly from {@code 8.8.8} to {@code 8.8.24}. No actual schema or data migration
 * happened in between, so the upgrade is a no-op — but the jump must still be declared explicitly.
 *
 * <p>The patch-to-patch auto-generator in {@code UpgradePlanRegistry} walks backwards one patch at
 * a time and would otherwise synthesize no-op plans for every non-existent version in {@code
 * 8.8.9..8.8.23}. Registering this explicit plan makes the generator jump from {@code 8.8.24}
 * straight to {@code 8.8.8}, skipping the entire missing range.
 */
public class Upgrade888to8824PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan().fromVersion("8.8.8").toVersion("8.8.24").build();
  }
}
