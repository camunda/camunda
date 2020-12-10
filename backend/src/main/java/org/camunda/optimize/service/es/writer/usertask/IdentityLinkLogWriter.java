/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.usertask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogType;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingByConcurrent;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_USER_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_GROUP_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE_OPERATIONS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUP_OPERATIONS;
import static org.camunda.optimize.service.util.importing.EngineConstants.IDENTITY_LINK_OPERATION_ADD;
import static org.camunda.optimize.service.util.importing.EngineConstants.IDENTITY_LINK_OPERATION_DELETE;

@Component
@Slf4j
public class IdentityLinkLogWriter extends AbstractUserTaskWriter<UserTaskInstanceDto> {

  private final OptimizeElasticsearchClient esClient;

  public IdentityLinkLogWriter(final OptimizeElasticsearchClient esClient,
                               final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public void importIdentityLinkLogs(final List<IdentityLinkLogEntryDto> identityLinkLogs) {
    final String importItemName = "identity link logs";
    log.debug("Writing [{}] {} to ES.", identityLinkLogs.size(), importItemName);

    final Map<String, List<IdentityLinkLogEntryDto>> identityLinksByTaskId = identityLinkLogs.stream()
      // we need to group by concurrent to make sure that the order is preserved
      .collect(groupingByConcurrent(IdentityLinkLogEntryDto::getTaskId));

    final List<UserTaskInstanceDto> userTaskInstances = new ArrayList<>();
    for (List<IdentityLinkLogEntryDto> identityLinkLogEntryDtoList : identityLinksByTaskId.values()) {
      final IdentityLinkLogEntryDto firstOperationEntry = identityLinkLogEntryDtoList.get(0);
      final List<AssigneeOperationDto> assigneeOperations = mapToAssigneeOperationDtos(identityLinkLogEntryDtoList);
      final List<CandidateGroupOperationDto> candidateGroupOperations =
        mapToCandidateGroupOperationDtos(identityLinkLogEntryDtoList);
      userTaskInstances.add(new UserTaskInstanceDto(
        firstOperationEntry.getTaskId(),
        firstOperationEntry.getProcessInstanceId(),
        firstOperationEntry.getEngine(),
        extractAssignee(assigneeOperations),
        extractCandidateGroups(candidateGroupOperations),
        assigneeOperations,
        candidateGroupOperations
      ));
    }

    final Map<String, List<UserTaskInstanceDto>> processInstanceIdToUserTasks = new HashMap<>();
    for (UserTaskInstanceDto userTask : userTaskInstances) {
      if (!processInstanceIdToUserTasks.containsKey(userTask.getProcessInstanceId())) {
        processInstanceIdToUserTasks.put(userTask.getProcessInstanceId(), new ArrayList<>());
      }
      processInstanceIdToUserTasks.get(userTask.getProcessInstanceId()).add(userTask);
    }

    final List<ImportRequestDto> importRequests = processInstanceIdToUserTasks.entrySet().stream()
      .map(entry -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(createUserTaskUpdateImportRequest(entry))
        .build())
      .collect(Collectors.toList());

    ElasticsearchWriterUtil.executeImportRequestsAsBulk(importItemName, importRequests);
  }

  private List<CandidateGroupOperationDto> mapToCandidateGroupOperationDtos(
    final List<IdentityLinkLogEntryDto> identityLinkLogs) {
    return identityLinkLogs.stream()
      .filter(entry -> IdentityLinkLogType.CANDIDATE.equals(entry.getType()))
      .map(logEntry -> new CandidateGroupOperationDto(
        logEntry.getId(),
        logEntry.getGroupId(),
        logEntry.getOperationType(),
        logEntry.getTimestamp()
      ))
      .collect(Collectors.toList());
  }

  private List<AssigneeOperationDto> mapToAssigneeOperationDtos(
    final List<IdentityLinkLogEntryDto> identityLinkLogs) {
    return identityLinkLogs.stream()
      .filter(entry -> IdentityLinkLogType.ASSIGNEE.equals(entry.getType()))
      .map(logEntry -> new AssigneeOperationDto(
        logEntry.getId(),
        logEntry.getUserId(),
        logEntry.getOperationType(),
        logEntry.getTimestamp()
      ))
      .collect(Collectors.toList());
  }

  private List<String> extractCandidateGroups(List<CandidateGroupOperationDto> identityLinkLogs) {
    List<String> candidates = new ArrayList<>();
    identityLinkLogs
      .forEach(logEntry -> {
        switch (logEntry.getOperationType()) {
          case IDENTITY_LINK_OPERATION_ADD:
            candidates.add(logEntry.getGroupId());
            break;
          case IDENTITY_LINK_OPERATION_DELETE:
            candidates.remove(logEntry.getGroupId());
            break;
          default:
            log.warn("Found unknown identity link operation type [{}]", logEntry.getOperationType());
        }
      });
    return candidates;
  }

  private String extractAssignee(List<AssigneeOperationDto> assigneeOps) {
    return assigneeOps.stream()
      // get last added assignee. In case the last two operations are add and delete and
      // both occurred at the same time, then ignore the delete and just take the add. We need to do
      // that since changing the assignee in tasklist results in those two operations, which will have the
      // exact same timestamp.
      .reduce((first, second) -> {
        boolean sameTimestampAndFirstIsAddOperation = first.getTimestamp().equals(second.getTimestamp()) &&
          IDENTITY_LINK_OPERATION_ADD.equals(first.getOperationType()) &&
          !IDENTITY_LINK_OPERATION_ADD.equals(second.getOperationType());
        return sameTimestampAndFirstIsAddOperation ? first : second;
      })
      .map(this::mapLogEntryToAssignee)
      .orElse(null);
  }

  private String mapLogEntryToAssignee(AssigneeOperationDto logEntry) {
    switch (logEntry.getOperationType()) {
      case IDENTITY_LINK_OPERATION_ADD:
        return logEntry.getUserId();
      case IDENTITY_LINK_OPERATION_DELETE:
        return null;
      default:
        log.warn("Found unknown identity link operation type [{}]", logEntry.getOperationType());
        return null;
    }
  }

  @Override
  protected String createInlineUpdateScript() {
    return
      // @formatter:off
      // 1 check for existing userTask
      "if (ctx._source.userTasks == null) ctx._source.userTasks = [];\n" +
      "def existingUserTasksById = ctx._source.userTasks.stream()" +
        ".collect(Collectors.toMap(task -> task.id, task -> task, (t1, t2) -> t1));\n" +
      "for (def currentUserTask : params.userTasks) {\n" +
        //
        "def existingTask = existingUserTasksById.get(currentUserTask.id);\n" +
        "if (existingTask != null) {\n" +
          // 2.1.1 if it exists add the assignee operation to the existing ones
          "def existingOperationsById = existingTask.assigneeOperations.stream()\n" +
            ".collect(Collectors.toMap(operation -> operation.id, operation -> operation));\n" +
          "currentUserTask.assigneeOperations.stream()\n" +
            ".forEachOrdered(operation -> existingOperationsById.putIfAbsent(operation.id, operation));\n" +
          "def assigneeOperations = new ArrayList(existingOperationsById.values());" +
          "Collections.sort(assigneeOperations, (o1, o2) -> o1.timestamp.compareTo(o2.timestamp));\n" +
          "existingTask.assigneeOperations = assigneeOperations;\n" +
          // 2.1.2 if it exists add the candidate group operation to the existing ones
          "existingOperationsById = existingTask.candidateGroupOperations.stream()\n" +
            ".collect(Collectors.toMap(operation -> operation.id, operation -> operation));\n" +
          "currentUserTask.candidateGroupOperations.stream()\n" +
            ".forEachOrdered(operation -> existingOperationsById.putIfAbsent(operation.id, operation));\n" +
          "def candidateOperations = new ArrayList(existingOperationsById.values());" +
          "Collections.sort(candidateOperations, (o1, o2) -> o1.timestamp.compareTo(o2.timestamp));\n" +
          "existingTask.candidateGroupOperations = candidateOperations;\n" +
        "} else {\n" +
          // 2.2 if it doesn't exist add it with id and assignee/candidate group operations set
          "existingUserTasksById.put(currentUserTask.id, currentUserTask);\n" +
        "}\n" +
      "}\n" +
      "ctx._source.userTasks = existingUserTasksById.values();\n" +
       createUpdateAssigneeScript() +
       createUpdateCandidateGroupScript() +
       createUpdateUserTaskMetricsScript();
  }

  private static String createUpdateAssigneeScript() {
    // @formatter:off
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
      .put("userTasksField", USER_TASKS)
      .put("assigneeField", USER_TASK_ASSIGNEE)
      .put("assigneeOperationsField", USER_TASK_ASSIGNEE_OPERATIONS)
      .put("assigneeOperationTypeField", ASSIGNEE_OPERATION_TYPE)
      .put("assigneeOperationUserIdField", ASSIGNEE_OPERATION_USER_ID)
      .put("identityLinkOperationAdd", IDENTITY_LINK_OPERATION_ADD)
      .build()
    );

    return substitutor.replace(
      "if (ctx._source.${userTasksField} != null) {\n" +
        "for (def currentTask : ctx._source.${userTasksField}) {\n" +
          "def assignee = currentTask.${assigneeOperationsField}.stream()\n" +
          "  .reduce((first, second) -> { \n" +
          "    boolean sameTimestampAndFirstIsAddOperation = first.timestamp.equals(second.timestamp) &&" +
          "      \"${identityLinkOperationAdd}\".equals(first.${assigneeOperationTypeField}) &&" +
          "      !\"${identityLinkOperationAdd}\".equals(second.${assigneeOperationTypeField});\n" +
          "     return sameTimestampAndFirstIsAddOperation? first: second;\n" +
          "})\n" +
          "  .map(logEntry -> {\n" +
          "    if(\"${identityLinkOperationAdd}\".equals(logEntry.${assigneeOperationTypeField})) {\n" +
          "      return logEntry.${assigneeOperationUserIdField};\n" +
          "    } else {\n" +
          "      return null;\n" +
          "    }\n" +
          "  })\n" +
          "  .orElse(null);\n" +
          "currentTask.${assigneeField} = assignee;\n" +
        "}\n" +
      "}\n"
    );
    // @formatter:on
  }

