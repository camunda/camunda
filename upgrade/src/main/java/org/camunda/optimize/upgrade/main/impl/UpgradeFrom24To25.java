/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;


public class UpgradeFrom24To25 implements Upgrade {

  private static final String FROM_VERSION = "2.4.0";
  private static final String TO_VERSION = "2.5.0";

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
        .addUpgradeStep(new UpdateDataStep(
          SINGLE_DECISION_REPORT_TYPE,
          QueryBuilders.matchAllQuery(),
          getMigrate25ReportViewStructureScript()
        ))
        .build();
      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  public static String getMigrate25ReportViewStructureScript() {
    // @formatter:off
    return
      "def reportData = ctx._source.data;\n" +
      "if (reportData.view != null) {\n" +
      "  if (reportData.view.operation != null) {\n" +
      "    if (reportData.view.operation == \"rawData\") {\n" +
      "      reportData.view.property = \"rawData\";\n" +
      "    }\n" +
      "  }\n" +
      "  reportData.view.remove('operation');\n"+
      "}\n";
    // @formatter:on
  }
}
