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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Upgrade86to87PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    validateOptimizeModeAndFailIfC7DataPresent(
        dependencies.databaseClient(), dependencies.indexNameService());
    return UpgradePlanBuilder.createUpgradePlan().fromVersion("8.6").toVersion("8.7.0").build();
  }

  @Override
  public void logErrorMessage(final String message) {
    log.error(message);
  }
}
