package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.AddFieldStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static org.camunda.optimize.service.es.schema.type.ReportType.REPORT_TYPE;


public class UpgradeFrom21To22 implements Upgrade {

  private static final String FROM_VERSION = "2.1.0";
  private static final String TO_VERSION = "2.2.0";

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
              .addUpgradeStep(createAddStateFieldStep())
              .addUpgradeStep(createAddReportTypeFieldStep())
              .build();

      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  private AddFieldStep createAddReportTypeFieldStep() {
    return new AddFieldStep(
      "report",
      "$.mappings.report.properties",
      REPORT_TYPE,
      Collections.singletonMap("type", "keyword"),
      "ctx._source.reportType = single"
    );
  }

  private AddFieldStep createAddStateFieldStep() {
    return new AddFieldStep(
      "process-instance",
      "$.mappings.process-instance.properties",
      "state",
      Collections.singletonMap("type", "keyword"),
      "ctx._source.state = null"
    );
  }

}
