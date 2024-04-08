/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.usertask;

import static java.util.stream.Collectors.groupingByConcurrent;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.service.db.es.writer.usertask.UserTaskDurationScriptUtil.createUpdateUserTaskMetricsScript;
import static org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder.PROCESS_INSTANCE_INDEX;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_TIMESTAMP;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_TYPE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_USER_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_GROUP_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_TIMESTAMP;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_TYPE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TYPE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE_OPERATIONS;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUP_OPERATIONS;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;
import static org.camunda.optimize.service.util.importing.EngineConstants.IDENTITY_LINK_OPERATION_ADD;
import static org.camunda.optimize.service.util.importing.EngineConstants.IDENTITY_LINK_OPERATION_DELETE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogType;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.repository.IndexRepository;
import org.camunda.optimize.service.db.repository.script.UserTaskScriptFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IdentityLinkLogWriter extends AbstractUserTaskWriter {
  public IdentityLinkLogWriter(
      final IndexRepository indexRepository, final ObjectMapper objectMapper) {
    super(indexRepository, objectMapper);
  }

  public List<ImportRequestDto> generateIdentityLinkLogImports(
      final List<IdentityLinkLogEntryDto> identityLinkLogs) {
    final String importItemName = "identity link logs";
    log.debug("Writing [{}] {} to database.", identityLinkLogs.size(), importItemName);

    final Map<String, List<IdentityLinkLogEntryDto>> identityLinksByTaskId =
        identityLinkLogs.stream()
            // we need to group by concurrent to make sure that the order is preserved
            .collect(groupingByConcurrent(IdentityLinkLogEntryDto::getTaskId));

    final List<FlowNodeInstanceDto> userTaskInstances = new ArrayList<>();
    for (List<IdentityLinkLogEntryDto> identityLinkLogEntryDtoList :
        identityLinksByTaskId.values()) {
      final IdentityLinkLogEntryDto firstOperationEntry = identityLinkLogEntryDtoList.get(0);
      final List<AssigneeOperationDto> assigneeOperations =
          mapToAssigneeOperationDtos(identityLinkLogEntryDtoList);
      final List<CandidateGroupOperationDto> candidateGroupOperations =
          mapToCandidateGroupOperationDtos(identityLinkLogEntryDtoList);
      userTaskInstances.add(
          new FlowNodeInstanceDto(
                  firstOperationEntry.getProcessDefinitionKey(),
                  firstOperationEntry.getEngine(),
                  firstOperationEntry.getProcessInstanceId(),
                  firstOperationEntry.getTaskId())
              .setAssignee(extractAssignee(assigneeOperations))
              .setCandidateGroups(extractCandidateGroups(candidateGroupOperations))
              .setAssigneeOperations(assigneeOperations)
              .setCandidateGroupOperations(candidateGroupOperations));
    }

    final Map<String, List<FlowNodeInstanceDto>> processInstanceIdToUserTasks = new HashMap<>();
    for (FlowNodeInstanceDto userTask : userTaskInstances) {
      processInstanceIdToUserTasks.putIfAbsent(userTask.getProcessInstanceId(), new ArrayList<>());
      processInstanceIdToUserTasks.get(userTask.getProcessInstanceId()).add(userTask);
    }

    final Set<String> keys =
        userTaskInstances.stream()
            .map(FlowNodeInstanceDto::getDefinitionKey)
            .collect(Collectors.toSet());
    indexRepository.createMissingIndices(
        PROCESS_INSTANCE_INDEX, Set.of(PROCESS_INSTANCE_MULTI_ALIAS), keys);

    return processInstanceIdToUserTasks.entrySet().stream()
        .map(entry -> createUserTaskUpdateImportRequest(entry, importItemName))
        .collect(Collectors.toList());
  }

  private List<CandidateGroupOperationDto> mapToCandidateGroupOperationDtos(
      final List<IdentityLinkLogEntryDto> identityLinkLogs) {
    return identityLinkLogs.stream()
        .filter(entry -> IdentityLinkLogType.CANDIDATE.equals(entry.getType()))
        .map(
            logEntry ->
                new CandidateGroupOperationDto(
                    logEntry.getId(),
                    logEntry.getGroupId(),
                    logEntry.getOperationType(),
                    logEntry.getTimestamp()))
        .collect(Collectors.toList());
  }

  private List<AssigneeOperationDto> mapToAssigneeOperationDtos(
      final List<IdentityLinkLogEntryDto> identityLinkLogs) {
    return identityLinkLogs.stream()
        .filter(entry -> IdentityLinkLogType.ASSIGNEE.equals(entry.getType()))
        .map(
            logEntry ->
                new AssigneeOperationDto(
                    logEntry.getId(),
                    logEntry.getUserId(),
                    logEntry.getOperationType(),
                    logEntry.getTimestamp()))
        .collect(Collectors.toList());
  }

  private List<String> extractCandidateGroups(
      final List<CandidateGroupOperationDto> identityLinkLogs) {
    List<String> candidates = new ArrayList<>();
    identityLinkLogs.forEach(
        logEntry -> {
          switch (logEntry.getOperationType()) {
            case IDENTITY_LINK_OPERATION_ADD -> candidates.add(logEntry.getGroupId());
            case IDENTITY_LINK_OPERATION_DELETE -> candidates.remove(logEntry.getGroupId());
            default ->
                log.warn(
                    "Found unknown identity link operation type [{}]", logEntry.getOperationType());
          }
        });
    return candidates;
  }

  private String extractAssignee(final List<AssigneeOperationDto> assigneeOps) {
    return assigneeOps.stream()
        // get last added assignee. In case the last two operations are add and delete and
        // both occurred at the same time, then ignore the delete and just take the add. We need to
        // do that since changing the assignee in tasklist results in those two operations, which
        // will have the exact same timestamp.
        .reduce(
            (first, second) -> {
              boolean sameTimestampAndFirstIsAddOperation =
                  first.getTimestamp().equals(second.getTimestamp())
                      && IDENTITY_LINK_OPERATION_ADD.equals(first.getOperationType())
                      && !IDENTITY_LINK_OPERATION_ADD.equals(second.getOperationType());
              return sameTimestampAndFirstIsAddOperation ? first : second;
            })
        .map(this::mapLogEntryToAssignee)
        .orElse(null);
  }

  private String mapLogEntryToAssignee(final AssigneeOperationDto logEntry) {
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
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("flowNodesField", FLOW_NODE_INSTANCES)
                .put("flowNodeTypeField", FLOW_NODE_TYPE)
                .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
                .put("userTaskIdField", USER_TASK_INSTANCE_ID)
                .put("assigneeOperationsField", USER_TASK_ASSIGNEE_OPERATIONS)
                .put("assigneeOpIdField", ASSIGNEE_OPERATION_ID)
                .put("assigneeOpTimestampField", ASSIGNEE_OPERATION_TIMESTAMP)
                .put("candidateGroupOperationsField", USER_TASK_CANDIDATE_GROUP_OPERATIONS)
                .put("candidateGroupOpIdField", CANDIDATE_GROUP_OPERATION_ID)
                .put("candidateGroupOpTimestampField", CANDIDATE_GROUP_OPERATION_TIMESTAMP)
                .build());

    return substitutor.replace(UserTaskScriptFactory.createIdentityLinkLogInlineUpdateScript())
        + createUpdateAssigneeScript()
        + createUpdateCandidateGroupScript()
        + createUpdateUserTaskMetricsScript();
  }

  private static String createUpdateAssigneeScript() {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("flowNodesField", FLOW_NODE_INSTANCES)
                .put("flowNodeTypeField", FLOW_NODE_TYPE)
                .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
                .put("assigneeField", USER_TASK_ASSIGNEE)
                .put("assigneeOperationsField", USER_TASK_ASSIGNEE_OPERATIONS)
                .put("assigneeOpTimestampField", ASSIGNEE_OPERATION_TIMESTAMP)
                .put("assigneeOperationTypeField", ASSIGNEE_OPERATION_TYPE)
                .put("assigneeOperationUserIdField", ASSIGNEE_OPERATION_USER_ID)
                .put("identityLinkOperationAdd", IDENTITY_LINK_OPERATION_ADD)
                .build());

    return substitutor.replace(UserTaskScriptFactory.createIdentityLinkLogUpdateAssigneeScript());
  }

  private static String createUpdateCandidateGroupScript() {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("flowNodesField", FLOW_NODE_INSTANCES)
                .put("flowNodeTypeField", FLOW_NODE_TYPE)
                .put("userTaskFlowNodeType", FLOW_NODE_TYPE_USER_TASK)
                .put("candidateGroupsField", USER_TASK_CANDIDATE_GROUPS)
                .put("candidateGroupOperationsField", USER_TASK_CANDIDATE_GROUP_OPERATIONS)
                .put("candidateGroupOperationTypeField", CANDIDATE_GROUP_OPERATION_TYPE)
                .put("candidateGroupOperationGroupIdField", CANDIDATE_GROUP_OPERATION_GROUP_ID)
                .put("identityLinkOperationAdd", IDENTITY_LINK_OPERATION_ADD)
                .build());

    return substitutor.replace(
        UserTaskScriptFactory.createIdentityLinkLogUpdateCandidateGroupScript());
  }
}
