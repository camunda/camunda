/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import io.camunda.optimize.service.db.es.schema.index.BusinessValueOverviewIndexES;
import io.camunda.optimize.service.db.es.schema.index.BusinessValueTargetIndexES;
import io.camunda.optimize.service.db.es.schema.index.JobRegistryIndexES;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.steps.schema.CreateIndexStep;

public class Upgrade89to810PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion("8.9")
        .toVersion("8.10.0")
        .addUpgradeStep(new CreateIndexStep(new BusinessValueTargetIndexES()))
        .addUpgradeStep(new CreateIndexStep(new BusinessValueOverviewIndexES()))
        .addUpgradeStep(new CreateIndexStep(new JobRegistryIndexES()))
        .build();
  }
}
