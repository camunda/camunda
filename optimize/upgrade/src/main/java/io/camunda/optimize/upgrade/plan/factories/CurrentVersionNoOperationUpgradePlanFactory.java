/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.upgrade.plan.factories;

import io.camunda.optimize.service.metadata.PreviousVersion;
import io.camunda.optimize.service.metadata.Version;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;

public class CurrentVersionNoOperationUpgradePlanFactory implements UpgradePlanFactory {

  public UpgradePlan createUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(PreviousVersion.PREVIOUS_VERSION_MAJOR_MINOR)
        .toVersion(Version.VERSION)
        .build();
  }

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return createUpgradePlan();
  }
}
