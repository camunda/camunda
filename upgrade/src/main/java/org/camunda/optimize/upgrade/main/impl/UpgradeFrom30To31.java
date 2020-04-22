/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import lombok.SneakyThrows;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.events.CamundaEventService;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

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
    final UpgradePlanBuilder.AddUpgradeStepBuilder upgradeBuilder = UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(migrateAxisLabels(SINGLE_PROCESS_REPORT_INDEX_NAME))
      .addUpgradeStep(migrateAxisLabels(SINGLE_DECISION_REPORT_INDEX_NAME))
      .addUpgradeStep(migrateAxisLabels(COMBINED_REPORT_INDEX_NAME))
      .addUpgradeStep(migrateProcessReportDateVariableFilter())
      .addUpgradeStep(migrateDecisionReportDateVariableFilter());
    upgradeAllCamundaActivityIndexProcessStartEndActivityIds(upgradeBuilder);
    return upgradeBuilder.build();
  }

  private UpgradeStep migrateProcessReportDateVariableFilter() {
    //@formatter:off
    final String script =
      "if (ctx._source.data.filter != null) {\n" +
      "  for (filter in ctx._source.data.filter) {\n" +
      "    if (\"variable\".equalsIgnoreCase(filter.type) && \"Date\".equalsIgnoreCase(filter.data.type)) {\n" +
      "      filter.data.data.type = \"fixed\";\n" +
      "    }\n" +
      "  }\n" +
      "}\n"
      ;
    //@formatter:on
    return new UpdateDataStep(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      QueryBuilders.matchAllQuery(),
      script
    );
  }

  private UpgradeStep migrateDecisionReportDateVariableFilter() {
    //@formatter:off
    final String script =
      "if (ctx._source.data.filter != null) {\n" +
      "  for (filter in ctx._source.data.filter) {\n" +
      "    if (\"inputVariable\".equalsIgnoreCase(filter.type) || \"outputVariable\".equalsIgnoreCase(filter.type)\n" +
      "         && \"Date\".equalsIgnoreCase(filter.data.type)) {\n" +
      "      filter.data.data.type = \"fixed\";\n" +
      "    }\n" +
      "  }\n" +
      "}\n"
      ;
    //@formatter:on
    return new UpdateDataStep(
      SINGLE_DECISION_REPORT_INDEX_NAME,
      QueryBuilders.matchAllQuery(),
      script
    );
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

  @SneakyThrows
  private void upgradeAllCamundaActivityIndexProcessStartEndActivityIds(final UpgradePlanBuilder.AddUpgradeStepBuilder upgradeBuilder) {
    final GetAliasesResponse aliases = upgradeDependencies.getEsClient().getAlias(
      new GetAliasesRequest(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*"), RequestOptions.DEFAULT
    );
    aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetaData::alias))
      .map(fullAliasName -> fullAliasName.substring(fullAliasName.lastIndexOf(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX)))
      .forEach(indexName -> upgradeBuilder.addUpgradeStep(migrateProcessStartEndActivityIds(indexName)));
  }

  private UpgradeStep migrateProcessStartEndActivityIds(String indexName) {
    //@formatter:off
    final String script =
        "if (ctx._source.activityInstanceId == ctx._source.processDefinitionKey + \"_processInstanceEnd\") {\n" +
        "  ctx._source.activityInstanceId = ctx._source.processInstanceId + \"_processInstanceEnd\";\n" +
        "} else if (ctx._source.activityInstanceId == ctx._source.processDefinitionKey + \"_processInstanceStart\") {\n" +
        "  ctx._source.activityInstanceId = ctx._source.processInstanceId + \"_processInstanceStart\";\n" +
        "} else if (ctx._source.activityInstanceId == ctx._source.processInstanceId + \"_end\") {\n" +
        "  ctx._source.activityInstanceId = ctx._id + \"_end\";\n" +
        "} else if (ctx._source.activityInstanceId == ctx._source.processInstanceId + \"_start\") {\n" +
        "  ctx._source.activityInstanceId = ctx._id + \"_start\";\n" +
        "}\n"
      ;
    //@formatter:on
    final List<String> typesToMigrate = new ArrayList(CamundaEventService.SPLIT_START_END_MAPPED_TYPES);
    typesToMigrate.add(CamundaEventService.PROCESS_START_TYPE);
    typesToMigrate.add(CamundaEventService.PROCESS_END_TYPE);
    return new UpdateDataStep(
      indexName,
      boolQuery().must(termsQuery(CamundaActivityEventIndex.ACTIVITY_TYPE, typesToMigrate)),
      script
    );
  }

}
