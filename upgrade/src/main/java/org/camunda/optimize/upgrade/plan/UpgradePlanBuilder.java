/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan;

import com.vdurmont.semver4j.Semver;
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
      // LOOSE to allow for missing patch if an update applies to all patches of a minor
      upgradePlan.setFromVersion(new Semver(fromVersion, Semver.SemverType.LOOSE));
      return new AddToVersionBuilder(upgradePlan);
    }
  }

  public static class AddToVersionBuilder {
    private final UpgradePlan upgradePlan;

    public AddToVersionBuilder(UpgradePlan upgradePlan) {
      this.upgradePlan = upgradePlan;
    }

    public AddUpgradeStepBuilder toVersion(String toVersion) {
      upgradePlan.setToVersion(new Semver(toVersion));
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
