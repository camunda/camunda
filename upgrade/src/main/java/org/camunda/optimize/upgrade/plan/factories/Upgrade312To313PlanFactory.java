/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;

public class Upgrade312To313PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.12")
      .toVersion("3.13.0")
      .build();
  }

}
