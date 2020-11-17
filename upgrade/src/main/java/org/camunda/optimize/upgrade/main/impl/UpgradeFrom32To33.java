/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateMappingIndexStep;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

public class UpgradeFrom32To33 extends UpgradeProcedure {

  public static final String FROM_VERSION = "3.2.0";
  public static final String TO_VERSION = "3.3.0";

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  @Override
  public UpgradePlan buildUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeSteps(markExistingDefinitionsAsNotDeleted())
      .addUpgradeStep(new UpdateMappingIndexStep(new SingleProcessReportIndex()))
      .addUpgradeStep(migrateDistributedByField(SINGLE_PROCESS_REPORT_INDEX_NAME))
      .addUpgradeStep(new UpdateIndexStep(new SingleDecisionReportIndex(), null))
      .addUpgradeStep(migrateDistributedByField(SINGLE_DECISION_REPORT_INDEX_NAME))
      .addUpgradeStep(migrateDashboardAvailableFilters())
      .addUpgradeStep(new UpdateMappingIndexStep(new EventIndex()))
      .build();
  }

  private UpgradeStep migrateDashboardAvailableFilters() {
    //@formatter:off
    final String script =
      "def currentFilters = ctx._source.availableFilters;\n" +
      "for (def currentFilter : currentFilters) {\n" +
      "  if (currentFilter.type == \'variable\' && currentFilter.data != null) {\n" +
      "    if (currentFilter.data.type == \'Boolean\' || currentFilter.data.type == \'Date\') {\n" +
      "      currentFilter.data.data = null;\n" +
      "    } else {\n" +
      "      currentFilter.data.data.allowCustomValues = false;\n" +
      "    }\n" +
      "  }" +
      "}\n";
    //@formatter:on
    return new UpdateDataStep(
      DASHBOARD_INDEX_NAME,
      matchAllQuery(),
      script
    );
  }

  private List<UpgradeStep> markExistingDefinitionsAsNotDeleted() {
    final String script = "ctx._source.deleted = false;";
    return Arrays.asList(
      new UpdateMappingIndexStep(new ProcessDefinitionIndex()),
      new UpdateMappingIndexStep(new DecisionDefinitionIndex()),
      new UpdateMappingIndexStep(new EventProcessDefinitionIndex()),
      new UpdateDataStep(PROCESS_DEFINITION_INDEX_NAME, matchAllQuery(), script),
      new UpdateDataStep(DECISION_DEFINITION_INDEX_NAME, matchAllQuery(), script),
      new UpdateDataStep(EVENT_PROCESS_DEFINITION_INDEX_NAME, matchAllQuery(), script)
    );
  }

  private UpgradeStep migrateDistributedByField(final String indexName) {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("distributeByField", "distributedBy")
        .build()
    );

    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put("distributeByNoneDto", new NoneDistributedByDto())
      .build();

    //@formatter:off
    final String script = substitutor.replace(
      "def data = ctx._source.data;\n" +
        "def configuration = data.configuration;\n" +
        "if(configuration.${distributeByField} == null) {\n" +
          "data.${distributeByField} = params.distributeByNoneDto;\n" +
        "} else {\n" +
          "data.${distributeByField} = configuration.${distributeByField};\n" +
        "}\n" +
        "configuration.remove(\"${distributeByField}\");\n"
    );
    //@formatter:on

    return new UpdateDataStep(
      indexName,
      matchAllQuery(),
      script,
      params
    );
  }
}
