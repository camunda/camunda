/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.mapper;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

  @Autowired private ProcessCache processCache;

  public TaskSearchResponse toTaskSearchResponse(TaskDTO taskDTO) {
    return new TaskSearchResponse()
        .setId(taskDTO.getId())
        .setName(this.getName(taskDTO))
        .setTaskDefinitionId(taskDTO.getFlowNodeBpmnId())
        .setProcessName(this.getProcessName(taskDTO))
        .setCreationDate(taskDTO.getCreationTime())
        .setCompletionDate(taskDTO.getCompletionTime())
        .setAssignee(taskDTO.getAssignee())
        .setTaskState(taskDTO.getTaskState())
        .setSortValues(taskDTO.getSortValues())
        .setIsFirst(taskDTO.getIsFirst())
        .setFormKey(taskDTO.getFormKey())
        .setProcessDefinitionKey(taskDTO.getProcessDefinitionId())
        .setProcessInstanceKey(taskDTO.getProcessInstanceId())
        .setDueDate(taskDTO.getDueDate())
        .setFollowUpDate(taskDTO.getFollowUpDate())
        .setCandidateGroups(taskDTO.getCandidateGroups())
        .setCandidateUsers(taskDTO.getCandidateUsers());
  }

  public TaskResponse toTaskResponse(TaskDTO taskDTO) {
    return new TaskResponse()
        .setId(taskDTO.getId())
        .setName(this.getName(taskDTO))
        .setTaskDefinitionId(taskDTO.getFlowNodeBpmnId())
        .setProcessName(this.getProcessName(taskDTO))
        .setCreationDate(taskDTO.getCreationTime())
        .setCompletionDate(taskDTO.getCompletionTime())
        .setAssignee(taskDTO.getAssignee())
        .setTaskState(taskDTO.getTaskState())
        .setFormKey(taskDTO.getFormKey())
        .setProcessDefinitionKey(taskDTO.getProcessDefinitionId())
        .setProcessInstanceKey(taskDTO.getProcessInstanceId())
        .setDueDate(taskDTO.getDueDate())
        .setFollowUpDate(taskDTO.getFollowUpDate())
        .setCandidateGroups(taskDTO.getCandidateGroups())
        .setCandidateUsers(taskDTO.getCandidateUsers());
  }

  public TaskQueryDTO toTaskQuery(TaskSearchRequest searchRequest) {
    return new TaskQueryDTO()
        .setState(searchRequest.getState())
        .setAssigned(searchRequest.getAssigned())
        .setAssignee(searchRequest.getAssignee())
        .setTaskDefinitionId(searchRequest.getTaskDefinitionId())
        .setCandidateGroup(searchRequest.getCandidateGroup())
        .setCandidateUser(searchRequest.getCandidateUser())
        .setProcessDefinitionId(searchRequest.getProcessDefinitionKey())
        .setProcessInstanceId(searchRequest.getProcessInstanceKey())
        .setPageSize(searchRequest.getPageSize())
        .setDueDate(searchRequest.getDueDate())
        .setFollowUpDate(searchRequest.getFollowUpDate())
        .setSort(searchRequest.getSort())
        .setSearchAfter(searchRequest.getSearchAfter())
        .setSearchAfterOrEqual(searchRequest.getSearchAfterOrEqual())
        .setSearchBefore(searchRequest.getSearchBefore())
        .setSearchBeforeOrEqual(searchRequest.getSearchBeforeOrEqual());
  }

  public String getName(TaskDTO task) {
    return defaultIfNull(
        processCache.getTaskName(task.getProcessDefinitionId(), task.getFlowNodeBpmnId()),
        task.getFlowNodeBpmnId());
  }

  public String getProcessName(TaskDTO task) {
    return defaultIfNull(
        processCache.getProcessName(task.getProcessDefinitionId()), task.getBpmnProcessId());
  }
}
