package org.camunda.optimize.upgrade.plan;

import org.camunda.optimize.upgrade.steps.UpgradeStep;

public class UpgradePlanBuilder {


  public static AddFromVersionBuilder createUpgradePlan() throws Exception {
    UpgradeExecutionPlan upgradeExecutionPlan = new UpgradeExecutionPlan();
    return new UpgradePlanBuilder().addFromVersion(upgradeExecutionPlan);
  }

  private AddFromVersionBuilder addFromVersion(UpgradeExecutionPlan upgradeExecutionPlan) {
    return new AddFromVersionBuilder(upgradeExecutionPlan);
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
