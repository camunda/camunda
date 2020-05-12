/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.importing.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.es.schema.index.events.EventTraceStateIndex;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.DeleteDataStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.Arrays;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_SEQUENCE_COUNT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_TRACE_STATE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;

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
      .addUpgradeStep(migrateDecisionReportDateVariableFilter())
      .addUpgradeStep(deleteDeprecatedDefinitionImportIndexDocument())
      .addUpgradeStep(migrateExcludedColumnsToNewVersionForProcessReports())
      .addUpgradeStep(migrateExcludedColumnsToNewVersionForDecisionReports());
    fixCamundaActivityEventActivityInstanceIdFields(upgradeBuilder);
    deleteCamundaTraceStateIndices(upgradeBuilder);
    deleteCamundaSequenceCountIndices(upgradeBuilder);
    upgradeBuilder.addUpgradeStep(deleteCamundaTraceStateImportIndexData());
    return upgradeBuilder.build();
  }

  private UpgradeStep migrateExcludedColumnsToNewVersionForProcessReports() {
    //@formatter:off
    final String script =
      "if (ctx._source.data.configuration != null) {\n" +
        "  def excludedColumns = ctx._source.data.configuration.excludedColumns;\n" +
        "  if (excludedColumns != null && !excludedColumns.isEmpty()) {\n" +
        "    excludedColumns = excludedColumns.stream()" +
        "                        .map(col -> col.replace(\"var__\", \"variable:\"))" +
        "                        .distinct()\n" +
        "                        .collect(Collectors.toList());\n" +
        "  }\n" +
        "  ctx._source.data.configuration.excludedColumns = excludedColumns;\n" +
        "}\n"
      ;
    //@formatter:on
    return new UpdateDataStep(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      QueryBuilders.matchAllQuery(),
      script
    );
  }

  private UpgradeStep migrateExcludedColumnsToNewVersionForDecisionReports() {
    //@formatter:off
    final String script =
      "if (ctx._source.data.configuration != null) {\n" +
        "  def excludedColumns = ctx._source.data.configuration.excludedColumns;\n" +
        "  if (excludedColumns != null && !excludedColumns.isEmpty()) {\n" +
        "    excludedColumns = excludedColumns.stream()" +
        "                        .map(col -> col.replace(\"inp__\", \"input:\"))" +
        "                        .map(col -> col.replace(\"out__\", \"output:\"))" +
        "                        .distinct()" +
        "                        .collect(Collectors.toList());\n" +
        "  }\n" +
        "  ctx._source.data.configuration.excludedColumns = excludedColumns;\n" +
        "}\n"
      ;
    //@formatter:on
    return new UpdateDataStep(
      SINGLE_DECISION_REPORT_INDEX_NAME,
      QueryBuilders.matchAllQuery(),
      script
    );
  }

  private UpgradeStep deleteDeprecatedDefinitionImportIndexDocument() {
    return new DeleteDataStep(
      IMPORT_INDEX_INDEX_NAME,
      QueryBuilders.idsQuery()
        .addIds(retrieveImportIndexDefinitionDocumentIds())
    );
  }

  private String[] retrieveImportIndexDefinitionDocumentIds() {
    try {
      SearchRequest searchRequest = new SearchRequest(IMPORT_INDEX_INDEX_NAME).source(
        new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())
      );
      final SearchResponse search = upgradeDependencies.getEsClient().search(searchRequest, RequestOptions.DEFAULT);
      return Arrays.stream(search.getHits().getHits())
        .map(SearchHit::getId)
        .filter(id -> id.startsWith(PROCESS_DEFINITION_INDEX_NAME) || id.startsWith(DECISION_DEFINITION_INDEX_NAME))
        .toArray(String[]::new);
    } catch (IOException e) {
      String errorMessage = "Optimize was not able to retrieve import index data from Elasticsearch!";
      throw new UpgradeRuntimeException(errorMessage, e);
    }
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
  private void deleteCamundaTraceStateIndices(final UpgradePlanBuilder.AddUpgradeStepBuilder upgradeBuilder) {
    final GetAliasesResponse aliases = upgradeDependencies.getEsClient().getAlias(
      new GetAliasesRequest(EVENT_TRACE_STATE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT
    );
    aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetaData::alias))
      .map(fullAliasName -> fullAliasName.substring(fullAliasName.lastIndexOf(EVENT_TRACE_STATE_INDEX_PREFIX) + EVENT_TRACE_STATE_INDEX_PREFIX
        .length()))
      .filter(indexSuffix -> !indexSuffix.equalsIgnoreCase(EXTERNAL_EVENTS_INDEX_SUFFIX))
      .forEach(indexSuffix -> upgradeBuilder.addUpgradeStep(new DeleteIndexIfExistsStep(new EventTraceStateIndex(
        indexSuffix))));
  }

  @SneakyThrows
  private void deleteCamundaSequenceCountIndices(final UpgradePlanBuilder.AddUpgradeStepBuilder upgradeBuilder) {
    final GetAliasesResponse aliases = upgradeDependencies.getEsClient().getAlias(
      new GetAliasesRequest(EVENT_SEQUENCE_COUNT_INDEX_PREFIX + "*"), RequestOptions.DEFAULT
    );
    aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetaData::alias))
      .map(fullAliasName -> fullAliasName.substring(fullAliasName.lastIndexOf(EVENT_SEQUENCE_COUNT_INDEX_PREFIX) + EVENT_SEQUENCE_COUNT_INDEX_PREFIX
        .length()))
      .filter(indexSuffix -> !indexSuffix.equalsIgnoreCase(EXTERNAL_EVENTS_INDEX_SUFFIX))
      .forEach(indexSuffix -> upgradeBuilder.addUpgradeStep(new DeleteIndexIfExistsStep(new EventSequenceCountIndex(
        indexSuffix))));
  }

  @SneakyThrows
  private void fixCamundaActivityEventActivityInstanceIdFields(final UpgradePlanBuilder.AddUpgradeStepBuilder upgradeBuilder) {
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

    final GetAliasesResponse aliases = upgradeDependencies.getEsClient().getAlias(
      new GetAliasesRequest(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*"), RequestOptions.DEFAULT
    );
    aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetaData::alias))
      .map(fullAliasName -> fullAliasName.substring(fullAliasName.lastIndexOf(CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX)))
      .forEach(indexName -> upgradeBuilder.addUpgradeStep(new UpdateDataStep(
        indexName,
        QueryBuilders.matchAllQuery(),
        script
      )));
  }

  @SneakyThrows
  private UpgradeStep deleteCamundaTraceStateImportIndexData() {
    return new DeleteDataStep(
      TIMESTAMP_BASED_IMPORT_INDEX_NAME,
      QueryBuilders.boolQuery()
        .must(QueryBuilders.prefixQuery(
          TimestampBasedImportIndexDto.Fields.esTypeIndexRefersTo,
          EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX
        ))
        .mustNot(QueryBuilders.termQuery(
          TimestampBasedImportIndexDto.Fields.esTypeIndexRefersTo,
          EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX
        ))
    );
  }

}
