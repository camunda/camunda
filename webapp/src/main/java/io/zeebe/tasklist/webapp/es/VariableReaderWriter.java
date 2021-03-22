/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es;

import static io.zeebe.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.zeebe.tasklist.util.ElasticsearchUtil.scroll;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.FlowNodeInstanceEntity;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.entities.TaskVariableEntity;
import io.zeebe.tasklist.entities.VariableEntity;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.zeebe.tasklist.schema.indices.VariableIndex;
import io.zeebe.tasklist.schema.templates.TaskVariableTemplate;
import io.zeebe.tasklist.util.CollectionUtil;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.VariableDTO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableReaderWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableReaderWriter.class);
  private static final String ABSENT_PARENT_ID = "-1";

  @Autowired private RestHighLevelClient esClient;
  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;
  @Autowired private VariableIndex variableIndex;
  @Autowired private TaskVariableTemplate taskVariableTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TaskReaderWriter taskReaderWriter;

  public void persistTaskVariables(String taskId, List<VariableDTO> changedVariables) {
    // take current runtime variables values and
    final List<VariableDTO> variablesByTaskId = getRuntimeVariablesByTaskId(taskId);

    // update/append with variables passed for task completion
    final Map<String, TaskVariableEntity> finalVariablesMap =
        variablesByTaskId.stream()
            .collect(
                Collectors.toMap(
                    VariableDTO::getName,
                    v -> new TaskVariableEntity(taskId, v.getName(), v.getValue())));
    for (VariableDTO var : changedVariables) {
      finalVariablesMap.put(
          var.getName(), new TaskVariableEntity(taskId, var.getName(), var.getValue()));
    }
    final BulkRequest bulkRequest = new BulkRequest();
    for (TaskVariableEntity variableEntity : finalVariablesMap.values()) {
      bulkRequest.add(createUpsertRequest(variableEntity));
    }
    try {
      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, RefreshPolicy.WAIT_UNTIL);
    } catch (PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private UpdateRequest createUpsertRequest(TaskVariableEntity variableEntity) {
    try {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskVariableTemplate.TASK_ID, variableEntity.getTaskId());
      updateFields.put(TaskVariableTemplate.NAME, variableEntity.getName());
      updateFields.put(TaskVariableTemplate.VALUE, variableEntity.getValue());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest(
              taskVariableTemplate.getFullQualifiedName(),
              ElasticsearchUtil.ES_INDEX_TYPE,
              variableEntity.getId())
          .upsert(objectMapper.writeValueAsString(variableEntity), XContentType.JSON)
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error preparing the query to upsert task variable instance [%s]",
              variableEntity.getId()),
          e);
    }
  }

  private List<VariableDTO> getRuntimeVariablesByTaskId(String taskId) {
    final TaskEntity task = taskReaderWriter.getTask(taskId);
    final List<GetVariablesRequest> requests =
        Collections.singletonList(GetVariablesRequest.createFrom(task));
    final Map<String, List<VariableDTO>> variablesByTaskIds =
        getRuntimeVariablesPerTaskId(requests);
    if (variablesByTaskIds.size() > 0) {
      return variablesByTaskIds.values().iterator().next();
    } else {
      return new ArrayList<>();
    }
  }

  private Map<String, List<VariableDTO>> getRuntimeVariablesPerTaskId(
      List<GetVariablesRequest> requests) {

    if (requests == null || requests.size() == 0) {
      return new HashMap<>();
    }

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
   * @return list of variables per each taskId
   */
  private Map<String, List<VariableDTO>> buildResponse(
      final Map<String, FlowNodeTree> flowNodeTrees,
      final Map<String, VariableMap> variableMaps,
      final List<GetVariablesRequest> requests) {

    final Map<String, List<VariableDTO>> response = new HashMap<>();

    for (GetVariablesRequest req : requests) {
      final FlowNodeTree flowNodeTree = flowNodeTrees.get(req.getWorkflowInstanceId());

      final VariableMap resultingVariableMap = new VariableMap();

      accumulateVariables(
          resultingVariableMap, variableMaps, flowNodeTree, req.getFlowNodeInstanceId());

      response.put(
          req.getTaskId(),
          resultingVariableMap.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .collect(getVariableDTOListCollector()));
    }
    return response;
  }

  @NotNull
  private Collector<Entry<String, String>, ArrayList<VariableDTO>, ArrayList<VariableDTO>>
      getVariableDTOListCollector() {
    return Collector.of(
        ArrayList::new,
        (list, entry) ->
            list.add(new VariableDTO().setName(entry.getKey()).setValue(entry.getValue())),
        (list1, list2) -> {
          list1.addAll(list2);
          return list1;
        });
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
    final String parentFlowNodeId =
        flowNodeTree != null ? flowNodeTree.getParent(flowNodeInstanceId) : null;
    if (parentFlowNodeId != null && !parentFlowNodeId.equals(ABSENT_PARENT_ID)) {
      accumulateVariables(resultingVariableMap, variableMaps, flowNodeTree, parentFlowNodeId);
    }
  }

  private List<VariableEntity> getVariablesByFlowNodeInstanceIds(List<String> flowNodeInstanceIds) {
    final TermsQueryBuilder flowNodeInstanceKeyQuery =
        termsQuery(VariableIndex.SCOPE_FLOW_NODE_ID, flowNodeInstanceIds);
    final SearchRequest searchRequest =
        new SearchRequest(variableIndex.getAlias())
            .source(new SearchSourceBuilder().query(constantScoreQuery(flowNodeInstanceKeyQuery)));
    try {
      return scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private List<FlowNodeInstanceEntity> getFlowNodeInstances(
      final List<String> workflowInstanceIds) {
    final TermsQueryBuilder workflowInstanceKeyQuery =
        termsQuery(FlowNodeInstanceIndex.WORKFLOW_INSTANCE_ID, workflowInstanceIds);
    final SearchRequest searchRequest =
        new SearchRequest(flowNodeInstanceIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(workflowInstanceKeyQuery))
                    .sort(FlowNodeInstanceIndex.POSITION, SortOrder.ASC));
    try {
      return scroll(searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all flow nodes: %s", e.getMessage());
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
    final List<VariableEntity> variables = getVariablesByFlowNodeInstanceIds(flowNodeInstanceIds);

    return variables.stream()
        .collect(groupingBy(VariableEntity::getScopeFlowNodeId, getVariableMapCollector()));
  }

  @NotNull
  private Collector<VariableEntity, VariableMap, VariableMap> getVariableMapCollector() {
    return Collector.of(
        VariableMap::new,
        (map, var) -> map.put(var.getName(), var.getValue()),
        (map1, map2) -> {
          map1.putAll(map2);
          return map1;
        });
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

  public List<List<VariableDTO>> getVariables(List<GetVariablesRequest> requests) {
    final Map<TaskState, List<GetVariablesRequest>> groupByStates =
        requests.stream().collect(groupingBy(GetVariablesRequest::getState));
    final Map<String, List<VariableDTO>> varsForActive =
        getRuntimeVariablesPerTaskId(groupByStates.get(TaskState.CREATED));
    final Map<String, List<VariableDTO>> varsForCompleted =
        getTaskVariablesPerTaskId(groupByStates.get(TaskState.COMPLETED));

    final List<List<VariableDTO>> response = new ArrayList<>();
    for (GetVariablesRequest req : requests) {
      List<VariableDTO> vars = new ArrayList<>();
      switch (req.getState()) {
        case CREATED:
          vars = varsForActive.getOrDefault(req.getTaskId(), new ArrayList<>());
          break;
        case COMPLETED:
          vars = varsForCompleted.getOrDefault(req.getTaskId(), new ArrayList<>());
          break;
        default:
          break;
      }
      vars.sort(Comparator.comparing(VariableDTO::getName));
      response.add(vars);
    }
    return response;
  }

  private Map<String, List<VariableDTO>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests) {

    if (requests == null || requests.size() == 0) {
      return new HashMap<>();
    }
    final TermsQueryBuilder taskIdsQuery =
        termsQuery(
            TaskVariableTemplate.TASK_ID,
            requests.stream().map(GetVariablesRequest::getTaskId).collect(toList()));
    final SearchRequest searchRequest =
        new SearchRequest(taskVariableTemplate.getAlias())
            .source(new SearchSourceBuilder().query(constantScoreQuery(taskIdsQuery)));
    try {
      final List<TaskVariableEntity> entities =
          scroll(searchRequest, TaskVariableEntity.class, objectMapper, esClient);
      return entities.stream()
          .collect(
              groupingBy(
                  TaskVariableEntity::getTaskId,
                  mapping(tv -> VariableDTO.createFrom(tv), toList())));
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
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

  public static class GetVariablesRequest {

    private String taskId;
    private TaskState state;
    private String flowNodeInstanceId;
    private String workflowInstanceId;

    public static GetVariablesRequest createFrom(TaskDTO taskDTO) {
      return new GetVariablesRequest()
          .setTaskId(taskDTO.getId())
          .setFlowNodeInstanceId(taskDTO.getFlowNodeInstanceId())
          .setState(taskDTO.getTaskState())
          .setWorkflowInstanceId(taskDTO.getWorkflowInstanceId());
    }

    public static GetVariablesRequest createFrom(TaskEntity taskEntity) {
      return new GetVariablesRequest()
          .setTaskId(taskEntity.getId())
          .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
          .setState(taskEntity.getState())
          .setWorkflowInstanceId(taskEntity.getWorkflowInstanceId());
    }

    public String getTaskId() {
      return taskId;
    }

    public GetVariablesRequest setTaskId(final String taskId) {
      this.taskId = taskId;
      return this;
    }

    public TaskState getState() {
      return state;
    }

    public GetVariablesRequest setState(final TaskState state) {
      this.state = state;
      return this;
    }

    public String getFlowNodeInstanceId() {
      return flowNodeInstanceId;
    }

    public GetVariablesRequest setFlowNodeInstanceId(final String flowNodeInstanceId) {
      this.flowNodeInstanceId = flowNodeInstanceId;
      return this;
    }

    public String getWorkflowInstanceId() {
      return workflowInstanceId;
    }

    public GetVariablesRequest setWorkflowInstanceId(final String workflowInstanceId) {
      this.workflowInstanceId = workflowInstanceId;
      return this;
    }
  }
}
