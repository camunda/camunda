/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.script;

import static org.camunda.optimize.service.db.es.writer.usertask.UserTaskDurationScriptUtil.createUpdateUserTaskMetricsScript;

public interface UserTaskScriptFactory {
  static String createCompletedUserTaskInlineUpdateScript(final String updateUserTaskFiledsScript) {
    return """
        if (ctx._source.${flowNodesField} == null) { ctx._source.${flowNodesField} = []; }
          def userTaskInstancesById = ctx._source.${flowNodesField}.stream()
            .filter(flowNode -> "${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField}))
            .collect(Collectors.toMap(flowNode -> flowNode.${userTaskIdField}, flowNode -> flowNode, (fn1, fn2) -> fn1));
            for (def newFlowNode : params.${flowNodesField}) {
              // Ignore flowNodes that aren't userTasks
              if(!"${userTaskFlowNodeType}".equalsIgnoreCase(newFlowNode.${flowNodeTypeField})){ continue; }

              def existingTask = userTaskInstancesById.get(newFlowNode.${userTaskIdField});
              if (existingTask != null) {
            """
        + updateUserTaskFiledsScript
        + """
              } else {
                userTaskInstancesById.put(newFlowNode.${userTaskIdField}, newFlowNode);
              }
            }
            ctx._source.${flowNodesField}.removeIf(flowNode -> "${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField}));
            ctx._source.${flowNodesField}.addAll(userTaskInstancesById.values());
            """
        + createUpdateUserTaskMetricsScript();
  }

  static String createIdentityLinkLogInlineUpdateScript() {
    return """
        // 1 check for existing userTask
        if (ctx._source.${flowNodesField} == null) ctx._source.${flowNodesField} = [];
        def userTaskInstancesById = ctx._source.${flowNodesField}.stream()
          .filter(flowNode -> "${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField}))
          .collect(Collectors.toMap(flowNode -> flowNode.${userTaskIdField}, flowNode -> flowNode, (fn1, fn2) -> fn1));
        for (def currentUserTask : params.${flowNodesField}) {
          // Ignore flowNodes that aren't userTasks
          if(!"${userTaskFlowNodeType}".equalsIgnoreCase(currentUserTask.${flowNodeTypeField})){ continue; }

          def existingTask = userTaskInstancesById.get(currentUserTask.${userTaskIdField});
          if (existingTask != null) {
            // 2.1.1 if it exists add the assignee operation to the existing ones
            if(existingTask.${assigneeOperationsField} == null) { existingTask.${assigneeOperationsField} = []; }
            def existingOperationsById = existingTask.${assigneeOperationsField}.stream()
              .collect(Collectors.toMap(operation -> operation.${assigneeOpIdField}, operation -> operation));
            currentUserTask.${assigneeOperationsField}.stream()
              .forEachOrdered(operation -> existingOperationsById.putIfAbsent(operation.${assigneeOpIdField}, operation));
            def assigneeOperations = new ArrayList(existingOperationsById.values());
            Collections.sort(assigneeOperations, (o1, o2) -> o1.${assigneeOpTimestampField}.compareTo(o2.${assigneeOpTimestampField}));
            existingTask.assigneeOperations = assigneeOperations;
            // 2.1.2 if it exists add the candidate group operation to the existing ones
            if(existingTask.${candidateGroupOperationsField} == null) { existingTask.${candidateGroupOperationsField} = []; }
            existingOperationsById = existingTask.${candidateGroupOperationsField}.stream()
              .collect(Collectors.toMap(operation -> operation.${candidateGroupOpIdField}, operation -> operation));
            currentUserTask.${candidateGroupOperationsField}.stream()
              .forEachOrdered(operation -> existingOperationsById.putIfAbsent(operation.${candidateGroupOpIdField}, operation));
            def candidateOperations = new ArrayList(existingOperationsById.values());
            Collections.sort(candidateOperations, (o1, o2) -> o1.${candidateGroupOpTimestampField}.compareTo(o2.${candidateGroupOpTimestampField}));
            existingTask.candidateGroupOperations = candidateOperations;
          } else {
            // 2.2 if it doesn't exist add it with id and assignee/candidate group operations set
            userTaskInstancesById.put(currentUserTask.${userTaskIdField}, currentUserTask);
          }
        }
        ctx._source.${flowNodesField}.removeIf(flowNode -> "${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField}));
        ctx._source.${flowNodesField}.addAll(userTaskInstancesById.values());
          """;
  }

