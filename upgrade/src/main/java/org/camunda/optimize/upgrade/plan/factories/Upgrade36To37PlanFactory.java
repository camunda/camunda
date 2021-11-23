/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex;
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

public class Upgrade36To37PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.6")
      .toVersion("3.7.0")
      .addUpgradeSteps(migrateReportFiltersAndConfig())
      .addUpgradeStep(migrateDashboards())
      .addUpgradeStep(migrateExternalProcessVariableIndex())
      .build();
  }

  private static UpgradeStep migrateDashboards() {
    // This script migrates the filters and also sets the default dashboard refresh rate
    // @formatter:off
    final String dashboardMigrationScript =
      "def filters = ctx._source.availableFilters;" +
      "ctx._source.refreshRateSeconds = null;" +
      "if (filters != null) {" +
        "for (def filter : filters) {" +
          "if (\"startDate\".equals(filter.type)) {" +
            "filter.type = \"instanceStartDate\";" +
          "}" +
          "if (\"endDate\".equals(filter.type)) {" +
            "filter.type = \"instanceEndDate\";" +
          "}" +
        "}" +
      "}";
    // @formatter:on
    return new UpdateIndexStep(new DashboardIndex(), dashboardMigrationScript);
  }

  private static List<UpgradeStep> migrateReportFiltersAndConfig() {
    // @formatter:off
    final String reportMigrationScript =
      "if (ctx._source.data != null) {\n" +
        // migrate report filters
        "def filters = ctx._source.data.filter;\n" +
        "if (filters != null) {\n" +
          "for (def filter : filters) {\n" +
            "if (\"startDate\".equals(filter.type)) {\n" +
              "filter.type = \"instanceStartDate\";\n" +
            "}\n" +
            "if (\"endDate\".equals(filter.type)) {\n" +
              "filter.type = \"instanceEndDate\";\n" +
            "}\n" +
          "}\n" +
        "}\n" +
        // add default logScale value
        "def config = ctx._source.data.configuration;\n" +
        "if (config != null) {\n" +
          "config.logScale = false;\n" +
        "}\n" +
      "}";
    // @formatter:on
    return Stream.of(new SingleProcessReportIndex(), new SingleDecisionReportIndex())
      .map(index -> new UpdateIndexStep(index, reportMigrationScript))
      .collect(Collectors.toList());
  }

  private static UpgradeStep migrateExternalProcessVariableIndex() {
    return new UpdateIndexStep(new ExternalProcessVariableIndex(), "ctx._source.serializationDataFormat = null;");
  }

}
