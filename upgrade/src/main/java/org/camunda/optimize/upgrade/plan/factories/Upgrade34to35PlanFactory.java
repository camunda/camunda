/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.util.MappingMetadataUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.BpmnModelUtil.parseBpmnModel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

public class Upgrade34to35PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.4.0")
      .toVersion("3.5.0")
      .addUpgradeSteps(migrateProcessAndEventProcessDefinitionsUpdateFlowNodeNamesFieldToFlowNodeData(dependencies))
      .addUpgradeSteps(mergeUserTaskAndFlowNodeData(dependencies, true))
      .addUpgradeSteps(mergeUserTaskAndFlowNodeData(dependencies, false))
      .build();
  }

  private static List<UpgradeStep> migrateProcessAndEventProcessDefinitionsUpdateFlowNodeNamesFieldToFlowNodeData(
    UpgradeExecutionDependencies dependencies) {
    final Map<String, Object> processDefinitionIdsToFlowNodeDataProcessDefinition = getAllExistingDataForFlowNodes(
      ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME, dependencies);
    final Map<String, Object> processDefinitionIdsToFlowNodeDataEventProcessDefinition =
      getAllExistingDataForFlowNodes(
        ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME, dependencies);

    //@formatter:off
    String script =
      "ctx._source.flowNodeData = ((Map) params.get(ctx._source.id)).values();\n" +
      "ctx._source.remove(\"flowNodeNames\");\n";
    //@formatter:on

    return Arrays.asList(
      new UpdateIndexStep(
        new ProcessDefinitionIndex(),
        script,
        processDefinitionIdsToFlowNodeDataProcessDefinition,
        Collections.emptySet()
      ),
      new UpdateIndexStep(
        new EventProcessDefinitionIndex(),
        script,
        processDefinitionIdsToFlowNodeDataEventProcessDefinition,
        Collections.emptySet()
      )
    );
  }

  private static List<UpgradeStep> mergeUserTaskAndFlowNodeData(final UpgradeExecutionDependencies dependencies,
                                                                final boolean eventBased) {
    List<String> indexIdentifiers = MappingMetadataUtil.retrieveProcessInstanceIndexIdentifiers(
      dependencies.getEsClient(),
      eventBased
    );

    return indexIdentifiers.stream()
      .map(indexIdentifier ->
             new UpdateIndexStep(
               eventBased ? new EventProcessInstanceIndex(indexIdentifier) : new ProcessInstanceIndex(indexIdentifier),
               getMergeUserTaskFlowNodeMappingScript()
             ))
      .collect(toList());
  }

  private static String getMergeUserTaskFlowNodeMappingScript() {
    // @formatter:off
      return "" +
        "def flowNodeInstances = ctx._source.events;" +
        "def userTaskInstances = ctx._source.userTasks;" +

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
                  "flowNode.flowNodeInstanceId = userTask.activityInstanceId;" +
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

        "ctx._source.flowNodeInstances = flowNodeInstances;" +
        "ctx._source.remove(\"events\");" +
        "ctx._source.remove(\"userTasks\");";
      // @formatter:on
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
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
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
}
