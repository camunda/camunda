/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.elasticsearch.index.query.QueryBuilders;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class UpgradeFrom30To31 extends UpgradeProcedure {
  public static final String FROM_VERSION = "3.0.0";
  public static final String TO_VERSION = "3.1.0";

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  public UpgradePlan buildUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(migrateAxisLabels(SINGLE_PROCESS_REPORT_INDEX_NAME))
      .addUpgradeStep(migrateAxisLabels(SINGLE_DECISION_REPORT_INDEX_NAME))
      .addUpgradeStep(migrateAxisLabels(COMBINED_REPORT_INDEX_NAME))
      .build();
  }

  private UpgradeStep migrateAxisLabels(final String index) {
    //@formatter:off
    final String script =
        "if (ctx._source.data.configuration.xlabel != null) {\n" +
        "  if (ctx._source.data.configuration.xLabel == null) {\n" +
        "    ctx._source.data.configuration.xLabel = ctx._source.data.configuration.xlabel;\n" +
        "  }\n" +
        "  ctx._source.data.configuration.remove(\"xlabel\");\n" +
        "}\n" +
        "if (ctx._source.data.configuration.ylabel != null) {\n" +
        "  if (ctx._source.data.configuration.yLabel == null) {\n" +
        "    ctx._source.data.configuration.yLabel = ctx._source.data.configuration.ylabel;\n" +
        "  }\n" +
        "  ctx._source.data.configuration.remove(\"ylabel\");\n" +
        "}\n"
      ;
    //@formatter:on
    return new UpdateDataStep(
      index,
      QueryBuilders.matchAllQuery(),
      script
    );
  }

}
