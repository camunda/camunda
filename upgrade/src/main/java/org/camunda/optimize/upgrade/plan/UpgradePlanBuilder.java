/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import org.camunda.optimize.upgrade.steps.UpgradeStep;

public class UpgradePlanBuilder {


  public static AddUpgradeDependenciesBuilder createUpgradePlan() {
    UpgradeExecutionPlan upgradeExecutionPlan = new UpgradeExecutionPlan();
    return new UpgradePlanBuilder().startUpgradePlanBuild(upgradeExecutionPlan);
  }

  private AddUpgradeDependenciesBuilder startUpgradePlanBuild(UpgradeExecutionPlan upgradeExecutionPlan) {
    return new AddUpgradeDependenciesBuilder(upgradeExecutionPlan);
  }

  public class AddUpgradeDependenciesBuilder {

    private UpgradeExecutionPlan upgradeExecutionPlan;

    public AddUpgradeDependenciesBuilder(UpgradeExecutionPlan upgradeExecutionPlan) {
      this.upgradeExecutionPlan = upgradeExecutionPlan;
    }

    public AddFromVersionBuilder addUpgradeDependencies(UpgradeExecutionDependencies upgradeDependencies) {
      upgradeExecutionPlan.addUpgradeDependencies(upgradeDependencies);
      return new AddFromVersionBuilder(upgradeExecutionPlan);
    }
  }

  public class AddFromVersionBuilder {

    private UpgradeExecutionPlan upgradeExecutionPlan;

    public AddFromVersionBuilder(UpgradeExecutionPlan upgradeExecutionPlan) {
      this.upgradeExecutionPlan = upgradeExecutionPlan;
    }

    public AddToVersionBuilder fromVersion(String fromVersion) {
      upgradeExecutionPlan.setFromVersion(fromVersion);
      return new AddToVersionBuilder(upgradeExecutionPlan);
    }
  }

  public class AddToVersionBuilder {
    private UpgradeExecutionPlan upgradeExecutionPlan;

    public AddToVersionBuilder(UpgradeExecutionPlan upgradeExecutionPlan) {
      this.upgradeExecutionPlan = upgradeExecutionPlan;
    }

    public AddUpgradeStepBuilder toVersion(String toVersion) {
      upgradeExecutionPlan.setToVersion(toVersion);
      return new AddUpgradeStepBuilder(upgradeExecutionPlan);
    }
  }

  public class AddUpgradeStepBuilder {

    private UpgradeExecutionPlan upgradeExecutionPlan;

    public AddUpgradeStepBuilder(UpgradeExecutionPlan upgradeExecutionPlan) {
      this.upgradeExecutionPlan = upgradeExecutionPlan;
    }

    public AddUpgradeStepBuilder addUpgradeStep(UpgradeStep upgradeStep) {
      upgradeExecutionPlan.addUpgradeStep(upgradeStep);
      return this;
    }

    public UpgradePlan build() {
      return upgradeExecutionPlan;
    }

  }
}
