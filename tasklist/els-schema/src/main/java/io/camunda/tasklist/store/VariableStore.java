/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.tasklist.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import java.util.*;
import java.util.stream.Collectors;

public interface VariableStore {

  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      List<String> flowNodeInstanceIds, List<String> varNames, final Set<String> fieldNames);

  public Map<String, List<SnapshotTaskVariableEntity>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests);

  Map<String, String> getTaskVariablesIdsWithIndexByTaskIds(final List<String> taskIds);

  public void persistTaskVariables(final Collection<SnapshotTaskVariableEntity> finalVariables);

  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<String> processInstanceIds);

  public VariableEntity getRuntimeVariable(final String variableId, Set<String> fieldNames);

  public SnapshotTaskVariableEntity getTaskVariable(
      final String variableId, Set<String> fieldNames);

  public List<String> getProcessInstanceIdsWithMatchingVars(
      List<String> varNames, List<String> varValues);

  private static Optional<String> getTaskVariableElsFieldByGraphqlField(final String fieldName) {
    switch (fieldName) {
      case ("id"):
        return Optional.of(SnapshotTaskVariableTemplate.ID);
      case ("name"):
        return Optional.of(SnapshotTaskVariableTemplate.NAME);
      case ("value"):
        return Optional.of(SnapshotTaskVariableTemplate.FULL_VALUE);
      case ("previewValue"):
        return Optional.of(SnapshotTaskVariableTemplate.VALUE);
      case ("isValueTruncated"):
        return Optional.of(SnapshotTaskVariableTemplate.IS_PREVIEW);
      default:
        return Optional.empty();
    }
  }

  public static Set<String> getTaskVariableElsFieldsByGraphqlFields(final Set<String> fieldNames) {
    return fieldNames.stream()
        .map((fn) -> getTaskVariableElsFieldByGraphqlField(fn))
        .flatMap(Optional::stream)
        .collect(Collectors.toSet());
  }

  private static Optional<String> getVariableElsFieldByGraphqlField(final String fieldName) {
    switch (fieldName) {
      case ("id"):
        return of(VariableTemplate.ID);
      case ("name"):
        return of(VariableTemplate.NAME);
      case ("value"):
        return of(VariableTemplate.FULL_VALUE);
      case ("previewValue"):
        return of(VariableTemplate.VALUE);
      case ("isValueTruncated"):
        return of(VariableTemplate.IS_PREVIEW);
      default:
        return empty();
    }
  }

  public static Set<String> getVariableTemplateElsFieldsByGraphqlFields(
      final Set<String> fieldNames) {
    final var result =
        fieldNames.stream()
            .map((fn) -> getVariableElsFieldByGraphqlField(fn))
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
    // When the variable value is not longer than the configured variable
    // threshold, then the variable value is stored in the field "value",
    // but not in the field "fullValue".
    // When the variable is longer than the configured variable threshold,
    // then the variable value is stored in the field "value" but truncated,
    // and the entire variable value is stored in the field "fullValue".
    // So, if the full value is requested, then additional
    // values (is_preview and value) must be requested as well,
    // so that the API returns the correct value.
    if (result.contains(VariableTemplate.FULL_VALUE)) {
      result.add(VariableTemplate.IS_PREVIEW);
      result.add(VariableTemplate.VALUE);
    }
    return result;
  }

  static class FlowNodeTree extends HashMap<String, String> {

    public String getParent(final String currentFlowNodeInstanceId) {
      return super.get(currentFlowNodeInstanceId);
    }

    public void setParent(final String currentFlowNodeInstanceId, final String parentFlowNodeInstanceId) {
      super.put(currentFlowNodeInstanceId, parentFlowNodeInstanceId);
    }

    public Set<String> getFlowNodeInstanceIds() {
      return super.keySet();
    }
  }

  static class VariableMap extends HashMap<String, VariableEntity> {

    public void putAll(final VariableMap m) {
      for (final Entry<String, VariableEntity> entry : m.entrySet()) {
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

    public static GetVariablesRequest createFrom(final TaskEntity taskEntity, final Set<String> fieldNames) {
      return new GetVariablesRequest()
          .setTaskId(String.valueOf(taskEntity.getKey()))
          .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
          .setState(taskEntity.getState())
          .setProcessInstanceId(taskEntity.getProcessInstanceId())
          .setFieldNames(fieldNames);
    }

    public static GetVariablesRequest createFrom(final TaskEntity taskEntity) {
      return new GetVariablesRequest()
          .setTaskId(String.valueOf(taskEntity.getKey()))
          .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
          .setState(taskEntity.getState())
          .setProcessInstanceId(taskEntity.getProcessInstanceId());
    }

    public static GetVariablesRequest createFrom(
        final TaskSearchView taskSearchView, final List<String> varNames, final Set<String> fieldNames) {
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
    public int hashCode() {
      return Objects.hash(
          taskId, state, flowNodeInstanceId, processInstanceId, varNames, fieldNames);
    }

    @Override
    public boolean equals(final Object o) {
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
  }
}
