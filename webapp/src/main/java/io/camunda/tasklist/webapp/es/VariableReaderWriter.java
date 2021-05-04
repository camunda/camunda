/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.tasklist.util.ElasticsearchUtil.scroll;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
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

      return new UpdateRequest()
          .index(taskVariableTemplate.getFullQualifiedName())
          .id(variableEntity.getId())
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

    // build flow node trees (for each process instance)
    final Map<String, FlowNodeTree> flowNodeTrees = buildFlowNodeTrees(requests);

    // build local variable map  (for each flow node instance)
    final List<String> flowNodeInstanceIds =
        flowNodeTrees.values().stream()
            .flatMap(f -> f.getFlowNodeInstanceIds().stream())
            .collect(Collectors.toList());
    final Map<String, VariableMap> variableMaps =
        buildVariableMaps(
            flowNodeInstanceIds,
            requests.stream()
                .map(GetVariablesRequest::getVarNames)
                .flatMap(x -> x == null ? null : x.stream())
                .collect(toList()));

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
      final FlowNodeTree flowNodeTree = flowNodeTrees.get(req.getProcessInstanceId());

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

  private List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      List<String> flowNodeInstanceIds, List<String> varNames) {
    final TermsQueryBuilder flowNodeInstanceKeyQ =
        termsQuery(VariableIndex.SCOPE_FLOW_NODE_ID, flowNodeInstanceIds);
    TermsQueryBuilder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = termsQuery(VariableIndex.NAME, varNames);
    }
    final SearchRequest searchRequest =
        new SearchRequest(variableIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(joinWithAnd(flowNodeInstanceKeyQ, varNamesQ))));
    try {
      return scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<String> processInstanceIds) {
    final TermsQueryBuilder processInstanceKeyQuery =
        termsQuery(FlowNodeInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds);
    final SearchRequest searchRequest =
        new SearchRequest(flowNodeInstanceIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(processInstanceKeyQuery))
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
  private Map<String, VariableMap> buildVariableMaps(
      List<String> flowNodeInstanceIds, List<String> varNames) {
    // get list of all variables
    final List<VariableEntity> variables =
        getVariablesByFlowNodeInstanceIds(flowNodeInstanceIds, varNames);

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
   * Builds flow node tree for each requested process instance id.
   *
   * @param requests
   * @return map of flow node trees per process instance id
   */
  private Map<String, FlowNodeTree> buildFlowNodeTrees(List<GetVariablesRequest> requests) {
    final List<String> processInstanceIds =
        CollectionUtil.map(requests, GetVariablesRequest::getProcessInstanceId);
    // get all flow node instances for all process instance ids
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(processInstanceIds);

    final Map<String, FlowNodeTree> flowNodeTrees = new HashMap<>();
    for (FlowNodeInstanceEntity flowNodeInstance : flowNodeInstances) {
      getFlowNodeTree(flowNodeTrees, flowNodeInstance.getProcessInstanceId())
          .setParent(flowNodeInstance.getId(), flowNodeInstance.getParentFlowNodeId());
    }
    return flowNodeTrees;
  }

  private FlowNodeTree getFlowNodeTree(
      Map<String, FlowNodeTree> flowNodeTrees, String processInstanceId) {
    if (flowNodeTrees.get(processInstanceId) == null) {
      flowNodeTrees.put(processInstanceId, new FlowNodeTree());
    }
    return flowNodeTrees.get(processInstanceId);
  }

  public List<VariableDTO> getVariables(String taskId, List<String> variableNames) {
    final TaskEntity task = taskReaderWriter.getTask(taskId);
    final List<GetVariablesRequest> requests =
        Collections.singletonList(GetVariablesRequest.createFrom(task).setVarNames(variableNames));
    Map<String, List<VariableDTO>> variablesByTaskIds = new HashMap<>();
    switch (task.getState()) {
      case CREATED:
        variablesByTaskIds = getRuntimeVariablesPerTaskId(requests);
        break;
      case COMPLETED:
        variablesByTaskIds = getTaskVariablesPerTaskId(requests);
        break;
      default:
        break;
    }

    List<VariableDTO> vars = new ArrayList<>();
    if (variablesByTaskIds.size() > 0) {
      vars = variablesByTaskIds.values().iterator().next();
    }
    vars.sort(Comparator.comparing(VariableDTO::getName));
    return vars;
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
    final TermsQueryBuilder taskIdsQ =
        termsQuery(
            TaskVariableTemplate.TASK_ID,
            requests.stream().map(GetVariablesRequest::getTaskId).collect(toList()));
    final List<String> varNames =
        requests.stream()
            .map(GetVariablesRequest::getVarNames)
            .flatMap(x -> x == null ? null : x.stream())
            .collect(toList());
    TermsQueryBuilder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = termsQuery(VariableIndex.NAME, varNames);
    }
    final SearchRequest searchRequest =
        new SearchRequest(taskVariableTemplate.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(joinWithAnd(taskIdsQ, varNamesQ))));
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
    private String processInstanceId;
    private List<String> varNames;

    public static GetVariablesRequest createFrom(TaskDTO taskDTO) {
      return new GetVariablesRequest()
          .setTaskId(taskDTO.getId())
          .setFlowNodeInstanceId(taskDTO.getFlowNodeInstanceId())
          .setState(taskDTO.getTaskState())
          .setProcessInstanceId(taskDTO.getProcessInstanceId());
    }

    public static GetVariablesRequest createFrom(TaskEntity taskEntity) {
      return new GetVariablesRequest()
          .setTaskId(taskEntity.getId())
          .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
          .setState(taskEntity.getState())
          .setProcessInstanceId(taskEntity.getProcessInstanceId());
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

    public String getProcessInstanceId() {
      return processInstanceId;
    }

    public GetVariablesRequest setProcessInstanceId(final String processInstanceId) {
      this.processInstanceId = processInstanceId;
      return this;
    }

    public List<String> getVarNames() {
      return varNames;
    }

    public GetVariablesRequest setVarNames(final List<String> varNames) {
      this.varNames = varNames;
      return this;
    }
  }
}
