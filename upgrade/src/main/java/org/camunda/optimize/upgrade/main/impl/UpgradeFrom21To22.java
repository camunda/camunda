package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UpgradeFrom21To22 implements Upgrade {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public String getInitialVersion() {
    return "2.1.0";
  }

  @Override
  public String getTargetVersion() {
    return "2.2.0";
  }

  @Override
  public void performUpgrade() {
    try {
      UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("2.1.0-SNAPSHOT")
      .toVersion("2.2.0")
      // add upgrade steps here
      .build();

      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

}
