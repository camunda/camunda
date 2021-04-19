/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
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

import static org.camunda.optimize.service.util.BpmnModelUtil.parseBpmnModel;

public class Upgrade34to35PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final OptimizeElasticsearchClient esClient) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.4.0")
      .toVersion("3.5.0")
      .addUpgradeSteps(migrateProcessAndEventProcessDefinitionsUpdateFlowNodeNamesFieldToFlowNodeData(esClient))
      .build();
  }

  private static List<UpgradeStep> migrateProcessAndEventProcessDefinitionsUpdateFlowNodeNamesFieldToFlowNodeData(OptimizeElasticsearchClient esClient) {
    final Map<String, Object> processDefinitionIdsToFlowNodeDataProcessDefinition = getAllExistingDataForFlowNodes(
      ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME,
      esClient
    );
    final Map<String, Object> processDefinitionIdsToFlowNodeDataEventProcessDefinition = getAllExistingDataForFlowNodes(
      ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME,
      esClient
    );

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

  private static Map<String, Object> getAllExistingDataForFlowNodes(final String indexName,
                                                                    final OptimizeElasticsearchClient esClient) {
    final SearchRequest searchRequest =
      new SearchRequest(indexName)
        .source(new SearchSourceBuilder().fetchSource(true));
    final SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new UpgradeRuntimeException(String.format(
        "Was not able to retrieve entries of index with name  %s", indexName), e);
    }
    Map<String, Object> flowNodeDataByDefinitionId = new HashMap<>();

    // get hits from elastic search query and convert them to JSON
    Arrays.stream(response.getHits().getHits())
      .map(hit -> (JSONObject) JSONValue.parse(hit.toString()))
      .map(jsonObject -> (JSONObject) JSONValue.parse(jsonObject.get("_source").toString()))
      .forEach(entry -> {
        final Object bpmnXml = entry.get(ProcessDefinitionOptimizeDto.Fields.bpmn20Xml);
        flowNodeDataByDefinitionId.put(
          entry.get(DefinitionOptimizeResponseDto.Fields.id).toString(),
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
