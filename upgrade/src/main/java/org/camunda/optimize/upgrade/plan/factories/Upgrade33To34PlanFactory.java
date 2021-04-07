/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.commons.text.StringSubstitutor;
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
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.camunda.optimize.upgrade.steps.schema.ReindexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.ElasticsearchStatusException;
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
import static org.camunda.optimize.dto.optimize.importing.IdentityLinkLogOperationType.CLAIM_OPERATION_TYPE;
import static org.camunda.optimize.dto.optimize.importing.IdentityLinkLogOperationType.UNCLAIM_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE_OPERATIONS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
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
      .addUpgradeSteps(migrateAllDecisionInstancesToDedicatedIndices(existingDecisionKeys))
      .addUpgradeSteps(createDedicatedInstanceIndicesPerDefinition(PROCESS, existingProcessKeys))
      .addUpgradeSteps(migrateAllProcessInstancesToDedicatedIndicesAndRecalculateUserTaskDurations(existingProcessKeys))
      .addUpgradeSteps(addReadAliasesToEventInstancesIndices(existingEventIndexIdToKeyMap))
      // do delete as last step to ensure consistent steps are generated if upgrade is resumed on a previous step
      // failure
      .addUpgradeStep(deleteOldInstanceIndex(DECISION))
      .addUpgradeStep(deleteOldInstanceIndex(PROCESS))
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
      "if (ctx._source.data.view != null) {\n" +
      "  def reportEntityType = ctx._source.data.view.entity;\n" +
      "  def currentFilters = ctx._source.data.filter;\n" +
      "  if (reportEntityType == 'userTask' || reportEntityType == 'flowNode') {\n" +
      "    def executionState = ctx._source.data.configuration.flowNodeExecutionState;\n" +
      "    if (executionState == 'completed') {\n" +
      "      def newFilter = [\n" +
      "        'type': 'completedOrCanceledFlowNodesOnly',\n" +
      "        'filterLevel': 'view'\n" +
      "       ];" +
      "      newFilter.data = null;" +
      "      currentFilters.add(newFilter);\n" +
      "    } else if (executionState == 'running') {\n" +
      "      def newFilter = [\n" +
      "        'type': 'runningFlowNodesOnly',\n" +
      "        'filterLevel': 'view'\n" +
      "       ];" +
      "      newFilter.data = null;" +
      "     currentFilters.add(newFilter);\n" +
      "    } else if (executionState == 'canceled') {\n" +
      "      def newFilter = [\n" +
      "        'type': 'canceledFlowNodesOnly',\n" +
      "        'filterLevel': 'view'\n" +
      "       ];" +
      "      newFilter.data = null;" +
      "     currentFilters.add(newFilter);\n" +
      "    }\n" +
      "  }\n" +
      "}\n" +
      "if (ctx._source.data.configuration.flowNodeExecutionState != null) {\n" +
      "  ctx._source.data.configuration.remove(\"flowNodeExecutionState\");\n" +
      "}\n";
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
      "if (ctx._source.data.view != null) {\n" +
      "  def reportView = ctx._source.data.view;\n" +
      "  reportView.properties = [];\n" +
      "  if (reportView.property != null) {\n" +
      "    reportView.properties.add(reportView.property);\n" +
      "  }\n" +
      "  reportView.remove(\"property\");\n" +
      "}\n";
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
      "if (ctx._source.data.view != null) {\n" +
      "  def reportView = ctx._source.data.view;\n" +
      "  reportView.properties = [];\n" +
      "  if (reportView.property != null) {\n" +
      "    reportView.properties.add(reportView.property);\n" +
      "  }\n" +
      "  reportView.remove(\"property\");\n" +
      "}\n";
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

  private static List<UpgradeStep> migrateAllDecisionInstancesToDedicatedIndices(final Set<String> existingDefinitionKeys) {
    return existingDefinitionKeys.stream()
      .map(key -> new ReindexStep(
        getOldInstanceIndex(DECISION),
        getNewInstanceIndex(DECISION, key),
        boolQuery().must(termQuery(resolveDefinitionKeyFieldForType(DECISION), key))
      ))
      .collect(toList());
  }

  private static List<UpgradeStep> migrateAllProcessInstancesToDedicatedIndicesAndRecalculateUserTaskDurations(
    final Set<String> existingDefinitionKeys) {
    return existingDefinitionKeys.stream()
      .map(key -> new ReindexStep(
        getOldInstanceIndex(PROCESS),
        getNewInstanceIndex(PROCESS, key),
        boolQuery().must(termQuery(resolveDefinitionKeyFieldForType(PROCESS), key)),
        getDurationCalculationAndRemovalOfClaimDateScript()
      ))
      .collect(toList());
  }

  private static DeleteIndexIfExistsStep deleteOldInstanceIndex(final DefinitionType type) {
    return new DeleteIndexIfExistsStep(getOldInstanceIndex(type));
  }

  private static List<UpgradeStep> addReadAliasesToEventInstancesIndices(final Map<String, String> indexIdToKeyMap) {
    return indexIdToKeyMap.entrySet().stream()
      .map(entry -> new UpdateIndexStep(
        new EventProcessInstanceIndex(entry.getKey()),
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
    } catch (Exception e) {
      if (e instanceof ElasticsearchStatusException
        && isInstanceIndexNotFoundException((ElasticsearchStatusException) e)) {
        return Collections.emptySet();
      }
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

  /**
   * Note:
   * This is the same script as in AbstractUserTaskWriter.createUpdateUserTaskMetricsScript, but with one additional
   * line to remove the claimDate field on each userTask.
   */
  public static String getDurationCalculationAndRemovalOfClaimDateScript() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("userTasksField", USER_TASKS)
        .put("assigneeOperationsField", USER_TASK_ASSIGNEE_OPERATIONS)
        .put("startDateField", USER_TASK_START_DATE)
        .put("endDateField", USER_TASK_END_DATE)
        .put("idleDurationInMsField", USER_TASK_IDLE_DURATION)
        .put("workDurationInMsField", USER_TASK_WORK_DURATION)
        .put("totalDurationInMsField", USER_TASK_TOTAL_DURATION)
        .put("canceledField", USER_TASK_CANCELED)
        .put("operationTypeClaim", CLAIM_OPERATION_TYPE.getId())
        .put("operationTypeUnclaim", UNCLAIM_OPERATION_TYPE.getId())
        .put("dateFormatPattern", OPTIMIZE_DATE_FORMAT)
        .build()
    );

    // @formatter:off
    return substitutor.replace(
      "if (ctx._source.${userTasksField} != null) {\n" +
        "def dateFormatter = new SimpleDateFormat(\"${dateFormatPattern}\");\n" +

        "for (def currentTask : ctx._source.${userTasksField}) {\n" +
        "def totalWorkTimeInMs = 0;\n" +
        "def totalIdleTimeInMs = 0;\n" +
        "def workTimeHasChanged = false;\n" +
        "def idleTimeHasChanged = false;\n" +

        "if (currentTask.${assigneeOperationsField} != null && !currentTask.${assigneeOperationsField}.isEmpty()) {\n" +
        // Collect all timestamps of unclaim operations, counting the startDate as the first and the endDate as the last unclaim
        "def allUnclaimTimestamps = currentTask.${assigneeOperationsField}.stream()\n" +
        ".filter(operation -> \"${operationTypeUnclaim}\".equals(operation.operationType))\n" +
        ".map(operation -> operation.timestamp)\n" +
        ".map(dateFormatter::parse)" +
        ".collect(Collectors.toList());\n" +
        "Optional.ofNullable(currentTask.${startDateField})" +
        ".map(dateFormatter::parse)\n" +
        ".ifPresent(startDate -> allUnclaimTimestamps.add(startDate));\n" +
        "Optional.ofNullable(currentTask.${endDateField})" +
        ".map(dateFormatter::parse)\n" +
        ".ifPresent(endDate -> allUnclaimTimestamps.add(endDate));\n" +
        "allUnclaimTimestamps.sort(Comparator.naturalOrder());\n" +

        // Collect all timestamps of claim operations
        "def allClaimTimestamps = currentTask.${assigneeOperationsField}.stream()\n" +
        ".filter(operation -> \"${operationTypeClaim}\".equals(operation.operationType))\n" +
        ".map(operation -> operation.timestamp)\n" +
        ".map(dateFormatter::parse)\n" +
        ".sorted(Comparator.naturalOrder())\n" +
        ".collect(Collectors.toList());\n" +

        // Calculate idle time, which is the sum of differences between claim and unclaim timestamp pairs, ie (claim_n - unclaim_n)
        // Note there will always be at least one unclaim (startDate)
        "for(def i = 0; i < allUnclaimTimestamps.size() &&  i < allClaimTimestamps.size(); i++) {\n" +
        "def unclaimDate = allUnclaimTimestamps.get(i);\n" +
        "def claimDate= allClaimTimestamps.get(i);\n" +
        "def idleTimeToAdd = claimDate.getTime() - unclaimDate.getTime();\n" +
        "totalIdleTimeInMs = totalIdleTimeInMs + idleTimeToAdd;\n" +
        "idleTimeHasChanged = true;\n" +
        "}\n" +

        // Calculate work time, which is the sum of differences between unclaim and previous claim timestamp pairs, ie (unclaim_n+1 - claim_n)
        // Note the startDate is the first unclaim, so can be disregarded for this calculation
        "for(def i = 0; i < allUnclaimTimestamps.size() - 1 &&  i < allClaimTimestamps.size(); i++) {\n" +
        "def claimDate = allClaimTimestamps.get(i);\n" +
        "def unclaimDate = allUnclaimTimestamps.get(i + 1);\n" +
        "def workTimeToAdd = unclaimDate.getTime() - claimDate.getTime();\n" +
        "totalWorkTimeInMs = totalWorkTimeInMs + workTimeToAdd;\n" +
        "workTimeHasChanged = true;\n" +
        "}\n" +

        // Edge case: task was unclaimed and then completed without claim (== there are 2 more unclaims than claims)
        // --> add time between end and last "real" unclaim as idle time
        "if(allUnclaimTimestamps.size() - allClaimTimestamps.size() == 2) {\n" +
        "def lastUnclaim = allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 1);\n" +
        "def secondToLastUnclaim = allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 2);\n" +
        "totalIdleTimeInMs = totalIdleTimeInMs + (lastUnclaim.getTime() - secondToLastUnclaim.getTime());\n" +
        "idleTimeHasChanged = true;\n" +
        "}\n" +
        "}\n" +

        // Edge case: no assignee operations exist but task was finished (task was completed or canceled without claim)
        "else if(currentTask.${assigneeOperationsField}.isEmpty() && currentTask.${totalDurationInMsField} != null) {\n" +
        "def wasCanceled = Boolean.TRUE.equals(currentTask.${canceledField});\n" +
        "if(wasCanceled) {\n" +
        // Task was cancelled --> assumed to have been idle the entire time
        "totalIdleTimeInMs = currentTask.${totalDurationInMsField};\n" +
        "totalWorkTimeInMs = 0;\n" +
        "} else {\n" +
        // Task was not canceled --> assumed to have been worked on the entire time (presumably programmatically)
        "totalIdleTimeInMs = 0;\n" +
        "totalWorkTimeInMs = currentTask.${totalDurationInMsField};\n" +
        "}\n" +
        "workTimeHasChanged = true;\n" +
        "idleTimeHasChanged = true;\n" +
        "}\n" +

        // Set work and idle time if they have been calculated. Otherwise, leave fields null.
        "if(idleTimeHasChanged) {\n" +
        "currentTask.${idleDurationInMsField} = totalIdleTimeInMs;\n" +
        "}\n" +
        "if(workTimeHasChanged) {\n" +
        "currentTask.${workDurationInMsField} = totalWorkTimeInMs;\n" +
        "}\n" +

        // NOTE: The below line is the only difference between this and the script in AbstractUserTaskWriter
        "currentTask.remove(\"claimDate\");\n" +

        "}\n" +
        "}\n"
    );
    // @formatter:on
  }
}
