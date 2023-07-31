/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

import java.util.List;

public class Upgrade310To311PlanFactory implements UpgradePlanFactory {
  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.10")
      .toVersion("3.11.0")
      .addUpgradeSteps(addDescriptionFieldToReportIndices())
      .addUpgradeStep(addDescriptionFieldToDashboardIndex())
      .build();
  }

  private UpgradeStep addDescriptionFieldToDashboardIndex() {
    return new UpdateIndexStep(new DashboardIndex(), addDescriptionScript());
  }

  private List<UpgradeStep> addDescriptionFieldToReportIndices() {
    return List.of(
      new UpdateIndexStep(new SingleProcessReportIndex(), addDescriptionScript() + migrateProcessReportColumnsScript()),
      new UpdateIndexStep(new SingleDecisionReportIndex(), addDescriptionScript()),
      new UpdateIndexStep(new CombinedReportIndex(), addDescriptionScript())
    );
  }

  private static String addDescriptionScript() {
    return "ctx._source.description = null;\n";
  }

  private static String migrateProcessReportColumnsScript() {
    // @formatter:off
    return
    "  def reportData = ctx._source.data;\n" +
      "if (reportData != null) {\n" +
      "  def reportConfig = reportData.configuration;\n" +
      "  if (reportConfig != null) {\n" +
      "    def reportColumns = reportConfig.tableColumns;\n" +
      "    if (reportColumns != null) {\n" +
      "      def newIncludedColumns = new ArrayList();\n" +
      "      reportColumns.includedColumns.forEach(includedColumn -> {\n" +
      "        if (includedColumn == \"numberOfUserTasks\") {\n" +
      "          newIncludedColumns.add(\"count:userTasks\");\n" +
      "        } else if (includedColumn == \"numberOfIncidents\") {\n" +
      "          newIncludedColumns.add(\"count:incidents\");\n" +
      "        } else if (includedColumn == \"numberOfOpenIncidents\") {\n" +
      "          newIncludedColumns.add(\"count:openIncidents\");\n" +
      "        } else {\n" +
      "          newIncludedColumns.add(includedColumn);\n" +
      "        }\n" +
      "      });\n" +
      "      reportColumns.includedColumns = newIncludedColumns;\n" +

      "      def newExcludedColumns = new ArrayList();\n" +
      "      reportColumns.excludedColumns.forEach(excludedColumn -> {\n" +
      "        if (excludedColumn == \"numberOfUserTasks\") {\n" +
      "          newExcludedColumns.add(\"count:userTasks\");\n" +
      "        } else if (excludedColumn == \"numberOfIncidents\") {\n" +
      "          newExcludedColumns.add(\"count:incidents\");\n" +
      "        } else if (excludedColumn == \"numberOfOpenIncidents\") {\n" +
      "          newExcludedColumns.add(\"count:openIncidents\");\n" +
      "        } else {\n" +
      "          newExcludedColumns.add(excludedColumn);\n" +
      "        }\n" +
      "      });\n" +
      "      reportColumns.excludedColumns = newExcludedColumns;\n" +

      "      def newColumnOrder = new ArrayList();\n" +
      "      reportColumns.columnOrder.forEach(column -> {\n" +
      "        if (column == \"numberOfUserTasks\") {\n" +
      "          newColumnOrder.add(\"count:userTasks\");\n" +
      "        } else if (column == \"numberOfIncidents\") {\n" +
      "          newColumnOrder.add(\"count:incidents\");\n" +
      "        } else if (column == \"numberOfOpenIncidents\") {\n" +
      "          newColumnOrder.add(\"count:openIncidents\");\n" +
      "        } else {\n" +
      "          newColumnOrder.add(column);\n" +
      "        }\n" +
      "      });\n" +
      "      reportColumns.columnOrder = newColumnOrder;\n" +
      "    }\n" +
      "  }\n" +
      "}\n";
    // @formatter:on
  }

}
