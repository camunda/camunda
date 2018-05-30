package org.camunda.optimize.upgrade.main;

import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UpgradeFrom21To22 {

  protected static Logger logger = LoggerFactory.getLogger(UpgradeFrom21To22.class);

  public static void main(String[] args) throws Exception {

    UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("2.1.0-SNAPSHOT")
      .toVersion("2.2.0")
      // add upgrade steps here
      .build();

    try {
      logger.info("Execute upgrade...");
      upgradePlan.execute();
      logger.info("Finished upgrade successfully!");
      System.exit(0);
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }


}
