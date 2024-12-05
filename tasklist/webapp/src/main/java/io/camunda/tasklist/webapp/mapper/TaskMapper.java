/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.mapper;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.dto.TaskDTO;
import io.camunda.tasklist.webapp.dto.TaskQueryDTO;
import io.camunda.tasklist.webapp.dto.VariableDTO;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

  public static final String TASK_DESCRIPTION = "taskContextDisplayName";

  @Autowired private ProcessCache processCache;

  public TaskSearchResponse toTaskSearchResponse(final TaskDTO taskDTO) {
    final TaskSearchResponse response =
        new TaskSearchResponse()
            .setId(taskDTO.getId())
            .setName(getName(taskDTO))
            .setTaskDefinitionId(taskDTO.getFlowNodeBpmnId())
            .setProcessName(getProcessName(taskDTO))
            .setCreationDate(taskDTO.getCreationTime())
            .setCompletionDate(taskDTO.getCompletionTime())
            .setAssignee(taskDTO.getAssignee())
            .setTaskState(taskDTO.getTaskState())
            .setSortValues(taskDTO.getSortValues())
            .setIsFirst(taskDTO.getIsFirst())
            .setFormKey(
                (taskDTO.getExternalFormReference() == null
                        || taskDTO.getExternalFormReference().isBlank())
                    ? taskDTO.getFormKey()
                    : taskDTO.getExternalFormReference())
            .setFormId(taskDTO.getFormId())
            .setFormVersion(taskDTO.getFormVersion())
            .setIsFormEmbedded(taskDTO.getIsFormEmbedded())
            .setProcessDefinitionKey(taskDTO.getProcessDefinitionId())
            .setProcessInstanceKey(taskDTO.getProcessInstanceId())
            .setTenantId(taskDTO.getTenantId())
            .setDueDate(taskDTO.getDueDate())
            .setFollowUpDate(taskDTO.getFollowUpDate())
            .setCandidateGroups(taskDTO.getCandidateGroups())
            .setCandidateUsers(taskDTO.getCandidateUsers())
            .setImplementation(taskDTO.getImplementation())
            .setPriority(taskDTO.getPriority());

    if (taskDTO.getVariables() != null) {
      final VariableSearchResponse[] allVariables =
          Stream.of(taskDTO.getVariables())
              .map(this::toVariableSearchResponse)
              .toArray(VariableSearchResponse[]::new);

      final VariableSearchResponse[] filteredVariables =
          Arrays.stream(allVariables)
              .filter(variable -> !variable.getName().equals(TASK_DESCRIPTION))
              .toArray(VariableSearchResponse[]::new);

      String taskDescriptions =
          Arrays.stream(allVariables)
              .filter(variable -> variable.getName().equals(TASK_DESCRIPTION))
              .map(VariableSearchResponse::getValue)
              .map(value -> value.replaceAll("\"", "")) // Remove quotes for FE
              .collect(Collectors.joining());

      if (taskDescriptions.isEmpty() || "null".equals(taskDescriptions)) {
        taskDescriptions = null;
      }

      response.setVariables(filteredVariables);
      response.setContext(taskDescriptions);
    }

    return response;
  }

  private VariableSearchResponse toVariableSearchResponse(final VariableDTO variableDTO) {
    return new VariableSearchResponse()
        .setId(variableDTO.getId())
        .setName(variableDTO.getName())
        .setValue(
            variableDTO.getIsValueTruncated()
                ? null
                : variableDTO
                    .getPreviewValue()) // Currently, for big variables, only truncated values are
        // included in the Task Search response. So, we avoid
        // retrieving the fullValue from the database and populate
        // the output value with previewValue if it is not
        // truncated.
        .setIsValueTruncated(variableDTO.getIsValueTruncated())
        .setPreviewValue(variableDTO.getPreviewValue());
  }

  public TaskResponse toTaskResponse(final TaskDTO taskDTO) {
    return new TaskResponse()
        .setId(taskDTO.getId())
        .setName(getName(taskDTO))
        .setTaskDefinitionId(taskDTO.getFlowNodeBpmnId())
        .setProcessName(getProcessName(taskDTO))
        .setCreationDate(taskDTO.getCreationTime())
        .setCompletionDate(taskDTO.getCompletionTime())
        .setAssignee(taskDTO.getAssignee())
        .setTaskState(taskDTO.getTaskState())
        .setFormKey(
            taskDTO.getExternalFormReference() == null
                    || taskDTO.getExternalFormReference().isBlank()
                ? taskDTO.getFormKey()
                : taskDTO.getExternalFormReference())
        .setFormId(taskDTO.getFormId())
        .setFormVersion(taskDTO.getFormVersion())
        .setIsFormEmbedded(taskDTO.getIsFormEmbedded())
        .setProcessDefinitionKey(taskDTO.getProcessDefinitionId())
        .setProcessInstanceKey(taskDTO.getProcessInstanceId())
        .setDueDate(taskDTO.getDueDate())
        .setFollowUpDate(taskDTO.getFollowUpDate())
        .setCandidateGroups(taskDTO.getCandidateGroups())
        .setCandidateUsers(taskDTO.getCandidateUsers())
        .setTenantId(taskDTO.getTenantId())
        .setImplementation(taskDTO.getImplementation())
        .setPriority(taskDTO.getPriority());
  }

  public TaskQueryDTO toTaskQuery(final TaskSearchRequest searchRequest) {
    return new TaskQueryDTO()
        .setState(searchRequest.getState())
        .setAssigned(searchRequest.getAssigned())
        .setAssignee(searchRequest.getAssignee())
        .setAssignees(searchRequest.getAssignees())
        .setTaskDefinitionId(searchRequest.getTaskDefinitionId())
        .setCandidateGroup(searchRequest.getCandidateGroup())
        .setCandidateGroups(searchRequest.getCandidateGroups())
        .setCandidateUser(searchRequest.getCandidateUser())
        .setCandidateUsers(searchRequest.getCandidateUsers())
        .setProcessDefinitionId(searchRequest.getProcessDefinitionKey())
        .setProcessInstanceId(searchRequest.getProcessInstanceKey())
        .setPageSize(searchRequest.getPageSize())
        .setDueDate(searchRequest.getDueDate())
        .setFollowUpDate(searchRequest.getFollowUpDate())
        .setTaskVariables(searchRequest.getTaskVariables())
        .setTenantIds(searchRequest.getTenantIds())
        .setSort(searchRequest.getSort())
        .setSearchAfter(searchRequest.getSearchAfter())
        .setSearchAfterOrEqual(searchRequest.getSearchAfterOrEqual())
        .setSearchBefore(searchRequest.getSearchBefore())
        .setSearchBeforeOrEqual(searchRequest.getSearchBeforeOrEqual())
        .setImplementation(searchRequest.getImplementation())
        .setPriority(searchRequest.getPriority());
  }

  public String getName(final TaskDTO task) {
    return defaultIfNull(
        processCache.getTaskName(task.getProcessDefinitionId(), task.getFlowNodeBpmnId()),
        task.getFlowNodeBpmnId());
  }

  public String getProcessName(final TaskDTO task) {
    return defaultIfNull(
        processCache.getProcessName(task.getProcessDefinitionId()), task.getBpmnProcessId());
  }
}
