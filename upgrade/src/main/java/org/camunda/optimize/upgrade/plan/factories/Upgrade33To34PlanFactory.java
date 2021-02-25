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
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.plan.indices.DecisionInstanceIndexV4Old;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.camunda.optimize.upgrade.steps.schema.ReindexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class Upgrade33To34PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final OptimizeElasticsearchClient esClient) {
    final Set<String> existingDecisionKeys = getAllExistingDefinitionKeys(esClient, DECISION);
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.3.0")
      .toVersion("3.4.0")
      .addUpgradeStep(migrateSingleProcessReportV6())
      .addUpgradeStep(migrateSingleDecisionReportV6())
      .addUpgradeStep(migrateEventMappingEventSources())
      .addUpgradeStep(migrateEventPublishStateEventSources())
      .addUpgradeSteps(createDedicatedInstanceIndicesPerDecisionDefinition(existingDecisionKeys))
      .addUpgradeSteps(migrateAllDecisionInstancesToDedicatedIndices(existingDecisionKeys))
      .addUpgradeStep(deleteOldDecisionInstanceIndex())
      .build();
  }

  private static UpgradeStep migrateSingleProcessReportV6() {
    return new UpdateIndexStep(
      new SingleProcessReportIndex(),
      createMigrateFlowNodeStatusConfigToFiltersScript() + createProcessReportToMultiMeasureFieldsScript()
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

  private static UpgradeStep migrateSingleDecisionReportV6() {
    return new UpdateIndexStep(
      new SingleDecisionReportIndex(),
      //@formatter:off
        "def reportView = ctx._source.data.view;\n" +
        "reportView.properties = [];\n" +
        "if (reportView.property != null) {\n" +
        "  reportView.properties.add(reportView.property);\n" +
        "}\n" +
        "reportView.remove(\"property\");\n"
      //@formatter:on
    );
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

  private static List<UpgradeStep> createDedicatedInstanceIndicesPerDecisionDefinition(final Set<String> existingDecisionKeys) {
    return existingDecisionKeys.stream()
      .map(key -> new CreateIndexStep(new DecisionInstanceIndex(key), Sets.newHashSet(DECISION_INSTANCE_MULTI_ALIAS)))
      .collect(toList());
  }

  private static List<UpgradeStep> migrateAllDecisionInstancesToDedicatedIndices(final Set<String> existingDecisionKeys) {
    return existingDecisionKeys.stream()
      .map(key -> new ReindexStep(
        new DecisionInstanceIndexV4Old(),
        new DecisionInstanceIndex(key),
        boolQuery().must(termQuery(DECISION_DEFINITION_KEY, key))
      ))
      .collect(toList());
  }

  private static DeleteIndexIfExistsStep deleteOldDecisionInstanceIndex() {
    return new DeleteIndexIfExistsStep(new DecisionInstanceIndexV4Old());
  }

  private Set<String> getAllExistingDefinitionKeys(final OptimizeElasticsearchClient esClient,
                                                   final DefinitionType type) {
    final String defKeyAggName = "definitionKeyAggregation";
    final TermsAggregationBuilder definitionKeyAgg = AggregationBuilders
      .terms(defKeyAggName)
      .field(resolveDefinitionKeyFieldForType(type));
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
        return new String[]{PROCESS_INSTANCE_INDEX_NAME};
      case DECISION:
        return new String[]{new DecisionInstanceIndexV4Old().getIndexName()};
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private String resolveDefinitionKeyFieldForType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return ProcessInstanceDto.Fields.processDefinitionKey;
      case DECISION:
        return DecisionInstanceDto.Fields.decisionDefinitionKey;
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

}
