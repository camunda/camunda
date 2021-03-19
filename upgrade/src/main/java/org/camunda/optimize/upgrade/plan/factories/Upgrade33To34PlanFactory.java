/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import com.google.common.collect.Sets;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.plan.indices.DecisionInstanceIndexV4Old;
import org.camunda.optimize.upgrade.plan.indices.EventProcessInstanceIndexV5Old;
import org.camunda.optimize.upgrade.plan.indices.ProcessInstanceIndexV5Old;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.AddAliasStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.camunda.optimize.upgrade.steps.schema.ReindexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class Upgrade33To34PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final OptimizeElasticsearchClient esClient) {
    final Set<String> existingDecisionKeys = getAllExistingDefinitionKeys(esClient, DECISION);
    final Set<String> existingProcessKeys = getAllExistingDefinitionKeys(esClient, PROCESS);
    final Map<String, String> existingEventIndexIdToKeyMap =
      getAllExistingEventBasedIndexIdToKey(esClient, getAllEventProcessIndexIds(esClient));
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.3.0")
      .toVersion("3.4.0")
      .addUpgradeStep(migrateSingleProcessReportV6())
      .addUpgradeStep(migrateSingleDecisionReportV6())
      .addUpgradeStep(migrateEventMappingEventSources())
      .addUpgradeStep(migrateEventPublishStateEventSources())
      .addUpgradeSteps(createDedicatedInstanceIndicesPerDefinition(DECISION, existingDecisionKeys))
      .addUpgradeSteps(migrateAllInstancesToDedicatedIndices(DECISION, existingDecisionKeys))
      .addUpgradeStep(deleteOldInstanceIndex(DECISION))
      .addUpgradeSteps(createDedicatedInstanceIndicesPerDefinition(PROCESS, existingProcessKeys))
      .addUpgradeSteps(migrateAllInstancesToDedicatedIndices(PROCESS, existingProcessKeys))
      .addUpgradeStep(deleteOldInstanceIndex(PROCESS))
      .addUpgradeSteps(upgradeAllEventProcessInstanceIndices(existingEventIndexIdToKeyMap.keySet()))
      .addUpgradeSteps(addReadAliasesToEventInstancesIndices(existingEventIndexIdToKeyMap))
      .build();
  }

  private static UpgradeStep migrateSingleProcessReportV6() {
    return new UpdateIndexStep(
      new SingleProcessReportIndex(),
      createMigrateFlowNodeStatusConfigToFiltersScript()
        + createProcessReportToMultiMeasureFieldsScript()
        + createProcessReportColumnOrderConfigScript()
    );
  }

  private static String createMigrateFlowNodeStatusConfigToFiltersScript() {
    //@formatter:off
    return
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
  }

  private static String createProcessReportToMultiMeasureFieldsScript() {
    //@formatter:off
    return
      "def reportConfiguration = ctx._source.data.configuration;\n" +
      "reportConfiguration.aggregationTypes = [];\n" +
      "if (reportConfiguration.aggregationType != null) {\n" +
      "  reportConfiguration.aggregationTypes.add(reportConfiguration.aggregationType);\n" +
      "}\n" +
      "reportConfiguration.remove(\"aggregationType\");\n" +
      "reportConfiguration.userTaskDurationTimes = [];\n" +
      "if (reportConfiguration.userTaskDurationTime != null) {\n" +
      "  reportConfiguration.userTaskDurationTimes.add(reportConfiguration.userTaskDurationTime);\n" +
      "}\n" +
      "reportConfiguration.remove(\"userTaskDurationTime\");\n" +
      "def reportView = ctx._source.data.view;\n" +
      "reportView.properties = [];\n" +
      "if (reportView.property != null) {\n" +
      "  reportView.properties.add(reportView.property);\n" +
      "}\n" +
      "reportView.remove(\"property\");\n";
    //@formatter:on
  }

  private static String createProcessReportColumnOrderConfigScript() {
    //@formatter:off
    return
      "def configuration = ctx._source.data.configuration;\n" +
      "configuration.tableColumns.columnOrder = [];\n" +
      "if (configuration.columnOrder != null) {\n" +
      "  configuration.tableColumns.columnOrder.addAll(configuration.columnOrder.instanceProps);\n" +
      "  configuration.tableColumns.columnOrder.addAll(configuration.columnOrder.variables);\n" +
      "}\n" +
      "configuration.remove(\"columnOrder\");\n"
      ;
    //@formatter:on
  }

  private static UpgradeStep migrateSingleDecisionReportV6() {
    return new UpdateIndexStep(
      new SingleDecisionReportIndex(),
      createDecisionReportViewScript() + createDecisionReportColumnOrderConfigScript()
    );
  }

  private static String createDecisionReportViewScript() {
    //@formatter:off
    return
      "def reportView = ctx._source.data.view;\n" +
      "reportView.properties = [];\n" +
      "if (reportView.property != null) {\n" +
      "  reportView.properties.add(reportView.property);\n" +
      "}\n" +
      "reportView.remove(\"property\");\n";
    //@formatter:on
  }

  private static String createDecisionReportColumnOrderConfigScript() {
    //@formatter:off
    return
      "def configuration = ctx._source.data.configuration;\n" +
      "configuration.tableColumns.columnOrder = [];\n" +
      "if (configuration.columnOrder != null) {\n" +
      "  configuration.tableColumns.columnOrder.addAll(configuration.columnOrder.instanceProps);\n" +
      "  configuration.tableColumns.columnOrder.addAll(configuration.columnOrder.inputVariables);\n" +
      "  configuration.tableColumns.columnOrder.addAll(configuration.columnOrder.outputVariables);\n" +
      "}\n" +
      "configuration.remove(\"columnOrder\");\n";
    //@formatter:on
  }

  private static UpgradeStep migrateEventMappingEventSources() {
    //@formatter:off
    final String script =
      "ctx._source.eventSources.forEach(eventSource -> {\n" +
      "  if (eventSource.type == 'external') {\n" +
      "    def sourceConfig = [\n" +
      "      'includeAllGroups': true,\n" +
      "      'group': null,\n" +
      "      'eventScope': eventSource.eventScope\n" +
      "     ];\n" +
      "    eventSource.configuration = sourceConfig;\n" +
      "  } else if (eventSource.type == 'camunda') {\n" +
      "    def sourceConfig = [\n" +
      "      'eventScope': eventSource.eventScope,\n" +
      "      'processDefinitionKey': eventSource.processDefinitionKey,\n" +
      "      'processDefinitionName': null,\n" +
      "      'versions': eventSource.versions,\n" +
      "      'tenants': eventSource.tenants,\n" +
      "      'tracedByBusinessKey': eventSource.tracedByBusinessKey,\n" +
      "      'traceVariable': eventSource.traceVariable\n" +
      "     ];\n" +
      "    eventSource.configuration = sourceConfig;\n" +
      "  }\n" +
      "  eventSource.remove(\"processDefinitionKey\");\n" +
      "  eventSource.remove(\"versions\");\n" +
      "  eventSource.remove(\"tenants\");\n" +
      "  eventSource.remove(\"tracedByBusinessKey\");\n" +
      "  eventSource.remove(\"traceVariable\");\n" +
      "  eventSource.remove(\"eventScope\");\n" +
      "})\n";
    //@formatter:on
    return new UpdateIndexStep(new EventProcessMappingIndex(), script);
  }

  private static UpgradeStep migrateEventPublishStateEventSources() {
    //@formatter:off
    final String script =
      "ctx._source.eventImportSources.forEach(eventImportSource -> {\n" +
      "  def existingEventSource = eventImportSource.eventSource;\n" +
      "  def eventSourceConfigs = new ArrayList();\n" +
      "  if (existingEventSource.type == 'external') {\n" +
      "    def sourceConfig = [\n" +
      "      'includeAllGroups': true,\n" +
      "      'group': null,\n" +
      "      'eventScope': existingEventSource.eventScope\n" +
      "     ];\n" +
      "     eventSourceConfigs.add(sourceConfig);\n" +
      "  } else if (existingEventSource.type == 'camunda') {\n" +
      "    def sourceConfig = [\n" +
      "      'eventScope': existingEventSource.eventScope,\n" +
      "      'processDefinitionKey': existingEventSource.processDefinitionKey,\n" +
      "      'processDefinitionName': null,\n" +
      "      'versions': existingEventSource.versions,\n" +
      "      'tenants': existingEventSource.tenants,\n" +
      "      'tracedByBusinessKey': existingEventSource.tracedByBusinessKey,\n" +
      "      'traceVariable': existingEventSource.traceVariable\n" +
      "     ];\n" +
      "     eventSourceConfigs.add(sourceConfig);\n" +
      "  }\n" +
      "  eventImportSource.eventImportSourceType = existingEventSource.type;\n" +
      "  eventImportSource.eventSourceConfigurations = eventSourceConfigs;\n" +
      "  eventImportSource.remove(\"eventSource\");\n" +
      "})\n";
    //@formatter:on
    return new UpdateIndexStep(new EventProcessPublishStateIndex(), script);
  }

  private static List<UpgradeStep> createDedicatedInstanceIndicesPerDefinition(final DefinitionType type,
                                                                               final Set<String> existingDefinitionKeys) {
    return existingDefinitionKeys.stream()
      .map(key -> new CreateIndexStep(getNewInstanceIndex(type, key), Sets.newHashSet(getNewReadMultiAlias(type))))
      .collect(toList());
  }

  private static List<UpgradeStep> migrateAllInstancesToDedicatedIndices(final DefinitionType type,
                                                                         final Set<String> existingDefinitionKeys) {
    return existingDefinitionKeys.stream()
      .map(key -> new ReindexStep(
        getOldInstanceIndex(type),
        getNewInstanceIndex(type, key),
        boolQuery().must(termQuery(resolveDefinitionKeyFieldForType(type), key))
      ))
      .collect(toList());
  }

  private static DeleteIndexIfExistsStep deleteOldInstanceIndex(final DefinitionType type) {
    return new DeleteIndexIfExistsStep(getOldInstanceIndex(type));
  }

  private static List<UpgradeStep> upgradeAllEventProcessInstanceIndices(final Set<String> existingIndexIds) {
    // upgrade all event process indices because the process instance index version has increased
    return existingIndexIds.stream()
      .map(id -> new UpdateIndexStep(new EventProcessInstanceIndex(id)))
      .collect(toList());
  }

  private static List<UpgradeStep> addReadAliasesToEventInstancesIndices(final Map<String, String> indexIdToKeyMap) {
    return indexIdToKeyMap.entrySet().stream()
      .map(entry -> new AddAliasStep(
        new EventProcessInstanceIndex(entry.getKey()),
        false,
        Sets.newHashSet(PROCESS_INSTANCE_MULTI_ALIAS, getProcessInstanceIndexAliasName(entry.getValue()))
      ))
      .collect(toList());
  }

  private Map<String, String> getAllExistingEventBasedIndexIdToKey(final OptimizeElasticsearchClient esClient,
                                                                   final Set<String> eventProcessIndexIds) {
    final Map<String, String> indexIdByKeyMap = new HashMap<>();
    final String defKeyAggName = "definitionKeyAggregation";
    final TermsAggregationBuilder definitionKeyAgg = AggregationBuilders
      .terms(defKeyAggName)
      .field(ProcessInstanceDto.Fields.processDefinitionKey)
      .size(MAX_RESPONSE_SIZE_LIMIT);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().fetchSource(false).size(0);
    searchSourceBuilder.aggregation(definitionKeyAgg);

    for (String indexId : eventProcessIndexIds) {
      final SearchRequest searchRequest =
        new SearchRequest(new EventProcessInstanceIndexV5Old(indexId).getIndexName()).source(searchSourceBuilder);
      final SearchResponse response;
      try {
        response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      } catch (IOException e) {
        throw new UpgradeRuntimeException("Was not able to retrieve definition keys for event based instances", e);
      }
      final Terms definitionKeyTerms = response.getAggregations().get(defKeyAggName);
      final List<String> definitionKeyList = definitionKeyTerms.getBuckets().stream()
        .map(MultiBucketsAggregation.Bucket::getKeyAsString)
        .collect(toList());
      if (definitionKeyList.isEmpty()) {
        return Collections.emptyMap();
      }
      indexIdByKeyMap.put(indexId, definitionKeyList.get(0));
    }

    return indexIdByKeyMap;
  }

  private Set<String> getAllEventProcessIndexIds(final OptimizeElasticsearchClient esClient) {
    final GetAliasesResponse aliases;
    try {
      aliases = esClient.getAlias(
        new GetAliasesRequest(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT
      );
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed retrieving aliases for event process index prefix.");
    }
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(aliasMetaDataPerIndex -> aliasMetaDataPerIndex.stream().map(AliasMetadata::alias))
      .map(fullAliasName ->
             fullAliasName.substring(
               fullAliasName.lastIndexOf(EVENT_PROCESS_INSTANCE_INDEX_PREFIX)
                 + EVENT_PROCESS_INSTANCE_INDEX_PREFIX.length()
             ))
      .collect(toSet());
  }

  private Set<String> getAllExistingDefinitionKeys(final OptimizeElasticsearchClient esClient,
                                                   final DefinitionType type) {
    final String defKeyAggName = "definitionKeyAggregation";
    final TermsAggregationBuilder definitionKeyAgg = AggregationBuilders
      .terms(defKeyAggName)
      .field(resolveDefinitionKeyFieldForType(type))
      .size(MAX_RESPONSE_SIZE_LIMIT);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().fetchSource(false).size(0);
    searchSourceBuilder.aggregation(definitionKeyAgg);

    final SearchRequest searchRequest =
      new SearchRequest(resolveIndexNameForType(type)).source(searchSourceBuilder);

    final SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new UpgradeRuntimeException(String.format("Was not able to retrieve instances of type %s", type), e);
    }
    final Terms definitionKeyTerms = response.getAggregations().get(defKeyAggName);
    return definitionKeyTerms.getBuckets().stream()
      .map(MultiBucketsAggregation.Bucket::getKeyAsString)
      .collect(toSet());
  }

  private String[] resolveIndexNameForType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return new String[]{new ProcessInstanceIndexV5Old().getIndexName()};
      case DECISION:
        return new String[]{new DecisionInstanceIndexV4Old().getIndexName()};
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private static String resolveDefinitionKeyFieldForType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return ProcessInstanceDto.Fields.processDefinitionKey;
      case DECISION:
        return DecisionInstanceDto.Fields.decisionDefinitionKey;
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private static IndexMappingCreator getOldInstanceIndex(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return new ProcessInstanceIndexV5Old();
      case DECISION:
        return new DecisionInstanceIndexV4Old();
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private static IndexMappingCreator getNewInstanceIndex(final DefinitionType type,
                                                         final String definitionKey) {
    switch (type) {
      case PROCESS:
        return new ProcessInstanceIndex(definitionKey);
      case DECISION:
        return new DecisionInstanceIndex(definitionKey);
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private static String getNewReadMultiAlias(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return PROCESS_INSTANCE_MULTI_ALIAS;
      case DECISION:
        return DECISION_INSTANCE_MULTI_ALIAS;
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

}
