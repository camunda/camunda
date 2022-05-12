/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Upgrade38To39PlayFactory implements UpgradePlanFactory {
  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.8")
      .toVersion("3.9.0")
      .addUpgradeSteps(migrateReports())
      .build();
  }

  private static List<UpgradeStep> migrateReports() {
    return Stream.of(new SingleProcessReportIndex(), new SingleDecisionReportIndex())
      .map(index -> new UpdateIndexStep(index, getReportMigrationScript()))
      .collect(Collectors.toList());
  }

  private static String getReportMigrationScript() {
    // @formatter:off
    return
      "if (ctx._source.data != null) {" +
        "def configuration = ctx._source.data.configuration;" +
        "if (configuration != null && configuration.targetValue != null) {" +
        " configuration.targetValue.kpi = [\"type\": null, \"active\": false];" +
        "}" +
        "}";
    // @formatter:on
  }
}
