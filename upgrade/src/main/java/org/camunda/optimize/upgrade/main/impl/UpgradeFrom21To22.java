package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UpgradeFrom21To22 implements Upgrade {

  public static final String FROM_VERSION = "2.1.0";
  public static final String TO_VERSION = "2.2.0";

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  @Override
  public void performUpgrade() {
    try {
      UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      // add upgrade steps here
      .build();

      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

}
