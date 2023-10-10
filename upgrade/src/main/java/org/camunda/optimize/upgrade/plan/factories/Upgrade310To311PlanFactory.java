/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.service.es.schema.MappingMetadataUtil;
import org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.POSITION_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ZEEBE_DATA_SOURCE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
public class Upgrade310To311PlanFactory implements UpgradePlanFactory {
  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    UpgradePlanBuilder.AddUpgradeStepBuilder upgradePlanBuilder = UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.10")
      .toVersion("3.11.0")
      .addUpgradeSteps(addDescriptionFieldToReportIndices())
      .addUpgradeStep(addDescriptionFieldToDashboardIndex());

    if (isC8Instance(upgradeExecutionDependencies)) {
      upgradePlanBuilder = upgradePlanBuilder
        .addUpgradeStep(updateTenantIdInProcessDefinitionDocumentC8())
        .addUpgradeSteps(updateTenantIdInProcessInstanceDocumentC8(upgradeExecutionDependencies))
        .addUpgradeStep(updateDefaultTenantIdOfCollectionScopesInC8())
        .addUpgradeStep(updateDefaultTenantIdOfProcessReportsInC8());
    }

    return upgradePlanBuilder.build();
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
    final BoolQueryBuilder query = boolQuery()
      .must(termQuery(AbstractDefinitionIndex.DATA_SOURCE + "." + DataSourceDto.Fields.type, ZEEBE_DATA_SOURCE))
      .mustNot(existsQuery(ProcessDefinitionIndex.TENANT_ID));
    return new UpdateDataStep(
      new ProcessDefinitionIndex(),
      query,
      String.format("ctx._source.tenantId = '%s';", ZEEBE_DEFAULT_TENANT_ID)
    );
  }

  private List<UpgradeStep> updateTenantIdInProcessInstanceDocumentC8(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    final BoolQueryBuilder query = boolQuery()
      .must(termQuery(ProcessInstanceIndex.DATA_SOURCE + "." + DataSourceDto.Fields.type, ZEEBE_DATA_SOURCE))
      .mustNot(existsQuery(ProcessInstanceIndex.TENANT_ID));
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("defaultTenantId", ZEEBE_DEFAULT_TENANT_ID)
        .build()
    );
    final String script = substitutor.replace(
      "  ctx._source.tenantId = '${defaultTenantId}';\n" +
        "  for (incident in ctx._source.incidents) {\n" +
        "      incident.tenantId = '${defaultTenantId}';\n" +
        "  }\n" +
        "  for (flowNode in ctx._source.flowNodeInstances) {\n" +
        "      flowNode.tenantId = '${defaultTenantId}';\n" +
        "  }\n");
    final List<String> processDefinitionKeys = MappingMetadataUtil.retrieveProcessInstanceIndexIdentifiers(
      upgradeExecutionDependencies.getEsClient(),
      false
    );
    List<UpgradeStep> updateDataSteps = new ArrayList<>();
    for (String processDefinitionKey : processDefinitionKeys) {
      updateDataSteps.add(new UpdateDataStep(new ProcessInstanceIndex(processDefinitionKey), query, script));
    }
    return updateDataSteps;
  }

  private UpgradeStep updateDefaultTenantIdOfProcessReportsInC8() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("defaultTenantId", ZEEBE_DEFAULT_TENANT_ID)
        .build()
    );
    // @formatter:off
    final String script = substitutor.replace(
      "for (definition in ctx._source.data.definitions) {\n" +
            "    definition.tenantIds = [\"${defaultTenantId}\"];\n" +
            "}\n");
    // @formatter:on
    return new UpdateDataStep(new SingleProcessReportIndex(), matchAllQuery(), script);
  }

  private UpgradeStep updateDefaultTenantIdOfCollectionScopesInC8() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("defaultTenantId", ZEEBE_DEFAULT_TENANT_ID)
        .build()
    );
    // @formatter:off
    final String script = substitutor.replace(
      "  for (scope in ctx._source.data.scope) {\n" +
        "    List tenants = new ArrayList();\n" +
        "    tenants.add(\"${defaultTenantId}\");\n" +
        "    scope.tenants = tenants;\n" +
        "}\n");
    // @formatter:on
    return new UpdateDataStep(new CollectionIndex(), matchAllQuery(), script);
  }

  private boolean isC8Instance(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    // <default> tenant migration should be applied to C8 data only. If zeebe is disabled, double check for imported C8
    // data in case upgrade is handled on non importer instance of Optimize
    return upgradeExecutionDependencies.getConfigurationService().getConfiguredZeebe().isEnabled()
      || isC8ImportDataPresent(upgradeExecutionDependencies)
      || isC8InstanceDataPresent(upgradeExecutionDependencies);
  }

  private boolean isC8ImportDataPresent(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    CountRequest countRequest = new CountRequest().indices(POSITION_BASED_IMPORT_INDEX_NAME);

    CountResponse countResponse;
    try {
      countResponse = upgradeExecutionDependencies.getEsClient().count(countRequest);
    } catch (IOException e) {
      final String reason = "Was not able to determine existence of imported C8 data.";
      log.error(reason, e);
      throw new UpgradeRuntimeException(reason, e);
    }
    return countResponse.getCount() > 0;
  }

  private boolean isC8InstanceDataPresent(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    CountRequest countRequest = new CountRequest()
      .query(boolQuery().must(termQuery(
        ProcessInstanceDto.Fields.dataSource + "." + DataSourceDto.Fields.type,
        ZEEBE_DATA_SOURCE
      )))
      .indices(PROCESS_INSTANCE_MULTI_ALIAS);

    CountResponse countResponse;
    try {
      countResponse = upgradeExecutionDependencies.getEsClient().count(countRequest);
    } catch (Exception e) {
      if (e instanceof ElasticsearchStatusException esStatusException && isInstanceIndexNotFoundException(esStatusException)) {
        return false;
      }
      final String reason = "Was not able to determine existence of imported C8 instances.";
      log.error(reason, e);
      throw new UpgradeRuntimeException(reason, e);
    }
    return countResponse.getCount() > 0;
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
