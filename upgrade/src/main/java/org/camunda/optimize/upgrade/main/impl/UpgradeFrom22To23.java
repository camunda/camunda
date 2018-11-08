package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpdateDataStep;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UpgradeFrom22To23 implements Upgrade {

  private static final String FROM_VERSION = "2.2.0";
  private static final String TO_VERSION = "2.3.0";

  private Logger logger = LoggerFactory.getLogger(getClass());
  private ConfigurationService configurationService = new ConfigurationService();

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
        .addUpgradeStep(relocateProcessPart())
        .build();
      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  private UpdateDataStep relocateProcessPart() {
    return new UpdateDataStep(
      "single-report",
      QueryBuilders.matchAllQuery(),
        "ctx._source.data.parameters = [\"processPart\": ctx._source.data.processPart];" +
        "ctx._source.data.remove(\"processPart\");"
    );
  }
}
