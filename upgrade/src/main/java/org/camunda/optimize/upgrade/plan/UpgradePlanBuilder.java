/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import org.camunda.optimize.upgrade.steps.UpgradeStep;

import java.util.List;

public class UpgradePlanBuilder {

  public static AddFromVersionBuilder createUpgradePlan() {
    UpgradePlan upgradePlan = new UpgradePlan();
    return new UpgradePlanBuilder().startUpgradePlanBuild(upgradePlan);
  }

  private AddFromVersionBuilder startUpgradePlanBuild(UpgradePlan upgradePlan) {
    return new AddFromVersionBuilder(upgradePlan);
  }

  public static class AddFromVersionBuilder {

    private final UpgradePlan upgradePlan;

    public AddFromVersionBuilder(UpgradePlan upgradePlan) {
      this.upgradePlan = upgradePlan;
    }

    public AddToVersionBuilder fromVersion(String fromVersion) {
      upgradePlan.setFromVersion(fromVersion);
      return new AddToVersionBuilder(upgradePlan);
    }
  }

  public static class AddToVersionBuilder {
    private final UpgradePlan upgradePlan;

    public AddToVersionBuilder(UpgradePlan upgradePlan) {
      this.upgradePlan = upgradePlan;
    }

    public AddUpgradeStepBuilder toVersion(String toVersion) {
      upgradePlan.setToVersion(toVersion);
      return new AddUpgradeStepBuilder(upgradePlan);
    }
  }

  public static class AddUpgradeStepBuilder {

    private final UpgradePlan upgradePlan;

    public AddUpgradeStepBuilder(UpgradePlan upgradePlan) {
      this.upgradePlan = upgradePlan;
    }

    public AddUpgradeStepBuilder addUpgradeStep(UpgradeStep upgradeStep) {
      upgradePlan.addUpgradeStep(upgradeStep);
      return this;
    }

    public AddUpgradeStepBuilder addUpgradeSteps(List<UpgradeStep> upgradeSteps) {
      upgradePlan.addUpgradeSteps(upgradeSteps);
      return this;
    }

    public UpgradePlan build() {
      return upgradePlan;
    }

  }
}