  static String createIdentityLinkLogUpdateAssigneeScript() {
    return """
      if (ctx._source.${flowNodesField} != null) {
        for (def currentFlowNode : ctx._source.${flowNodesField}) {
          // Ignore any flowNodes that arent userTasks
          if(!"${userTaskFlowNodeType}".equalsIgnoreCase(currentFlowNode.${flowNodeTypeField})) {
            continue;
          }

          if(currentFlowNode.${assigneeOperationsField} == null) { currentFlowNode.${assigneeOperationsField} = []; }
          def assignee = currentFlowNode.${assigneeOperationsField}.stream()
           .reduce((first, second) -> {
             boolean sameTimestampAndFirstIsAddOperation = first.${assigneeOpTimestampField}.equals(second.${assigneeOpTimestampField}) &&
               "${identityLinkOperationAdd}".equals(first.${assigneeOperationTypeField}) &&
               !"${identityLinkOperationAdd}".equals(second.${assigneeOperationTypeField});
              return sameTimestampAndFirstIsAddOperation ? first : second;
          })
           .map(logEntry -> {
             if("${identityLinkOperationAdd}".equals(logEntry.${assigneeOperationTypeField})) {
               return logEntry.${assigneeOperationUserIdField};
             } else {
               return null;
             }
           })
           .orElse(null);
          currentFlowNode.${assigneeField} = assignee;
        }
      }
    """;
  }

  static String createIdentityLinkLogUpdateCandidateGroupScript() {
    return """
      if (ctx._source.${flowNodesField} != null) {
        for (def currentFlowNode : ctx._source.${flowNodesField}) {
          // Ignore any flowNodes that arent userTasks
          if(!"${userTaskFlowNodeType}".equalsIgnoreCase(currentFlowNode.${flowNodeTypeField})) {
          continue;
          }

          if(currentFlowNode.${candidateGroupOperationsField} == null) { currentFlowNode.${candidateGroupOperationsField} = []; }
          Set candidateGroups = new HashSet();
          currentFlowNode.${candidateGroupOperationsField}.stream()
           .forEach(logEntry -> {
             if ("${identityLinkOperationAdd}".equals(logEntry.${candidateGroupOperationTypeField})) {
               candidateGroups.add(logEntry.${candidateGroupOperationGroupIdField});
             } else {
               candidateGroups.remove(logEntry.${candidateGroupOperationGroupIdField});
             }
           });
          currentFlowNode.${candidateGroupsField} = new ArrayList(candidateGroups);
        }
      }
    """;
  }

  static String createRunningUserTaskInstanceInlineUpdateScript(
      final String updateUserTaskFiledsScript) {
    return """
      if (ctx._source.${flowNodesField} == null) { ctx._source.${flowNodesField} = []; }
      def userTaskInstancesById = ctx._source.${flowNodesField}.stream()
        .filter(flowNode -> "${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField}))
        .collect(Collectors.toMap(flowNode -> flowNode.${userTaskIdField}, flowNode -> flowNode, (fn1, fn2) -> fn1));
      for (def newFlowNode : params.${flowNodesField}) {
        // Ignore flowNodes that aren't userTasks
        if(!"${userTaskFlowNodeType}".equalsIgnoreCase(newFlowNode.${flowNodeTypeField})){ continue; }

        def existingTask  = userTaskInstancesById.get(newFlowNode.${userTaskIdField});
        if (existingTask != null) {
      """
        + updateUserTaskFiledsScript
        + """
        } else {
          userTaskInstancesById.put(newFlowNode.${userTaskIdField}, newFlowNode);
        }
      }
      ctx._source.${flowNodesField}.removeIf(flowNode -> "${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField}));
      ctx._source.${flowNodesField}.addAll(userTaskInstancesById.values());
            """
        + createUpdateUserTaskMetricsScript();
  }
}
