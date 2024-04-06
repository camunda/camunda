/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.store.util;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.util.CollectionUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskVariableSearchUtil {
  private static final String ABSENT_PARENT_ID = "-1";

  @Autowired private VariableStore variableStore;

  public Boolean checkIfVariablesExistInTask(
      List<VariableStore.GetVariablesRequest> requests, Map<String, String> variableNameAndVar) {

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

    final Map<String, List<VariableEntity>> variables =
        buildResponse(flowNodeTrees, variableMaps, requests);

    for (Map.Entry<String, List<VariableEntity>> taskEntry : variables.entrySet()) {
      final List<VariableEntity> taskVariables = taskEntry.getValue();

      for (Map.Entry<String, String> variableEntry : variableNameAndVar.entrySet()) {
        final String requiredVarName = variableEntry.getKey();
        final String requiredVarValue = variableEntry.getValue();

        // Check if the variable with the required name and value exists for the current task.
        final boolean exists =
            taskVariables.stream()
                .anyMatch(
                    varEntity ->
                        requiredVarName.equals(varEntity.getName())
                            && requiredVarValue.equals(varEntity.getValue()));

        if (!exists) {
          return false; // If the required variable doesn't exist for the task, return false.
        }
      }
    }

    return true;
  }

  private Map<String, VariableStore.FlowNodeTree> buildFlowNodeTrees(
      List<VariableStore.GetVariablesRequest> requests) {
    final List<String> processInstanceIds =
        CollectionUtil.map(requests, VariableStore.GetVariablesRequest::getProcessInstanceId);
    // get all flow node instances for all process instance ids
    final List<FlowNodeInstanceEntity> flowNodeInstances =
        variableStore.getFlowNodeInstances(processInstanceIds);

    final Map<String, VariableStore.FlowNodeTree> flowNodeTrees = new HashMap<>();
    for (FlowNodeInstanceEntity flowNodeInstance : flowNodeInstances) {
      getFlowNodeTree(flowNodeTrees, flowNodeInstance.getProcessInstanceId())
          .setParent(flowNodeInstance.getId(), flowNodeInstance.getParentFlowNodeId());
    }
    return flowNodeTrees;
  }

  private VariableStore.FlowNodeTree getFlowNodeTree(
      Map<String, VariableStore.FlowNodeTree> flowNodeTrees, String processInstanceId) {
    if (flowNodeTrees.get(processInstanceId) == null) {
      flowNodeTrees.put(processInstanceId, new VariableStore.FlowNodeTree());
    }
    return flowNodeTrees.get(processInstanceId);
  }

  private Map<String, VariableStore.VariableMap> buildVariableMaps(
      List<String> flowNodeInstanceIds, List<String> varNames, Set<String> fieldNames) {
    // get list of all variables
    final List<VariableEntity> variables =
        variableStore.getVariablesByFlowNodeInstanceIds(flowNodeInstanceIds, varNames, fieldNames);

    return variables.stream()
        .collect(groupingBy(VariableEntity::getScopeFlowNodeId, getVariableMapCollector()));
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

  private Map<String, List<VariableEntity>> buildResponse(
      final Map<String, VariableStore.FlowNodeTree> flowNodeTrees,
      final Map<String, VariableStore.VariableMap> variableMaps,
      final List<VariableStore.GetVariablesRequest> requests) {

    final Map<String, List<VariableEntity>> response = new HashMap<>();

    for (VariableStore.GetVariablesRequest req : requests) {
      final VariableStore.FlowNodeTree flowNodeTree = flowNodeTrees.get(req.getProcessInstanceId());

      final VariableStore.VariableMap resultingVariableMap = new VariableStore.VariableMap();

      accumulateVariables(
          resultingVariableMap, variableMaps, flowNodeTree, req.getFlowNodeInstanceId());

      response.put(
          req.getTaskId(),
          resultingVariableMap.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .map(e -> e.getValue())
              .collect(Collectors.toList()));
    }
    return response;
  }

  private void accumulateVariables(
      VariableStore.VariableMap resultingVariableMap,
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
