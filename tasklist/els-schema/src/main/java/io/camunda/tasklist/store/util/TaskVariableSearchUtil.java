/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.util;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskVariableSearchUtil {
  private static final String ABSENT_PARENT_ID = "-1";

  @Autowired private VariableStore variableStore;

  public List<String> getTaskIdsContainingVariables(
      final List<VariableStore.GetVariablesRequest> requests,
      final Map<String, String> variableNameAndVar) {

    if (requests.isEmpty() || variableNameAndVar.isEmpty()) {
      return Collections.emptyList();
    }

    // build flow node trees (for each process instance)
    final Map<String, VariableStore.FlowNodeTree> flowNodeTrees = buildFlowNodeTrees(requests);

    // build local variable map  (for each flow node instance)
    final List<String> flowNodeInstanceIds =
        flowNodeTrees.values().stream()
            .flatMap(f -> f.getFlowNodeInstanceIds().stream())
            .collect(Collectors.toList());
    final Map<String, VariableStore.VariableMap> variableMaps =
        buildVariableMaps(
            flowNodeInstanceIds,
            requests.stream()
                .map(VariableStore.GetVariablesRequest::getVarNames)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .collect(toList()),
            requests
                .get(0)
                .getFieldNames()); // we assume here that all requests has the same list of  fields

    final var taskIdToVariablesMap =
        buildTaskIdToVariablesMap(flowNodeTrees, variableMaps, requests);

    return taskIdToVariablesMap.entrySet().stream()
        .filter(e -> e.getValue().entrySet().containsAll(variableNameAndVar.entrySet()))
        .map(Entry::getKey)
        .toList();
  }

  private Map<String, VariableStore.FlowNodeTree> buildFlowNodeTrees(
      final List<VariableStore.GetVariablesRequest> requests) {
    final List<String> processInstanceIds =
        CollectionUtil.map(requests, VariableStore.GetVariablesRequest::getProcessInstanceId);
    // get all flow node instances for all process instance ids
    final List<FlowNodeInstanceEntity> flowNodeInstances =
        variableStore.getFlowNodeInstances(
            processInstanceIds.stream().map(Long::parseLong).toList());

    final Map<String, VariableStore.FlowNodeTree> flowNodeTrees = new HashMap<>();
    for (final FlowNodeInstanceEntity flowNodeInstance : flowNodeInstances) {
      getFlowNodeTree(flowNodeTrees, String.valueOf(flowNodeInstance.getProcessInstanceKey()))
          .setParent(flowNodeInstance.getId(), String.valueOf(getScopeKey(flowNodeInstance)));
    }

    // ensure that process instances are added
    // as a FNI to the FNI tree
    flowNodeTrees
        .keySet()
        .forEach(
            id -> {
              getFlowNodeTree(flowNodeTrees, id).setParent(id, ABSENT_PARENT_ID);
            });

    return flowNodeTrees;
  }

  private Long getScopeKey(final FlowNodeInstanceEntity flowNodeInstance) {
    return Optional.ofNullable(flowNodeInstance.getScopeKey())
        .map(this::getScopeKeyIfPresent)
        .orElseGet(() -> getScopeKeyFromTreePathIfPresent(flowNodeInstance));
  }

  private Long getScopeKeyIfPresent(final Long scopeKey) {
    return !ABSENT_PARENT_ID.equals(String.valueOf(scopeKey)) ? scopeKey : null;
  }

  private Long getScopeKeyFromTreePathIfPresent(final FlowNodeInstanceEntity flowNodeInstance) {
    return Optional.ofNullable(flowNodeInstance.getTreePath())
        .map(this::splitTreePath)
        .map(v -> getParentScopeKey(flowNodeInstance, v))
        .orElse(flowNodeInstance.getProcessInstanceKey());
  }

  private List<Long> splitTreePath(final String treePath) {
    return Arrays.stream(treePath.split("/")).map(Long::valueOf).collect(Collectors.toList());
  }

  private Long getParentScopeKey(
      final FlowNodeInstanceEntity flowNodeInstance, final List<Long> treePath) {
    if (!treePath.isEmpty() && treePath.getLast().equals(flowNodeInstance.getKey())) {
      treePath.removeLast();
    }
    return !treePath.isEmpty() ? treePath.getLast() : null;
  }

  private VariableStore.FlowNodeTree getFlowNodeTree(
      final Map<String, VariableStore.FlowNodeTree> flowNodeTrees, final String processInstanceId) {
    if (flowNodeTrees.get(processInstanceId) == null) {
      flowNodeTrees.put(processInstanceId, new VariableStore.FlowNodeTree());
    }
    return flowNodeTrees.get(processInstanceId);
  }

  private Map<String, VariableStore.VariableMap> buildVariableMaps(
      final List<String> flowNodeInstanceIds,
      final List<String> varNames,
      final Set<String> fieldNames) {
    // get list of all variables
    final List<VariableEntity> variables =
        variableStore.getVariablesByFlowNodeInstanceIds(flowNodeInstanceIds, varNames, fieldNames);

    return variables.stream()
        .collect(groupingBy(v -> String.valueOf(v.getScopeKey()), getVariableMapCollector()));
  }

  private Collector<VariableEntity, VariableStore.VariableMap, VariableStore.VariableMap>
      getVariableMapCollector() {
    return Collector.of(
        VariableStore.VariableMap::new,
        (map, var) -> map.put(var.getName(), var),
        (map1, map2) -> {
          map1.putAll(map2);
          return map1;
        });
  }

  private Map<String, Map<String, String>> buildTaskIdToVariablesMap(
      final Map<String, VariableStore.FlowNodeTree> flowNodeTrees,
      final Map<String, VariableStore.VariableMap> variableMaps,
      final List<VariableStore.GetVariablesRequest> requests) {

    final Map<String, Map<String, String>> response = new HashMap<>();

    for (final VariableStore.GetVariablesRequest req : requests) {
      final VariableStore.FlowNodeTree flowNodeTree = flowNodeTrees.get(req.getProcessInstanceId());

      final VariableStore.VariableMap resultingVariableMap = new VariableStore.VariableMap();

      accumulateVariables(
          resultingVariableMap, variableMaps, flowNodeTree, req.getFlowNodeInstanceId());

      response
          .computeIfAbsent(req.getTaskId(), k -> new HashMap<>())
          .putAll(
              resultingVariableMap.values().stream()
                  .collect(Collectors.toMap(VariableEntity::getName, VariableEntity::getValue)));
    }
    return response;
  }

  private void accumulateVariables(
      final VariableStore.VariableMap resultingVariableMap,
      final Map<String, VariableStore.VariableMap> variableMaps,
      final VariableStore.FlowNodeTree flowNodeTree,
      final String flowNodeInstanceId) {
    final VariableStore.VariableMap m = variableMaps.get(flowNodeInstanceId);
    if (m != null) {
      resultingVariableMap.putAll(m);
    }
    final String parentFlowNodeId =
        flowNodeTree != null ? flowNodeTree.getParent(flowNodeInstanceId) : null;
    if (parentFlowNodeId != null && !parentFlowNodeId.equals(ABSENT_PARENT_ID)) {
      accumulateVariables(resultingVariableMap, variableMaps, flowNodeTree, parentFlowNodeId);
    }
  }
}
