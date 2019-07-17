/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.groupingByConcurrent;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ASSIGNEE_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ASSIGNEE_OPERATION_USER_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.CANDIDATE_GROUP_OPERATION_GROUP_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.CANDIDATE_GROUP_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASKS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ASSIGNEE_OPERATIONS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_CANDIDATE_GROUPS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_CANDIDATE_GROUP_OPERATIONS;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.IDENTITY_LINK_OPERATION_ADD;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.IDENTITY_LINK_OPERATION_DELETE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.IDENTITY_LINK_TYPE_ASSIGNEE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.IDENTITY_LINK_TYPE_CANDIDATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

@Component
@Slf4j
public class IdentityLinkLogWriter extends AbstractUserTaskWriter {

  private final OptimizeElasticsearchClient esClient;

  public IdentityLinkLogWriter(final OptimizeElasticsearchClient esClient,
                               final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public void importIdentityLinkLogs(final List<IdentityLinkLogEntryDto> identityLinkLogs) throws Exception {
    log.debug("Writing [{}] identity link logs to elasticsearch", identityLinkLogs.size());

    final BulkRequest userTaskToProcessInstanceBulkRequest = new BulkRequest();
    userTaskToProcessInstanceBulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

    final Map<String, List<IdentityLinkLogEntryDto>> identityLinksByTaskId = identityLinkLogs.stream()
      // we need to group by concurrent to make sure that the order is preserved
      .collect(groupingByConcurrent(IdentityLinkLogEntryDto::getTaskId));

    final Map<String, List<UserTaskInstanceDto>> userTasksByProcessInstance = identityLinksByTaskId
      .values()
      .stream()
      .map(identityLinkLogEntryDtos -> {
        final IdentityLinkLogEntryDto firstOperationEntry = identityLinkLogEntryDtos.get(0);
        List<AssigneeOperationDto> assigneeOperations = mapToAssigneeOperationDtos(identityLinkLogEntryDtos);
        List<CandidateGroupOperationDto> candidateGroupOperations = mapToCandidateGroupOperationDtos(
          identityLinkLogEntryDtos);
        return new UserTaskInstanceDto(
          firstOperationEntry.getTaskId(),
          firstOperationEntry.getProcessInstanceId(),
          firstOperationEntry.getEngine(),
          extractAssignee(assigneeOperations),
          extractCandidateGroups(candidateGroupOperations),
          assigneeOperations,
          candidateGroupOperations
        );
      })
      .collect(groupingBy(UserTaskInstanceDto::getProcessInstanceId));

    for (Map.Entry<String, List<UserTaskInstanceDto>> processInstanceEntry : userTasksByProcessInstance.entrySet()) {
      addUserTaskToProcessInstanceRequest(
        userTaskToProcessInstanceBulkRequest,
        processInstanceEntry.getKey(),
        processInstanceEntry.getValue()
      );
    }

    final BulkResponse bulkResponse = esClient.bulk(userTaskToProcessInstanceBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      String errorMessage = String.format(
        "There were failures while writing identity link logs with message: %s",
        bulkResponse.buildFailureMessage()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }

  }

  private List<CandidateGroupOperationDto> mapToCandidateGroupOperationDtos(List<IdentityLinkLogEntryDto> identityLinkLogs) {
    return identityLinkLogs.stream()
      .filter(entry -> Objects.equals(IDENTITY_LINK_TYPE_CANDIDATE, entry.getType()))
      .map(logEntry -> new CandidateGroupOperationDto(
        logEntry.getId(),
        logEntry.getGroupId(),
        logEntry.getOperationType(),
        logEntry.getTimestamp()
      ))
      .collect(Collectors.toList());
  }

  private List<AssigneeOperationDto> mapToAssigneeOperationDtos(final List<IdentityLinkLogEntryDto> identityLinkLogs) {
    return identityLinkLogs.stream()
      .filter(entry -> IDENTITY_LINK_TYPE_ASSIGNEE.equals(entry.getType()))
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
    Set set = new HashSet();
    return candidates;
  }

  private String extractAssignee(List<AssigneeOperationDto> assigneeOps) {
    return assigneeOps.stream()
      // add, there is no assignee at all
      // get last added assignee
      .reduce((first, second) -> second)
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

  private void addUserTaskToProcessInstanceRequest(final BulkRequest bulkRequest,
                                                   final String processInstanceId,
                                                   final List<UserTaskInstanceDto> userTasks)
    throws IOException {
    final Script updateScript = createUpdateScript(userTasks);

    final UserTaskInstanceDto firstUserTaskInstance = userTasks.stream().findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("No user tasks to import provided"));
    final ProcessInstanceDto procInst = new ProcessInstanceDto()
      .setProcessInstanceId(firstUserTaskInstance.getProcessInstanceId())
      .setEngine(firstUserTaskInstance.getEngine())
      .setUserTasks(userTasks);
    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    UpdateRequest request = new UpdateRequest(PROC_INSTANCE_TYPE, PROC_INSTANCE_TYPE, processInstanceId)
      .script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

  private Script createUpdateScript(final List<UserTaskInstanceDto> userTasksWithAssigneeAndCandidateGroups) {
    return createDefaultScript(
      // @formatter:off
      // 1 check for existing userTask
      "if (ctx._source.userTasks == null) ctx._source.userTasks = [];\n" +
      "def existingUserTasksById = ctx._source.userTasks.stream()" +
        ".collect(Collectors.toMap(task -> task.id, task -> task));\n" +
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
          "ctx._source.userTasks.add(currentUserTask);\n" +
        "}\n" +
      "}\n" +
       createUpdateAssigneeScript() +
       createUpdateCandidateGroupScript()
      ,
      // @formatter:on
      ImmutableMap.of(
        USER_TASKS, mapToParameterSet(userTasksWithAssigneeAndCandidateGroups)
      )
    );
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
          "  .reduce((first, second) -> second)\n" +
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