/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.optimize.dto.optimize.DataImportSourceType;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.data.DashboardStateFilterDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.util.MappingMetadataUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.service.util.BpmnModelUtil.parseBpmnModel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@Slf4j
public class Upgrade34to35PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.4.0")
      .toVersion("3.5.0")
      .addUpgradeSteps(migrateDefinitions(dependencies))
      .addUpgradeStep(migrateProcessReports())
      .addUpgradeStep(migrateDecisionReports())
      .addUpgradeStep(migrateDashboardFilters())
      .addUpgradeSteps(migrateProcessInstances(dependencies, true))
      .addUpgradeSteps(migrateProcessInstances(dependencies, false))
      .build();
  }

  private static List<UpgradeStep> migrateDefinitions(final UpgradeExecutionDependencies dependencies) {
    final Map<String, Object> processDefinitionIdsToFlowNodeDataProcessDefinition = getAllExistingDataForFlowNodes(
      ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME, dependencies);
    final Map<String, Object> processDefinitionIdsToFlowNodeDataEventProcessDefinition =
      getAllExistingDataForFlowNodes(
        ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME, dependencies);

    //@formatter:off
    String updateFlowNodeDatascript =
      // First part is to update the flow node data
      "ctx._source.flowNodeData = ((Map) params.get(ctx._source.id)).values();\n" +
      "ctx._source.remove(\"flowNodeNames\");\n";
      // Second part is to update the data import source data
    //@formatter:on

    return Arrays.asList(
      new UpdateIndexStep(
        new ProcessDefinitionIndex(),
        updateFlowNodeDatascript + getUpdateImportSourceScript(DataImportSourceType.ENGINE.getId()),
        processDefinitionIdsToFlowNodeDataProcessDefinition,
        Collections.emptySet()
      ),
      new UpdateIndexStep(
        new EventProcessDefinitionIndex(),
        updateFlowNodeDatascript + getUpdateImportSourceScript(DataImportSourceType.EVENTS.getId()),
        processDefinitionIdsToFlowNodeDataEventProcessDefinition,
        Collections.emptySet()
      ),
      new UpdateIndexStep(
        new DecisionDefinitionIndex(),
        getUpdateImportSourceScript(DataImportSourceType.ENGINE.getId())
      )
    );
  }

  private static String getUpdateImportSourceScript(final String importType) {
    //@formatter:off
    return
      "def dataSource = [\n" +
      "  'type': \"" + importType + "\",\n" +
      "  'name': ctx._source.engine\n" +
      "];\n" +
      "ctx._source.dataSource = dataSource;\n" +
      "ctx._source.remove(\"engine\");\n";
    //@formatter:on
  }

  private static List<UpgradeStep> migrateProcessInstances(final UpgradeExecutionDependencies dependencies,
                                                           final boolean eventBased) {
    final List<String> indexIdentifiers = MappingMetadataUtil.retrieveProcessInstanceIndexIdentifiers(
      dependencies.getEsClient(),
      eventBased
    );

    if (!eventBased && !indexIdentifiers.isEmpty()) {
      checkForAndLogIncompleteUserTasks(dependencies.getEsClient());
    }

    return indexIdentifiers.stream()
      .map(indexIdentifier ->
             new UpdateIndexStep(
               eventBased ? new EventProcessInstanceIndex(indexIdentifier) : new ProcessInstanceIndex(indexIdentifier),
               getMergeUserTaskFlowNodeMappingScript() + getDataSourceMigrationScript(eventBased)
             ))
      .collect(toList());
  }

  private static String getDataSourceMigrationScript(final boolean eventBased) {
    return eventBased ? getUpdateImportSourceScript(DataImportSourceType.EVENTS.getId())
      : getUpdateImportSourceScript(DataImportSourceType.ENGINE.getId());
  }

  private static UpgradeStep migrateDashboardFilters() {
    final Map<String, Object> params = new HashMap<>();
    params.put("stateFilterData", new DashboardStateFilterDataDto(null));
    params.put("dateFilterData", new DashboardDateFilterDataDto(null));

    final String updateScript = "" +
      "if(ctx._source.availableFilters != null) {\n" +
      " for(filter in ctx._source.availableFilters) {\n" +
      "   if(\"state\".equalsIgnoreCase(filter.type)) {\n" +
      "     filter.data = params.stateFilterData;" +
      "   } else if(\"startDate\".equalsIgnoreCase(filter.type) || \"endDate\".equalsIgnoreCase(filter.type)) {" +
      "     filter.data = params.dateFilterData;" +
      "   } else {\n" +
      "     filter.data.defaultValues = null;" +
      "   }\n  " +
      " }\n" +
      "}";
    return new UpdateDataStep(new DashboardIndex(), matchAllQuery(), updateScript, params);
  }

  private static String getMergeUserTaskFlowNodeMappingScript() {
    // @formatter:off
      return "" +
        "def flowNodeInstances = ctx._source.events;" +

        // UserTasks are filtered due to importing edge case:
        // UserTasks may not have an activityInstanceId due to import via IdentityLinkLog or CanceledUserTaskImport only.
        // If the userTask has no activityInstanceId, we cannot associate it with a flowNode which is why these tasks are
        // excluded from the migration and a warning is added to the logs so users can decide whether to reset their
        // importers to recover the lost data.
        "def userTaskInstances = ctx._source.userTasks.stream()" +
          ".filter(userTask -> userTask.activityInstanceId != null)" +
          ".collect(Collectors.toList());" +

        "for (flowNode in flowNodeInstances) {" +
          "flowNode.flowNodeInstanceId = flowNode.id;" +
          "flowNode.flowNodeId = flowNode.activityId;" +
          "flowNode.flowNodeType = flowNode.activityType;" +
          "flowNode.totalDurationInMs = flowNode.durationInMs;" +
          "flowNode.remove(\"id\");" +
          "flowNode.remove(\"activityId\");" +
          "flowNode.remove(\"activityType\");" +
          "flowNode.remove(\"durationInMs\");" +

          "if (flowNode.flowNodeType.equalsIgnoreCase(\"userTask\")) { " +
            "userTaskInstances.stream()" +
              ".filter(userTask -> userTask.activityInstanceId.equals(flowNode.flowNodeInstanceId))" +
              ".findFirst()" +
              ".ifPresent(" +
                "userTask -> {" +
                  "flowNode.userTaskInstanceId = userTask.id;" +
                  "flowNode.dueDate = userTask.dueDate;" +
                  "flowNode.deleteReason = userTask.deleteReason;" +
                  "flowNode.assignee = userTask.assignee;" +
                  "flowNode.candidateGroups = userTask.candidateGroups;" +
                  "flowNode.assigneeOperations = userTask.assigneeOperations;" +
                  "flowNode.candidateGroupOperations = userTask.candidateGroupOperations;" +
                  "flowNode.totalDurationInMs = userTask.totalDurationInMs;" +
                  "flowNode.idleDurationInMs = userTask.idleDurationInMs;" +
                  "flowNode.workDurationInMs = userTask.workDurationInMs;" +
                "}" +
              ");" +
            "userTaskInstances.removeIf(userTask -> userTask.activityInstanceId.equals(flowNode.flowNodeInstanceId));" +
          "}" +
        "}" +

        // Also add userTasks whose flowNodeInstance may not have been imported yet
        "for(userTask in userTaskInstances) {" +
          "userTask.flowNodeInstanceId = userTask.activityInstanceId;" +
          "userTask.userTaskInstanceId = userTask.id;" +
          "userTask.flowNodeId = userTask.activityId;" +
          "userTask.processInstanceId = ctx._source.processInstanceId;" +
          "userTask.flowNodeType = \"userTask\";" +
          "userTask.remove(\"id\");" +
          "userTask.remove(\"activityId\");" +
          "userTask.remove(\"activityInstanceId\");" +
          "userTask.remove(\"activityType\");" +
          "flowNodeInstances.add(userTask);" +
        "}" +

        // Remove userTask flowNodeInstances whose userTaskInstances have not been imported/migrated
        "flowNodeInstances.removeIf(" +
          "flowNode -> flowNode.flowNodeType.equalsIgnoreCase(\"userTask\") && flowNode.userTaskInstanceId == null" +
        ");" +

        "ctx._source.flowNodeInstances = flowNodeInstances;" +
        "ctx._source.remove(\"events\");" +
        "ctx._source.remove(\"userTasks\");";
      // @formatter:on
  }

  private static void checkForAndLogIncompleteUserTasks(final OptimizeElasticsearchClient esClient) {
    final long incompleteUserTaskCount = getIncompleteUserTaskCount(esClient);
    if (incompleteUserTaskCount > 0) {
      log.warn(
        String.format(
          "Process instance data includes %s incomplete userTasks, this can happen due to an unfinished userTask " +
            "import. This userTask data cannot be migrated and will be removed during migration, which will result in" +
            " small inaccuracies in Optimize userTask data. Please refer to Optimize migration notes for more details" +
            " and for instructions on how to resolve this issue after the migration has finished:%n" +
            "https://docs.camunda.org/optimize/latest/technical-guide/update/3.4-to-3.5/",
          incompleteUserTaskCount
        )
      );
    }
  }

  private static long getIncompleteUserTaskCount(final OptimizeElasticsearchClient esClient) {
    final String countAggName = "incompleteUserTaskCount";
    final String filterAggName = "filterAggName";
    final String nestedAggName = "nestedAgg";

    AggregationBuilder countAgg = AggregationBuilders
      .count(countAggName)
      .field("userTasks.id");

    countAgg = nested(nestedAggName, "userTasks")
      .subAggregation(
        wrapWithFilterLimitedParentAggregation(
          filterAggName,
          boolQuery()
            .must(existsQuery("userTasks.id"))
            .mustNot(existsQuery("userTasks.activityInstanceId")),
          countAgg
        ));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .fetchSource(false)
      .aggregation(countAgg)
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_MULTI_ALIAS).source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest);
    } catch (IOException e) {
      throw new UpgradeRuntimeException("Could not retrieve count of incomplete userTasks.", e);
    }

    final Aggregations unnestedAggs = ((Nested) response.getAggregations().get(nestedAggName)).getAggregations();
    final Optional<Aggregations> unwrappedAggs = unwrapFilterLimitedAggregations(filterAggName, unnestedAggs);
    final ValueCount count = unwrappedAggs.orElse(unnestedAggs).get(countAggName);
    return count.getValue();
  }

  private static Map<String, Object> getAllExistingDataForFlowNodes(final String indexName,
                                                                    final UpgradeExecutionDependencies dependencies) {
    final OptimizeElasticsearchClient esClient = dependencies.getEsClient();
    final SearchRequest searchRequest =
      new SearchRequest(indexName)
        .source(new SearchSourceBuilder().query(matchAllQuery()).size(LIST_FETCH_LIMIT))
        .scroll(timeValueSeconds(dependencies.getConfigurationService().getEsScrollTimeoutInSeconds()));
    final SearchResponse response;
    try {
      response = esClient.search(searchRequest);
    } catch (IOException e) {
      throw new UpgradeRuntimeException(String.format(
        "Was not able to retrieve entries of index with name  %s", indexName), e);
    }

    final List<Map> existingDefinitionMaps = ElasticsearchReaderUtil.retrieveAllScrollResults(
      response,
      Map.class,
      dependencies.getObjectMapper(),
      esClient,
      dependencies.getConfigurationService().getEsScrollTimeoutInSeconds()
    );
    Map<String, Object> flowNodeDataByDefinitionId = new HashMap<>();

    existingDefinitionMaps
      .forEach(definition -> {
        final Object bpmnXml = definition.get(ProcessDefinitionOptimizeDto.Fields.bpmn20Xml);
        flowNodeDataByDefinitionId.put(
          definition.get(DefinitionOptimizeResponseDto.Fields.id).toString(),
          Optional.ofNullable(bpmnXml)
            .map(xml -> extractFlowNodeIdToFlowNodeData(xml.toString()))
            .orElse(Collections.emptyMap())
        );
      });
    return flowNodeDataByDefinitionId;
  }

  private static Map<String, FlowNodeDataDto> extractFlowNodeIdToFlowNodeData(final String bpmn20Xml) {
    BpmnModelInstance model = parseBpmnModel(bpmn20Xml);
    final Map<String, FlowNodeDataDto> result = new HashMap<>();
    for (FlowNode node : model.getModelElementsByType(FlowNode.class)) {
      FlowNodeDataDto flowNode = new FlowNodeDataDto(node.getId(), node.getName(), node.getElementType().getTypeName());
      result.put(node.getId(), flowNode);
    }
    return result;
  }

  private UpgradeStep migrateProcessReports() {
    final String script =
      //@formatter:off
      "ctx._source.data.definitions = [];\n" +
      "String definitionKey = ctx._source.data.processDefinitionKey;\n" +
      "String appliedToValue = \"all\";\n" +
      "if (definitionKey != null && !\"\".equals(definitionKey)) {\n" +
      "  String identifier = UUID.randomUUID().toString();\n" +
      "  ctx._source.data.definitions.add([\n" +
      "    \"identifier\" : identifier,\n" +
      "    \"key\" : definitionKey,\n" +
      "    \"name\" : ctx._source.data.processDefinitionName,\n" +
      "    \"displayName\" : null,\n" +
      "    \"versions\" : ctx._source.data.processDefinitionVersions,\n" +
      "    \"tenantIds\" : ctx._source.data.tenantIds != null ? ctx._source.data.tenantIds : Collections.singletonList(null)\n" +
      "  ]);\n" +
      "  appliedToValue = identifier;\n" +
      "}\n" +
      "if (ctx._source.data.filter != null) {\n" +
      "  ctx._source.data.filter.forEach(filter -> filter.appliedTo = [appliedToValue]);\n" +
      "}\n" +
      "ctx._source.data.remove(\"processDefinitionKey\");\n" +
      "ctx._source.data.remove(\"processDefinitionName\");\n" +
      "ctx._source.data.remove(\"processDefinitionVersions\");\n" +
      "ctx._source.data.remove(\"tenantIds\");\n";
    //@formatter:on
    return new UpdateIndexStep(new SingleProcessReportIndex(), script);
  }

  private UpgradeStep migrateDecisionReports() {
    final String script =
      //@formatter:off
      "ctx._source.data.definitions = [];\n" +
      "String definitionKey = ctx._source.data.decisionDefinitionKey;\n" +
      "String appliedToValue = \"all\";\n" +
      "if (definitionKey != null && !\"\".equals(definitionKey)) {\n" +
      "  String identifier = UUID.randomUUID().toString();\n" +
      "  ctx._source.data.definitions.add([\n" +
      "    \"identifier\" : identifier,\n" +
      "    \"key\" : definitionKey,\n" +
      "    \"name\" : ctx._source.data.decisionDefinitionName,\n" +
      "    \"displayName\" : null,\n" +
      "    \"versions\" : ctx._source.data.decisionDefinitionVersions,\n" +
      "    \"tenantIds\" : ctx._source.data.tenantIds != null ? ctx._source.data.tenantIds : Collections.singletonList(null)\n" +
      "  ]);\n" +
      "  appliedToValue = identifier;\n" +
      "}\n" +
      "if (ctx._source.data.filter != null) {\n" +
      "  ctx._source.data.filter.forEach(filter -> filter.appliedTo = [appliedToValue]);\n" +
      "}\n" +
      "ctx._source.data.remove(\"decisionDefinitionKey\");\n" +
      "ctx._source.data.remove(\"decisionDefinitionName\");\n" +
      "ctx._source.data.remove(\"decisionDefinitionVersions\");\n" +
      "ctx._source.data.remove(\"tenantIds\");\n";
    //@formatter:on
    return new UpdateIndexStep(new SingleDecisionReportIndex(), script);
  }

}
