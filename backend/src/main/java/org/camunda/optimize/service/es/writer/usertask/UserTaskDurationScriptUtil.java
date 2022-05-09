/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer.usertask;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.text.StringSubstitutor;

import static org.camunda.optimize.dto.optimize.importing.IdentityLinkLogOperationType.CLAIM_OPERATION_TYPE;
import static org.camunda.optimize.dto.optimize.importing.IdentityLinkLogOperationType.UNCLAIM_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE_OPERATIONS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserTaskDurationScriptUtil {

  public static String createUpdateUserTaskMetricsScript() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("flowNodesField", FLOW_NODE_INSTANCES)
        .put("flowNodeTypeField", FLOW_NODE_TYPE)
        .put("userTaskInstanceIdField", USER_TASK_INSTANCE_ID)
        .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
        .put("assigneeOperationsField", USER_TASK_ASSIGNEE_OPERATIONS)
        .put("startDateField", FLOW_NODE_START_DATE)
        .put("endDateField", FLOW_NODE_END_DATE)
        .put("idleDurationInMsField", USER_TASK_IDLE_DURATION)
        .put("workDurationInMsField", USER_TASK_WORK_DURATION)
        .put("totalDurationInMsField", FLOW_NODE_TOTAL_DURATION)
        .put("canceledField", FLOW_NODE_CANCELED)
        .put("operationTypeClaim", CLAIM_OPERATION_TYPE.getId())
        .put("operationTypeUnclaim", UNCLAIM_OPERATION_TYPE.getId())
        .put("dateFormatPattern", OPTIMIZE_DATE_FORMAT)
        .build()
    );

    // @formatter:off
    return substitutor.replace(
      "def updatedUserTaskIds = params.${flowNodesField}.stream()" +
        " .filter(flowNode -> flowNode.${flowNodeTypeField}.equalsIgnoreCase(\"${userTaskFlowNodeType}\"))" +
        " .map(flowNode -> flowNode.${userTaskInstanceIdField})" +
        " .collect(Collectors.toList());\n" +

        "if (!updatedUserTaskIds.isEmpty() && ctx._source.${flowNodesField} != null) {\n" +
        " def dateFormatter = new SimpleDateFormat(\"${dateFormatPattern}\");\n" +

        " for (def currentTask : ctx._source.${flowNodesField}) {\n" +
        // Only recalculate duration of userTasks which have been updated
        " if (!currentTask.${flowNodeTypeField}.equalsIgnoreCase(\"${userTaskFlowNodeType}\")" +
        "   || (currentTask.${flowNodeTypeField}.equalsIgnoreCase(\"${userTaskFlowNodeType}\")" +
        "   && !updatedUserTaskIds.contains(currentTask.${userTaskInstanceIdField}))) {\n" +
        "   continue;\n" +
        " }\n" +

        " def totalWorkTimeInMs = 0;\n" +
        " def totalIdleTimeInMs = 0;\n" +
        " def workTimeHasChanged = false;\n" +
        " def idleTimeHasChanged = false;\n" +

        " if (currentTask.${assigneeOperationsField} != null && !currentTask.${assigneeOperationsField}.isEmpty()) {\n" +
        // Collect all timestamps of unclaim operations, counting the startDate as the first and the endDate as the last unclaim
        "   def allUnclaimTimestamps = currentTask.${assigneeOperationsField}.stream()\n" +
        "     .filter(operation -> \"${operationTypeUnclaim}\".equals(operation.operationType))\n" +
        "     .map(operation -> operation.timestamp)\n" +
        "     .map(dateFormatter::parse)" +
        "     .collect(Collectors.toList());\n" +
        "   Optional.ofNullable(currentTask.${startDateField})" +
        "     .map(dateFormatter::parse)\n" +
        "     .ifPresent(startDate -> allUnclaimTimestamps.add(startDate));\n" +
        "   Optional.ofNullable(currentTask.${endDateField})" +
        "     .map(dateFormatter::parse)\n" +
        "     .ifPresent(endDate -> allUnclaimTimestamps.add(endDate));\n" +
        "   allUnclaimTimestamps.sort(Comparator.naturalOrder());\n" +

        // Collect all timestamps of claim operations
        "   def allClaimTimestamps = currentTask.${assigneeOperationsField}.stream()\n" +
        "     .filter(operation -> \"${operationTypeClaim}\".equals(operation.operationType))\n" +
        "     .map(operation -> operation.timestamp)\n" +
        "     .map(dateFormatter::parse)\n" +
        "     .sorted(Comparator.naturalOrder())\n" +
        "     .collect(Collectors.toList());\n" +

        // Calculate idle time, which is the sum of differences between claim and unclaim timestamp pairs, ie (claim_n - unclaim_n)
        // Note there will always be at least one unclaim (startDate)
        "   for (def i = 0; i < allUnclaimTimestamps.size() &&  i < allClaimTimestamps.size(); i++) {\n" +
        "     def unclaimDate = allUnclaimTimestamps.get(i);\n" +
        "     def claimDate= allClaimTimestamps.get(i);\n" +
        "     def idleTimeToAdd = claimDate.getTime() - unclaimDate.getTime();\n" +
        "     totalIdleTimeInMs = totalIdleTimeInMs + idleTimeToAdd;\n" +
        "     idleTimeHasChanged = true;\n" +
        "   }\n" +

        // Calculate work time, which is the sum of differences between unclaim and previous claim timestamp pairs, ie (unclaim_n+1 - claim_n)
        // Note the startDate is the first unclaim, so can be disregarded for this calculation
        "   for (def i = 0; i < allUnclaimTimestamps.size() - 1 &&  i < allClaimTimestamps.size(); i++) {\n" +
        "     def claimDate = allClaimTimestamps.get(i);\n" +
        "     def unclaimDate = allUnclaimTimestamps.get(i + 1);\n" +
        "     def workTimeToAdd = unclaimDate.getTime() - claimDate.getTime();\n" +
        "     totalWorkTimeInMs = totalWorkTimeInMs + workTimeToAdd;\n" +
        "     workTimeHasChanged = true;\n" +
        "   }\n" +

        // Edge case: task was unclaimed and then completed without claim (== there are 2 more unclaims than claims)
        // --> add time between end and last "real" unclaim as idle time
        "   if (allUnclaimTimestamps.size() - allClaimTimestamps.size() == 2) {\n" +
        "     def lastUnclaim = allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 1);\n" +
        "     def secondToLastUnclaim = allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 2);\n" +
        "     totalIdleTimeInMs = totalIdleTimeInMs + (lastUnclaim.getTime() - secondToLastUnclaim.getTime());\n" +
        "     idleTimeHasChanged = true;\n" +
        "   }\n" +
        " }\n" +

        // Edge case: no assignee operations exist but task was finished (task was completed or canceled without claim)
        " else if (currentTask.${totalDurationInMsField} != null) {\n" +
        "   def wasCanceled = Boolean.TRUE.equals(currentTask.${canceledField});\n" +
        "   if (wasCanceled) {\n" +
        // Task was cancelled --> assumed to have been idle the entire time
        "     totalIdleTimeInMs = currentTask.${totalDurationInMsField};\n" +
        "     totalWorkTimeInMs = 0;\n" +
        "   } else {\n" +
        // Task was not canceled --> assumed to have been worked on the entire time (presumably programmatically)
        "     totalIdleTimeInMs = 0;\n" +
        "     totalWorkTimeInMs = currentTask.${totalDurationInMsField};\n" +
        "   }\n" +
        "   workTimeHasChanged = true;\n" +
        "   idleTimeHasChanged = true;\n" +
        " }\n" +

        // Set work and idle time if they have been calculated. Otherwise, leave fields null.
        " if (idleTimeHasChanged) {\n" +
        "   currentTask.${idleDurationInMsField} = totalIdleTimeInMs;\n" +
        " }\n" +
        " if (workTimeHasChanged) {\n" +
        "   currentTask.${workDurationInMsField} = totalWorkTimeInMs;\n" +
        " }\n" +
        " }\n" +
        "}\n"
    );
    // @formatter:on
  }
}
