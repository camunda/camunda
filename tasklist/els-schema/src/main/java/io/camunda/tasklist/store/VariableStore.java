/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store;

import io.camunda.tasklist.entities.*;
import io.camunda.tasklist.views.TaskSearchView;
import java.util.*;

public interface VariableStore {

  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      List<String> flowNodeInstanceIds, List<String> varNames, final Set<String> fieldNames);

  public Map<String, List<TaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests);

  Map<String, String> getTaskVariablesIdsWithIndexByTaskIds(final List<String> taskIds);

  public void persistTaskVariables(final Collection<TaskVariableEntity> finalVariables);

  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<String> processInstanceIds);

  public VariableEntity getRuntimeVariable(final String variableId, Set<String> fieldNames);

  public TaskVariableEntity getTaskVariable(final String variableId, Set<String> fieldNames);

  public List<String> getProcessInstanceIdsWithMatchingVars(
      List<String> varNames, List<String> varValues);

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

  static class VariableMap extends HashMap<String, VariableEntity> {

    public void putAll(final VariableMap m) {
      for (Entry<String, VariableEntity> entry : m.entrySet()) {
        // since we build variable map from bottom to top of the flow node tree, we don't overwrite
        // the values from lower (inner) scopes with those from upper (outer) scopes
        putIfAbsent(entry.getKey(), entry.getValue());
      }
    }

    @Override
    @Deprecated
    public void putAll(final Map<? extends String, ? extends VariableEntity> m) {
      super.putAll(m);
    }
  }

  public static class GetVariablesRequest {

    private String taskId;
    private TaskState state;
    private String flowNodeInstanceId;
    private String processInstanceId;
    private List<String> varNames;
    private Set<String> fieldNames = new HashSet<>();

    public static GetVariablesRequest createFrom(TaskEntity taskEntity, Set<String> fieldNames) {
      return new GetVariablesRequest()
          .setTaskId(taskEntity.getId())
          .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
          .setState(taskEntity.getState())
          .setProcessInstanceId(taskEntity.getProcessInstanceId())
          .setFieldNames(fieldNames);
    }

    public static GetVariablesRequest createFrom(TaskEntity taskEntity) {
      return new GetVariablesRequest()
          .setTaskId(taskEntity.getId())
          .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
          .setState(taskEntity.getState())
          .setProcessInstanceId(taskEntity.getProcessInstanceId());
    }

    public static GetVariablesRequest createFrom(
        TaskSearchView taskSearchView, List<String> varNames, Set<String> fieldNames) {
      return new GetVariablesRequest()
          .setTaskId(taskSearchView.getId())
          .setFlowNodeInstanceId(taskSearchView.getFlowNodeInstanceId())
          .setState(taskSearchView.getState())
          .setProcessInstanceId(taskSearchView.getProcessInstanceId())
          .setVarNames(varNames)
          .setFieldNames(fieldNames);
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

    public Set<String> getFieldNames() {
      return fieldNames;
    }

    public GetVariablesRequest setFieldNames(final Set<String> fieldNames) {
      this.fieldNames = fieldNames;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final GetVariablesRequest that = (GetVariablesRequest) o;
      return Objects.equals(taskId, that.taskId)
          && state == that.state
          && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
          && Objects.equals(processInstanceId, that.processInstanceId)
          && Objects.equals(varNames, that.varNames)
          && Objects.equals(fieldNames, that.fieldNames);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          taskId, state, flowNodeInstanceId, processInstanceId, varNames, fieldNames);
    }
  }
}
