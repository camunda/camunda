/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es.reader;

import static io.zeebe.tasklist.util.ElasticsearchUtil.scroll;
import static java.util.stream.Collectors.groupingBy;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.FlowNodeInstanceEntity;
import io.zeebe.tasklist.entities.VariableEntity;
import io.zeebe.tasklist.es.schema.templates.FlowNodeInstanceTemplate;
import io.zeebe.tasklist.es.schema.templates.VariableTemplate;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.util.CollectionUtil;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.util.Pair;
import io.zeebe.tasklist.webapp.graphql.entity.VariableDTO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableReader.class);
  private static final String ABSENT_PARENT_ID = "-1";

  @Autowired private RestHighLevelClient esClient;
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  @Autowired private VariableTemplate variableTemplate;
  @Autowired private ObjectMapper objectMapper;

  public List<List<VariableDTO>> getVariablesByTaskIds(List<GetVariablesRequest> requests) {

    // build flow node trees (for each workflow instance)
    final Map<String, FlowNodeTree> flowNodeTrees = buildFlowNodeTrees(requests);

    // build local variable map  (for each flow node instance)
    final List<String> flowNodeInstanceIds =
        flowNodeTrees.values().stream()
            .flatMap(f -> f.getFlowNodeInstanceIds().stream())
            .collect(Collectors.toList());
    final Map<String, VariableMap> variableMaps = buildVariableMaps(flowNodeInstanceIds);

    return buildResponse(flowNodeTrees, variableMaps, requests);
  }

  /**
   * Builds lists of variables taking into account nested scopes.
   *
   * @param flowNodeTrees
   * @param variableMaps
   * @param requests
   * @return
   */
  private List<List<VariableDTO>> buildResponse(
      final Map<String, FlowNodeTree> flowNodeTrees,
      final Map<String, VariableMap> variableMaps,
      final List<GetVariablesRequest> requests) {

    final List<List<VariableDTO>> response = new ArrayList<>();

    for (GetVariablesRequest req : requests) {
      final FlowNodeTree flowNodeTree = flowNodeTrees.get(req.getWorkflowInstanceId());

      final VariableMap resultingVariableMap = new VariableMap();

      accumulateVariables(
          resultingVariableMap, variableMaps, flowNodeTree, req.getFlowNodeInstanceId());

      response.add(
          resultingVariableMap.entrySet().stream()
              .sorted(Comparator.comparing(Map.Entry::getKey))
              .collect(
                  Collector.of(
                      () -> new ArrayList<>(),
                      (list, entry) ->
                          list.add(
                              new VariableDTO().setName(entry.getKey()).setValue(entry.getValue())),
                      (list1, list2) -> {
                        list1.addAll(list2);
                        return list1;
                      })));
    }
    return response;
  }

  private void accumulateVariables(
      VariableMap resultingVariableMap,
      final Map<String, VariableMap> variableMaps,
      final FlowNodeTree flowNodeTree,
      final String flowNodeInstanceId) {
    final VariableMap m = variableMaps.get(flowNodeInstanceId);
    if (m != null) {
      resultingVariableMap.putAll(m);
    }
    final String parentFlowNodeId = flowNodeTree.getParent(flowNodeInstanceId);
    if (parentFlowNodeId != null && !parentFlowNodeId.equals(ABSENT_PARENT_ID)) {
      accumulateVariables(resultingVariableMap, variableMaps, flowNodeTree, parentFlowNodeId);
    }
  }

  private List<VariableEntity> getVariables(List<String> flowNodeInstanceIds) {
    final TermsQueryBuilder workflowInstanceKeyQuery =
        termsQuery(VariableTemplate.SCOPE_FLOW_NODE_ID, flowNodeInstanceIds);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(variableTemplate)
            .source(new SearchSourceBuilder().query(constantScoreQuery(workflowInstanceKeyQuery)));
    try {
      return scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

  private List<FlowNodeInstanceEntity> getFlowNodeInstances(
      final List<String> workflowInstanceIds) {
    final TermsQueryBuilder workflowInstanceKeyQuery =
        termsQuery(FlowNodeInstanceTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceIds);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(workflowInstanceKeyQuery))
                    .sort(FlowNodeInstanceTemplate.POSITION, SortOrder.ASC));
    try {
      return scroll(searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all flow nodes: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

  /**
   * Builds variable map for each flow node instance id: "local" variables for each flow node
   * instance.
   *
   * @param flowNodeInstanceIds
   * @return
   */
  private Map<String, VariableMap> buildVariableMaps(List<String> flowNodeInstanceIds) {
    // get list of all variables
    final List<VariableEntity> variables = getVariables(flowNodeInstanceIds);

    return variables.stream()
        .collect(
            groupingBy(
                VariableEntity::getScopeFlowNodeId,
                Collector.of(
                    () -> new VariableMap(),
                    (map, var) -> map.put(var.getName(), var.getValue()),
                    (map1, map2) -> {
                      map1.putAll(map2);
                      return map1;
                    })));
  }

  /**
   * Builds flow node tree for each requested workflow instance id.
   *
   * @param requests
   * @return map of flow node trees per workflow instance id
   */
  private Map<String, FlowNodeTree> buildFlowNodeTrees(List<GetVariablesRequest> requests) {
    final List<String> workflowInstanceIds =
        CollectionUtil.map(requests, GetVariablesRequest::getWorkflowInstanceId);
    // get all flow node instances for all workflow instance ids
    final List<FlowNodeInstanceEntity> flowNodeInstances =
        getFlowNodeInstances(workflowInstanceIds);

    final Map<String, FlowNodeTree> flowNodeTrees = new HashMap<>();
    for (FlowNodeInstanceEntity flowNodeInstance : flowNodeInstances) {
      getFlowNodeTree(flowNodeTrees, flowNodeInstance.getWorkflowInstanceId())
          .setParent(flowNodeInstance.getId(), flowNodeInstance.getParentFlowNodeId());
    }
    return flowNodeTrees;
  }

  private FlowNodeTree getFlowNodeTree(
      Map<String, FlowNodeTree> flowNodeTrees, String workflowInstanceId) {
    if (flowNodeTrees.get(workflowInstanceId) == null) {
      flowNodeTrees.put(workflowInstanceId, new FlowNodeTree());
    }
    return flowNodeTrees.get(workflowInstanceId);
  }

  static class FlowNodeTree extends HashMap<String, String> {

    public String getParent(String currentFlowNodeInstanceId) {
      return super.get(currentFlowNodeInstanceId);
    }

    public void setParent(String currentFlowNodeInstanceId, String parentFlowNodeInstanceId) {
      super.put(currentFlowNodeInstanceId, parentFlowNodeInstanceId);
    }

    public Set<String> getFlowNodeInstanceIds() {
      return super.keySet();
    }
  }

  static class VariableMap extends HashMap<String, String> {

    public void putAll(final VariableMap m) {
      for (Map.Entry<String, String> entry : m.entrySet()) {
        // since we build variable map from bottom to top of the flow node tree, we don't overwrite
        // the values from lower (inner) scopes with those from upper (outer) scopes
        putIfAbsent(entry.getKey(), entry.getValue());
      }
    }

    @Override
    @Deprecated
    public void putAll(final Map<? extends String, ? extends String> m) {
      super.putAll(m);
    }
  }

  public static class GetVariablesRequest extends Pair<String, String> {

    public GetVariablesRequest(final String flowNodeInstanceId, final String workflowInstanceId) {
      super(flowNodeInstanceId, workflowInstanceId);
    }

    public String getFlowNodeInstanceId() {
      return super.first;
    }

    public String getWorkflowInstanceId() {
      return super.second;
    }
  }
}
