/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer.usertask;

import static io.camunda.optimize.dto.optimize.importing.UserTaskIdentityOperationType.CLAIM_OPERATION_TYPE;
import static io.camunda.optimize.dto.optimize.importing.UserTaskIdentityOperationType.UNCLAIM_OPERATION_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TOTAL_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE_OPERATIONS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.text.StringSubstitutor;

public final class UserTaskDurationScriptUtil {

  private UserTaskDurationScriptUtil() {}

  public static String createUpdateUserTaskMetricsScript() {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
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
                .build());

    // @formatter:off
    return substitutor.replace(
        "def updatedUserTaskIds = params.${flowNodesField}.stream()"
            + " .filter(flowNode -> flowNode.${flowNodeTypeField}.equalsIgnoreCase(\"${userTaskFlowNodeType}\"))"
            + " .map(flowNode -> flowNode.${userTaskInstanceIdField})"
            + " .collect(Collectors.toList());\n"
            + "if (!updatedUserTaskIds.isEmpty() && ctx._source.${flowNodesField} != null) {\n"
            + " def dateFormatter = new SimpleDateFormat(\"${dateFormatPattern}\");\n"
            + " for (def currentTask : ctx._source.${flowNodesField}) {\n"
            +
            // Only recalculate duration of userTasks which have been updated
            " if (!currentTask.${flowNodeTypeField}.equalsIgnoreCase(\"${userTaskFlowNodeType}\")"
            + "   || (currentTask.${flowNodeTypeField}.equalsIgnoreCase(\"${userTaskFlowNodeType}\")"
            + "   && !updatedUserTaskIds.contains(currentTask.${userTaskInstanceIdField}))) {\n"
            + "   continue;\n"
            + " }\n"
            + " def totalWorkTimeInMs = 0;\n"
            + " def totalIdleTimeInMs = 0;\n"
            + " def workTimeHasChanged = false;\n"
            + " def idleTimeHasChanged = false;\n"
            + " if (currentTask.${assigneeOperationsField} != null && !currentTask.${assigneeOperationsField}.isEmpty()) {\n"
            +
            // Collect all timestamps of unclaim operations, counting the startDate as the first and
            // the endDate as the last unclaim
            "   def allUnclaimTimestamps = currentTask.${assigneeOperationsField}.stream()\n"
            + "     .filter(operation -> \"${operationTypeUnclaim}\".equals(operation.operationType))\n"
            + "     .map(operation -> operation.timestamp)\n"
            + "     .map(dateFormatter::parse)"
            + "     .collect(Collectors.toList());\n"
            + "   Optional.ofNullable(currentTask.${startDateField})"
            + "     .map(dateFormatter::parse)\n"
            + "     .ifPresent(startDate -> allUnclaimTimestamps.add(startDate));\n"
            + "   Optional.ofNullable(currentTask.${endDateField})"
            + "     .map(dateFormatter::parse)\n"
            + "     .ifPresent(endDate -> allUnclaimTimestamps.add(endDate));\n"
            + "   allUnclaimTimestamps.sort(Comparator.naturalOrder());\n"
            +

            // Collect all timestamps of claim operations
            "   def allClaimTimestamps = currentTask.${assigneeOperationsField}.stream()\n"
            + "     .filter(operation -> \"${operationTypeClaim}\".equals(operation.operationType))\n"
            + "     .map(operation -> operation.timestamp)\n"
            + "     .map(dateFormatter::parse)\n"
            + "     .sorted(Comparator.naturalOrder())\n"
            + "     .collect(Collectors.toList());\n"
            +

            // Deduplicate consecutive CLAIMs: keep only the first CLAIM in each unclaimed window.
            // Consecutive CLAIMs (with no UNCLAIM between them) occur e.g. when the same user is
            // re-assigned without unclaiming first. Only the first CLAIM opens a new work period;
            // subsequent consecutive CLAIMs are redundant for duration calculation.
            "   def effectiveClaimTimestamps = new ArrayList();\n"
            + "   for (def claimTs : allClaimTimestamps) {\n"
            + "     if (effectiveClaimTimestamps.isEmpty()) {\n"
            + "       effectiveClaimTimestamps.add(claimTs);\n"
            + "     } else {\n"
            + "       def lastEffectiveClaim = effectiveClaimTimestamps.get(effectiveClaimTimestamps.size() - 1);\n"
            + "       boolean hasUnclaimBetween = false;\n"
            + "       for (def ut : allUnclaimTimestamps) {\n"
            + "         if (ut.getTime() > lastEffectiveClaim.getTime() && ut.getTime() < claimTs.getTime()) {\n"
            + "           hasUnclaimBetween = true;\n"
            + "           break;\n"
            + "         }\n"
            + "       }\n"
            + "       if (hasUnclaimBetween) { effectiveClaimTimestamps.add(claimTs); }\n"
            + "     }\n"
            + "   }\n"
            +

            // Calculate idle time, which is the sum of differences between claim and unclaim
            // timestamp pairs, ie (claim_n - unclaim_n).
            // Note there will always be at least one unclaim (startDate).
            "   for (def i = 0; i < allUnclaimTimestamps.size() && i < effectiveClaimTimestamps.size(); i++) {\n"
            + "     def unclaimDate = allUnclaimTimestamps.get(i);\n"
            + "     def claimDate = effectiveClaimTimestamps.get(i);\n"
            + "     totalIdleTimeInMs = totalIdleTimeInMs + (claimDate.getTime() - unclaimDate.getTime());\n"
            + "     idleTimeHasChanged = true;\n"
            + "   }\n"
            +

            // Calculate work time, which is the sum of differences between unclaim and previous
            // claim timestamp pairs, ie (unclaim_n+1 - claim_n).
            // Note the startDate is the first unclaim, so can be disregarded for this calculation.
            "   for (def i = 0; i < allUnclaimTimestamps.size() - 1 && i < effectiveClaimTimestamps.size(); i++) {\n"
            + "     def claimDate = effectiveClaimTimestamps.get(i);\n"
            + "     def unclaimDate = allUnclaimTimestamps.get(i + 1);\n"
            + "     totalWorkTimeInMs = totalWorkTimeInMs + (unclaimDate.getTime() - claimDate.getTime());\n"
            + "     workTimeHasChanged = true;\n"
            + "   }\n"
            +

            // Edge case: task was unclaimed and then completed without re-claiming
            // (== there are 2 more unclaims than effective claims)
            // --> add idle time from last "real" unclaim to end
            "   if (allUnclaimTimestamps.size() - effectiveClaimTimestamps.size() == 2) {\n"
            + "     def secondToLastUnclaim = allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 2);\n"
            + "     def lastUnclaim = allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 1);\n"
            + "     totalIdleTimeInMs = totalIdleTimeInMs + (lastUnclaim.getTime() - secondToLastUnclaim.getTime());\n"
            + "     idleTimeHasChanged = true;\n"
            + "   }\n"
            + " }\n"
            +

            // Edge case: no assignee operations exist but task was finished (task was completed or
            // canceled without claim)
            " else if (currentTask.${totalDurationInMsField} != null) {\n"
            + "   def wasCanceled = Boolean.TRUE.equals(currentTask.${canceledField});\n"
            + "   if (wasCanceled) {\n"
            +
            // Task was cancelled --> assumed to have been idle the entire time
            "     totalIdleTimeInMs = currentTask.${totalDurationInMsField};\n"
            + "     totalWorkTimeInMs = 0;\n"
            + "   } else {\n"
            +
            // Task was not canceled --> assumed to have been worked on the entire time (presumably
            // programmatically)
            "     totalIdleTimeInMs = 0;\n"
            + "     totalWorkTimeInMs = currentTask.${totalDurationInMsField};\n"
            + "   }\n"
            + "   workTimeHasChanged = true;\n"
            + "   idleTimeHasChanged = true;\n"
            + " }\n"
            +

            // Set work and idle time if they have been calculated. Otherwise, leave fields null.
            " if (idleTimeHasChanged) {\n"
            + "   currentTask.${idleDurationInMsField} = totalIdleTimeInMs;\n"
            + " }\n"
            + " if (workTimeHasChanged) {\n"
            + "   currentTask.${workDurationInMsField} = totalWorkTimeInMs;\n"
            + " }\n"
            + " }\n"
            + "}\n");
    // @formatter:on
  }
}
