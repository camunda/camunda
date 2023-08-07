/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import org.camunda.optimize.service.es.schema.MappingMetadataUtil;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Slf4j
public class Upgrade310To311PlanFactory implements UpgradePlanFactory {
  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.10")
      .toVersion("3.11.0")
      .addUpgradeSteps(addDescriptionFieldToReportIndices())
      .addUpgradeStep(addDescriptionFieldToDashboardIndex())
      .addUpgradeStep(updateTenantIdInProcessDefinitionDocumentC8())
      .addUpgradeSteps(updateTenantIdInProcessInstanceDocumentC8(upgradeExecutionDependencies))
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

  private UpgradeStep updateTenantIdInProcessDefinitionDocumentC8() {
   final BoolQueryBuilder query = boolQuery().mustNot(existsQuery(ProcessDefinitionIndex.TENANT_ID));
    final String script =
      // Only data sources with type == 'zeebe' will get migrated.
      // In case the data source has name == 'zeebe' it wont get migrated
      // In practice this should migrate all definitions for C8 environments only, and not affect C7 environments
      "if (ctx._source.dataSource.type == 'zeebe') {\n" +
      "  ctx._source.tenantId = '<default>';\n" +
      "}\n";
    return new UpdateDataStep(new ProcessDefinitionIndex(), query, script);
  }

  private List<UpgradeStep> updateTenantIdInProcessInstanceDocumentC8(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    List<String> processDefinitionKeys = MappingMetadataUtil.retrieveProcessInstanceIndexIdentifiers(
      upgradeExecutionDependencies.getEsClient(),
      false
    );
    final BoolQueryBuilder query =  boolQuery().mustNot(existsQuery(ProcessInstanceIndex.TENANT_ID));
    final String script =
      // Only data sources with type == 'zeebe' will get migrated.
      // In case the data source has name == 'zeebe' it wont get migrated
      // In practice this should migrate all definitions for C8 environments only, and not affect C7 environments
      "if (ctx._source.dataSource.type == 'zeebe') {\n" +
      "  ctx._source.tenantId = '<default>';\n" +
      "  for (incident in ctx._source.incidents) {\n" +
      "      incident.tenantId = '<default>';\n" +
      "  }\n" +
      "}\n";
    List<UpgradeStep> updateDataSteps = new ArrayList<>();
    for (String processDefinitionKey : processDefinitionKeys) {
        updateDataSteps.add(new UpdateDataStep(new ProcessInstanceIndex(processDefinitionKey),query, script));
      }
    return updateDataSteps;
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