  private static String createUpdateCandidateGroupScript() {
    // @formatter:off
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
      .put("userTasksField", USER_TASKS)
      .put("candidateGroupsField", USER_TASK_CANDIDATE_GROUPS)
      .put("candidateGroupOperationsField", USER_TASK_CANDIDATE_GROUP_OPERATIONS)
      .put("candidateGroupOperationTypeField", CANDIDATE_GROUP_OPERATION_TYPE)
      .put("candidateGroupOperationGroupIdField", CANDIDATE_GROUP_OPERATION_GROUP_ID)
      .put("identityLinkOperationAdd", IDENTITY_LINK_OPERATION_ADD)
      .build()
    );

    return substitutor.replace(
      "if (ctx._source.${userTasksField} != null) {\n" +
        "for (def currentTask : ctx._source.${userTasksField}) {\n" +
          "Set candidateGroups = new HashSet();\n" +
          "currentTask.${candidateGroupOperationsField}.stream()\n" +
          "  .forEach(logEntry -> {\n" +
          "    if (\"${identityLinkOperationAdd}\".equals(logEntry.${candidateGroupOperationTypeField})) {\n" +
          "      candidateGroups.add(logEntry.${candidateGroupOperationGroupIdField});\n" +
          "    } else {\n" +
          "      candidateGroups.remove(logEntry.${candidateGroupOperationGroupIdField});\n" +
          "    }\n" +
          "  });\n" +
          "currentTask.${candidateGroupsField} = new ArrayList(candidateGroups);\n" +
        "}\n" +
      "}\n"
    );
    // @formatter:on
  }

}
