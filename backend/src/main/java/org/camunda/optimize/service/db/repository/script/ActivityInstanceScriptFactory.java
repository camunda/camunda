/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.script;

import static org.camunda.optimize.service.db.es.writer.usertask.UserTaskDurationScriptUtil.createUpdateUserTaskMetricsScript;

public interface ActivityInstanceScriptFactory {
  static String createCompletedActivityInlineUpdateScript(final String updateUserTaskFiledsScript) {
    return
        """
        def existingFlowNodeInstancesById = ctx._source.${flowNodesField}.stream()
         .filter(flowNode -> !"${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField}))
         .collect(Collectors.toMap(n -> n.${flowNodeInstanceIdField}, n -> n, (n1, n2) -> n1));
        def flowNodeInstancesToAddById = params.${flowNodesField}.stream()
         .filter(flowNode -> !"${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField}))
         .collect(Collectors.toMap(n -> n.${flowNodeInstanceIdField}, n -> n, (n1, n2) -> n1));
        existingFlowNodeInstancesById.putAll(flowNodeInstancesToAddById);

        // For userTask flownodes, we cannot rely on the flowNodeInstanceId as identifier because the identityLinkLog only
        // has the userTaskID. Due to our IdentityLinkLogImport, it is possible to have userTasks with only userTaskId and
        // no flownodeId/FlownodeInstanceId. Also note flownodes that aren't userTasks do not have userTaskIDs.
        def existingUserTaskInstancesById = ctx._source.${flowNodesField}.stream()
         .filter(flowNode -> "${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField}))
         .collect(Collectors.toMap(u -> u.${userTaskIdField}, u -> u, (u1, u2) -> u1));

        for (def newFlowNode : params.${flowNodesField}) {
          // Ignore flowNodes that aren't userTasks
          if(!"${userTaskFlowNodeType}".equalsIgnoreCase(newFlowNode.${flowNodeTypeField})){ continue; }

          def existingTask = existingUserTaskInstancesById.get(newFlowNode.${userTaskIdField});
          if (existingTask != null) {
      """
        + updateUserTaskFiledsScript
        +
        """
          } else {
            existingUserTaskInstancesById.put(newFlowNode.${userTaskIdField}, newFlowNode);
          }
        }

        ctx._source.${flowNodesField} = [];
        ctx._source.${flowNodesField}.addAll(existingFlowNodeInstancesById.values());
        ctx._source.${flowNodesField}.addAll(existingUserTaskInstancesById.values());

        if (params.${flowNodesField}.stream().anyMatch(flowNode ->
          "${userTaskFlowNodeType}".equalsIgnoreCase(flowNode.${flowNodeTypeField})
          && flowNode.${flowNodeCanceledField}
          && !flowNode.${flowNodeEndDateField}.isEmpty())
        ) {
          // update userTask metrics only if a userTask has been canceled and already has an endDate (imported via
          // CompletedUserTaskImport)
     """
        + createUpdateUserTaskMetricsScript()
        +
        """
        }
      """;
  }

  static String createRunningActivityInlineUpdateScript(final String updateUserTaskFiledsScript) {
    return
        """
          def existingFlowNodeInstancesById = ctx._source.${flowNodesField}.stream()
            .filter(n -> !"${userTaskFlowNodeType}".equalsIgnoreCase(n.${flowNodeTypeField}))
            .collect(Collectors.toMap(n -> n.${flowNodeInstanceIdField}, n -> n, (n1, n2) -> n1));
          def flowNodeInstancesToAddById = params.${flowNodesField}.stream()
            .filter(n -> !"${userTaskFlowNodeType}".equalsIgnoreCase(n.${flowNodeTypeField}))
            .filter(n -> !existingFlowNodeInstancesById.containsKey(n.${flowNodeInstanceIdField}))
            .collect(Collectors.toMap(n -> n.${flowNodeInstanceIdField}, n -> n, (n1, n2) -> n1));

          // For userTask flownodes, we cannot rely on the flowNodeInstanceId as identifier because the identityLinkLog only
          // has the userTaskID. Due to our IdentityLinkLogImport, it is possible to have userTasks with only userTaskId and
          // no flownodeId/FlownodeInstanceId. Also note flownodes that aren't userTasks do not have userTaskIDs.
          def existingUserTaskInstancesById = ctx._source.${flowNodesField}.stream()
            .filter(u -> "${userTaskFlowNodeType}".equalsIgnoreCase(u.${flowNodeTypeField}))
            .collect(Collectors.toMap(u -> u.${userTaskIdField}, u -> u, (u1, u2) -> u1));

          def userTaskInstancesToAddById = params.${flowNodesField}.stream()
            .filter(u -> "${userTaskFlowNodeType}".equalsIgnoreCase(u.${flowNodeTypeField}))
            .filter(u -> !existingUserTaskInstancesById.containsKey(u.${userTaskIdField}))
            .collect(Collectors.toMap(u -> u.${userTaskIdField}, u -> u, (u1, u2) -> u1));

          for (def newFlowNode : params.${flowNodesField}) {
            // Ignore flowNodes that aren't userTasks
            if(!"${userTaskFlowNodeType}".equalsIgnoreCase(newFlowNode.${flowNodeTypeField})){ continue; }

            def existingTask = existingUserTaskInstancesById.get(newFlowNode.${userTaskIdField});
            if (existingTask != null) {
        """
        + updateUserTaskFiledsScript
        +
        """
            } else {
              existingUserTaskInstancesById.put(newFlowNode.${userTaskIdField}, newFlowNode);
            }
          }

           ctx._source.${flowNodesField}.addAll(flowNodeInstancesToAddById.values());
           ctx._source.${flowNodesField}.addAll(userTaskInstancesToAddById.values());
        """;
  }
}
