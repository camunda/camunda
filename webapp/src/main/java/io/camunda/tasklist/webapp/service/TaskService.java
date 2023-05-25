/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.service;

import static io.camunda.tasklist.Metrics.*;
import static io.camunda.tasklist.util.CollectionUtil.countNonNullObjects;
import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.es.TaskReaderWriter;
import io.camunda.tasklist.webapp.es.TaskValidator;
import io.camunda.tasklist.webapp.es.contract.UsageMetricsContract;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.AssigneeMigrator;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskService {

  @Autowired private UserReader userReader;

  @Autowired private ZeebeClient zeebeClient;
  @Autowired private TaskReaderWriter taskReaderWriter;

  @Autowired private VariableService variableService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private Metrics metrics;

  @Autowired private UsageMetricsContract metricsContract;

  @Autowired private AssigneeMigrator assigneeMigrator;

  @Autowired private TaskValidator taskValidator;

  public List<TaskDTO> getTasks(TaskQueryDTO query, List<String> fieldNames) {
    if (countNonNullObjects(
            query.getSearchAfter(), query.getSearchAfterOrEqual(),
            query.getSearchBefore(), query.getSearchBeforeOrEqual())
        > 1) {
      throw new InvalidRequestException(
          "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");
    }
    return taskReaderWriter.getTasks(query, fieldNames);
  }

  public TaskDTO getTask(String taskId, List<String> fieldNames) {
    return taskReaderWriter.getTaskDTO(taskId, fieldNames);
  }

  public TaskDTO assignTask(String taskId, String assignee, Boolean allowOverrideAssignment) {
    if (allowOverrideAssignment == null) {
      allowOverrideAssignment = true;
    }

    final UserDTO currentUser = getCurrentUser();
    if (StringUtils.isEmpty(assignee) && currentUser.isApiUser()) {
      throw new InvalidRequestException("Assignee must be specified");
    }

    if (StringUtils.isNotEmpty(assignee)
        && !currentUser.isApiUser()
        && !assignee.equals(currentUser.getUserId())) {
      throw new ForbiddenActionException(
          "User doesn't have the permission to assign another user to this task");
    }

    final TaskEntity taskBefore = taskReaderWriter.getTask(taskId);
    taskValidator.validateCanAssign(taskBefore, allowOverrideAssignment);

    final String taskAssignee = determineTaskAssignee(assignee);
    final TaskEntity claimedTask = taskReaderWriter.persistTaskClaim(taskBefore, taskAssignee);

    updateClaimedMetric(claimedTask);
    return TaskDTO.createFrom(claimedTask, objectMapper);
  }

  private String determineTaskAssignee(String assignee) {
    final UserDTO currentUser = getCurrentUser();
    return StringUtils.isEmpty(assignee) && !currentUser.isApiUser()
        ? currentUser.getUserId()
        : assignee;
  }

  public TaskDTO completeTask(String taskId, List<VariableInputDTO> variables) {
    final Map<String, Object> variablesMap = new HashMap<>();

    requireNonNullElse(variables, Collections.<VariableInputDTO>emptyList())
        .forEach(
            variable -> {
              variablesMap.put(variable.getName(), this.extractTypedValue(variable));
            });

    final TaskEntity task = taskReaderWriter.getTask(taskId);
    taskValidator.validateCanComplete(task);

    // complete
    CompleteJobCommandStep1 completeJobCommand =
        zeebeClient.newCompleteCommand(Long.parseLong(taskId));
    completeJobCommand = completeJobCommand.variables(variablesMap);
    completeJobCommand.send().join();

    // persist completion and variables
    final TaskEntity completedTaskEntity = taskReaderWriter.persistTaskCompletion(task);
    variableService.persistTaskVariables(taskId, variables);
    updateCompletedMetric(completedTaskEntity);
    return TaskDTO.createFrom(completedTaskEntity, objectMapper);
  }

  private Object extractTypedValue(VariableInputDTO variable) {
    try {
      return objectMapper.readValue(variable.getValue(), Object.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  public TaskDTO unassignTask(String taskId) {
    final TaskEntity taskBefore = taskReaderWriter.getTask(taskId);
    taskValidator.validateCanUnassign(taskBefore);

    return TaskDTO.createFrom(taskReaderWriter.persistTaskUnclaim(taskBefore), objectMapper);
  }

  private UserDTO getCurrentUser() {
    return userReader.getCurrentUser();
  }

  private void updateClaimedMetric(final TaskEntity task) {
    metrics.recordCounts(COUNTER_NAME_CLAIMED_TASKS, 1, getTaskMetricLabels(task));
  }

  private void updateCompletedMetric(final TaskEntity task) {
    metrics.recordCounts(COUNTER_NAME_COMPLETED_TASKS, 1, getTaskMetricLabels(task));
    assigneeMigrator.migrateUsageMetrics(getCurrentUser().getUserId());
    metricsContract.registerTaskCompleteEvent(task);
  }

  private String[] getTaskMetricLabels(final TaskEntity task) {
    return new String[] {
      TAG_KEY_BPMN_PROCESS_ID, task.getBpmnProcessId(),
      TAG_KEY_FLOW_NODE_ID, task.getFlowNodeBpmnId(),
      TAG_KEY_USER_ID, userReader.getCurrentUserId(),
      TAG_KEY_ORGANIZATION_ID, userReader.getCurrentOrganizationId()
    };
  }
}
