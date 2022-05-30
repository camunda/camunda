/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

public class Upgrade38To39PlanFactory implements UpgradePlanFactory {
  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.8")
      .toVersion("3.9.0")
      .addUpgradeStep(migrateDecisionReports())
      .addUpgradeStep(migrateProcessReports())
      .addUpgradeStep(migrateDashboards())
      .build();
  }

  private static UpgradeStep migrateDecisionReports() {
    return new UpdateIndexStep(new SingleDecisionReportIndex(), getReportKPIMigrationScript());
  }

  private static UpgradeStep migrateProcessReports() {
    String processReportMigrationScript = getReportKPIMigrationScript() + getManagementProcessReportMigrationScript();
    return new UpdateIndexStep(new SingleProcessReportIndex(), processReportMigrationScript);
  }

  private static UpgradeStep migrateDashboards() {
    return new UpdateIndexStep(new DashboardIndex(), "ctx._source.managementDashboard = false;\n");
  }

  private static String getReportKPIMigrationScript() {
    // @formatter:off
    return
      "  if (ctx._source.data != null) {\n" +
        "  def configuration = ctx._source.data.configuration;\n" +
        "  if (configuration != null && configuration.targetValue != null) {\n" +
        "    def targetValue = configuration.targetValue;\n" +
        "    configuration.targetValue.isKpi = false;\n" +
        "    if (targetValue.durationProgress != null) {\n" +
        "      def durationProgressTarget = targetValue.durationProgress.target;\n" +
        "      if (durationProgressTarget != null) {\n" +
        "        durationProgressTarget?.put(\"isBelow\", false);\n" +
        "      }\n" +
        "    }\n" +
        "    if (targetValue.countProgress != null) {\n" +
        "      def countProgressTarget = targetValue.countProgress;\n" +
        "      countProgressTarget?.put(\"isBelow\", false);\n" +
        "    }\n" +
        "  }\n" +
        "}\n";
    // @formatter:on
  }

  private static String getManagementProcessReportMigrationScript() {
    // @formatter:off
    return
      "  if (ctx._source.data != null) {\n" +
        "  ctx._source.data.managementReport = false;\n" +
        "}\n";
    // @formatter:on
  }

}
