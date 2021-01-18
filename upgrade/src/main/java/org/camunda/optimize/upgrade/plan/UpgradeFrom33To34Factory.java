/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UpgradeFrom33To34Factory {

  public static UpgradePlan createUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.3.0")
      .toVersion("3.4.0")
      .addUpgradeStep(migrateFlowNodeStatusConfigToFilters())
      .build();
  }

  private static UpgradeStep migrateFlowNodeStatusConfigToFilters() {
    //@formatter:off
    final String script =
      "def reportEntityType = ctx._source.data.view.entity;\n" +
      "def currentFilters = ctx._source.data.filter;\n" +
      "if (reportEntityType == 'userTask' || reportEntityType == 'flowNode') {\n" +
      "  def executionState = ctx._source.data.configuration.flowNodeExecutionState;\n" +
      "  if (executionState == 'completed') {\n" +
      "    def newFilter = [\n" +
      "      'type': 'completedOrCanceledFlowNodesOnly',\n" +
      "      'filterLevel': 'view'\n" +
      "     ];" +
      "    newFilter.data = null;" +
      "    currentFilters.add(newFilter);\n" +
      "  } else if (executionState == 'running') {\n" +
      "    def newFilter = [\n" +
      "      'type': 'runningFlowNodesOnly',\n" +
      "      'filterLevel': 'view'\n" +
      "     ];" +
      "    newFilter.data = null;" +
      "    currentFilters.add(newFilter);\n" +
      "  } else if (executionState == 'canceled') {\n" +
      "    def newFilter = [\n" +
      "      'type': 'canceledFlowNodesOnly',\n" +
      "      'filterLevel': 'view'\n" +
      "     ];" +
      "    newFilter.data = null;" +
      "    currentFilters.add(newFilter);\n" +
      "  }\n" +
      "}\n" +
      "ctx._source.data.configuration.remove(\"flowNodeExecutionState\");\n";
    //@formatter:on
    return new UpdateIndexStep(new SingleProcessReportIndex(), script);
  }

}
