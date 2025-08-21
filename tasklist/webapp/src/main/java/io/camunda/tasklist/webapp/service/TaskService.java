/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import static io.camunda.tasklist.Metrics.*;
import static io.camunda.tasklist.util.CollectionUtil.countNonNullObjects;
import static io.camunda.tasklist.webapp.service.OrganizationService.DEFAULT_ORGANIZATION;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.Auth0Properties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.FormStore.FormIdView;
import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.tasklist.webapp.dto.TaskDTO;
import io.camunda.tasklist.webapp.dto.TaskQueryDTO;
import io.camunda.tasklist.webapp.dto.VariableDTO;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.es.TaskValidator;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.TasklistAuthenticationUtil;
import io.camunda.tasklist.zeebe.TasklistServicesAdapter;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

@Component
public class TaskService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskService.class);
  private static final String DEFAULT_USER = "No name";

  @Autowired private CamundaAuthenticationProvider authenticationProvider;
  @Autowired private TaskStore taskStore;
  @Autowired private VariableService variableService;
  @Autowired private FormStore formStore;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private Metrics metrics;
  @Autowired private TaskMetricsStore taskMetricsStore;
  @Autowired private TaskValidator taskValidator;
  @Autowired private TasklistServicesAdapter tasklistServicesAdapter;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private OrganizationService organizationService;

  public List<TaskDTO> getTasks(final TaskQueryDTO query) {
    return getTasks(query, emptySet(), false);
  }

  public List<TaskDTO> getTasks(
      final TaskQueryDTO query,
      final Set<String> includeVariableNames,
      final boolean fetchFullValuesFromDB) {
    if (countNonNullObjects(
            query.getSearchAfter(), query.getSearchAfterOrEqual(),
            query.getSearchBefore(), query.getSearchBeforeOrEqual())
        > 1) {
      throw new InvalidRequestException(
          "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");
    }

    if (query.getPageSize() <= 0) {
      throw new InvalidRequestException("Page size should be a positive number");
    }

    if (query.getImplementation() != null
        && !query.getImplementation().equals(TaskImplementation.ZEEBE_USER_TASK)
        && !query.getImplementation().equals(TaskImplementation.JOB_WORKER)) {
      throw new InvalidRequestException(
          String.format(
              "Invalid implementation, the valid values are %s and %s",
              TaskImplementation.ZEEBE_USER_TASK, TaskImplementation.JOB_WORKER));
    }

    final List<TaskSearchView> tasks = taskStore.getTasks(query.toTaskQuery());
    final Set<String> fieldNames =
        fetchFullValuesFromDB
            ? emptySet()
            : Set.of(
                "id",
                "name",
                "previewValue",
                "isValueTruncated"); // use fieldNames to not fetch fullValue from DB
    final Map<String, List<VariableDTO>> variablesPerTaskId =
        CollectionUtils.isEmpty(includeVariableNames)
            ? Collections.emptyMap()
            : variableService.getVariablesPerTaskId(
                tasks.stream()
                    .map(
                        taskView ->
                            VariableStore.GetVariablesRequest.createFrom(
                                taskView, new ArrayList<>(includeVariableNames), fieldNames))
                    .toList());
    return tasks.stream()
        .map(
            it ->
                TaskDTO.createFrom(
                    it,
                    Optional.ofNullable(variablesPerTaskId.get(it.getId()))
                        .map(list -> list.toArray(new VariableDTO[list.size()]))
                        .orElse(null),
                    objectMapper))
        .toList();
  }

  public TaskDTO getTask(final String taskId) {
    final TaskEntity task = taskStore.getTask(taskId);
    if (taskFormLinkIsNotComplete(task)) {
      LOGGER.debug(
          "Task with id {} found having incorrect form linking to form with key {}",
          taskId,
          task.getFormKey());

      final Optional<FormIdView> linkedForm = formStore.getFormByKey(task.getFormKey());

      linkedForm.ifPresent(
          form -> {
            updateTaskLinkedForm(task, form);
            task.setFormId(form.bpmnId());
            task.setFormVersion(form.version());
          });
    }
    return TaskDTO.createFrom(task, objectMapper);
  }

  public TaskDTO assignTask(
      final String taskId, final String assignee, Boolean allowOverrideAssignment) {
    if (allowOverrideAssignment == null) {
      allowOverrideAssignment = true;
    }

    final var isApiUser = TasklistAuthenticationUtil.isApiUser();
    if (StringUtils.isEmpty(assignee) && isApiUser) {
      throw new InvalidRequestException("Assignee must be specified");
    }

    final boolean allowNonSelfAssignment =
        Optional.ofNullable(tasklistProperties.getFeatureFlag().getAllowNonSelfAssignment())
            .orElse(false);

    final var currentAuthentication = authenticationProvider.getCamundaAuthentication();
    final var currentUsername = currentAuthentication.authenticatedUsername();

    if (!allowNonSelfAssignment
        && StringUtils.isNotEmpty(assignee)
        && !isApiUser
        && !assignee.equals(currentUsername)) {
      throw new ForbiddenActionException(
          "User doesn't have the permission to assign another user to this task");
    }

    final TaskEntity taskBefore = taskStore.getTask(taskId);
    taskValidator.validateCanAssign(taskBefore, allowOverrideAssignment);

    final String taskAssignee = determineTaskAssignee(assignee, currentUsername);
    tasklistServicesAdapter.assignUserTask(taskBefore, taskAssignee);

    final TaskEntity claimedTask = taskStore.persistTaskClaim(taskBefore, taskAssignee);
    final var assignedTaskMetrics = getTaskMetricLabels(claimedTask, currentUsername);
    updateClaimedMetric(assignedTaskMetrics);
    updateTaskAssignedMetric(claimedTask);
    return TaskDTO.createFrom(claimedTask, objectMapper);
  }

  public void updateTaskAssignedMetric(TaskEntity task) {
    // Only write metrics when completing a Job-based User Tasks. With 8.7,
    // metrics for completed (not Job-based) User Tasks are written by the
    // handler "TaskCompletedMetricHandler" in the camunda-exporter
    if (TaskImplementation.JOB_WORKER.equals(task.getImplementation())) {
      taskMetricsStore.registerTaskAssigned(task);
    }
  }

  private String determineTaskAssignee(final String assignee, final String authenticatedUsername) {
    return StringUtils.isEmpty(assignee) && !TasklistAuthenticationUtil.isApiUser()
        ? authenticatedUsername
        : assignee;
  }

  public TaskDTO completeTask(
      final String taskId,
      final List<VariableInputDTO> variables,
      final boolean withDraftVariableValues) {
    final Map<String, Object> variablesMap = new HashMap<>();
    requireNonNullElse(variables, Collections.<VariableInputDTO>emptyList())
        .forEach(variable -> variablesMap.put(variable.getName(), extractTypedValue(variable)));

    try {
      LOGGER.info("Starting completion of task with ID: {}", taskId);

      final TaskEntity task = taskStore.getTask(taskId);
      taskValidator.validateCanComplete(task);
      tasklistServicesAdapter.completeUserTask(task, variablesMap);

      // persist completion and variables
      final TaskEntity completedTaskEntity = taskStore.persistTaskCompletion(task);
      try {
        LOGGER.info("Start variable persistence: {}", taskId);
        variableService.persistTaskVariables(taskId, variables, withDraftVariableValues);
        deleteDraftTaskVariablesSafely(taskId);
        updateCompletedMetric(completedTaskEntity);
        LOGGER.info("Task with ID {} completed successfully.", taskId);
      } catch (final Exception e) {
        LOGGER.error(
            "Task with key {} was COMPLETED but error happened after completion: {}.",
            taskId,
            e.getMessage());
      }

      return TaskDTO.createFrom(completedTaskEntity, objectMapper);
    } catch (final HttpServerErrorException e) { // Track only internal server errors
      LOGGER.error("Error completing task with ID: {}. Details: {}", taskId, e.getMessage(), e);
      throw new TasklistRuntimeException("Error completing task with ID: " + taskId, e);
    }
  }

  void deleteDraftTaskVariablesSafely(final String taskId) {
    try {
      LOGGER.info(
          "Start deletion of draft task variables associated with task with id='{}'", taskId);
      variableService.deleteDraftTaskVariables(taskId);
    } catch (final Exception ex) {
      final String errorMessage =
          String.format(
              "Error during deletion of draft task variables associated with task with id='%s'",
              taskId);
      LOGGER.error(errorMessage, ex);
    }
  }

  private Object extractTypedValue(final VariableInputDTO variable) {
    if (variable.getValue().equals("null")) {
      return objectMapper
          .nullNode(); // JSON Object null must be instanced like "null", also should not send to
      // objectMapper null values
    }

    try {
      return objectMapper.readValue(variable.getValue(), Object.class);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  public TaskDTO unassignTask(final String taskId) {
    final TaskEntity taskBefore = taskStore.getTask(taskId);
    taskValidator.validateCanUnassign(taskBefore);
    final TaskEntity taskEntity = taskStore.persistTaskUnclaim(taskBefore);
    try {
      tasklistServicesAdapter.unassignUserTask(taskEntity);
    } catch (final Exception e) {
      taskStore.persistTaskClaim(taskBefore, taskBefore.getAssignee());
      throw e;
    }
    return TaskDTO.createFrom(taskEntity, objectMapper);
  }

  private boolean taskFormLinkIsNotComplete(final TaskEntity task) {
    return task.getFormKey() != null
        && task.getFormId() == null
        && (task.getIsFormEmbedded() == null || !task.getIsFormEmbedded())
        && task.getExternalFormReference() == null;
  }

  private void updateTaskLinkedForm(final TaskEntity task, final FormIdView form) {
    CompletableFuture.runAsync(
        () -> {
          taskStore.updateTaskLinkedForm(task, form.bpmnId(), form.version());
          LOGGER.debug(
              "Updated Task with id {} form link of key {} to formId {} and version {}",
              task.getKey(),
              task.getFormKey(),
              form.bpmnId(),
              form.version());
        });
  }

  private void updateClaimedMetric(final String[] metricsLabels) {
    metrics.recordCounts(COUNTER_NAME_CLAIMED_TASKS, 1, metricsLabels);
  }

  private void updateCompletedMetric(final TaskEntity task) {
    LOGGER.info("Updating completed task metric for task with ID: {}", task.getKey());
    try {
      final var currentAuthentication = authenticationProvider.getCamundaAuthentication();
      final var authenticatedUsername = currentAuthentication.authenticatedUsername();
      final var completedTaskLabels = getTaskMetricLabels(task, authenticatedUsername);
      metrics.recordCounts(COUNTER_NAME_COMPLETED_TASKS, 1, completedTaskLabels);
    } catch (final Exception e) {
      LOGGER.error("Error updating completed task metric for task with ID: {}", task.getKey(), e);
      throw new TasklistRuntimeException(
          "Error updating completed task metric for task with ID: " + task.getKey(), e);
    }
  }

  private String[] getTaskMetricLabels(final TaskEntity task, final String username) {
    final String keyUserId;

    if (TasklistAuthenticationUtil.isApiUser()) {
      if (task.getAssignee() != null) {
        keyUserId = task.getAssignee();
      } else {
        keyUserId = DEFAULT_USER;
      }
    } else {
      keyUserId = username;
    }

    return new String[] {
      TAG_KEY_BPMN_PROCESS_ID, task.getBpmnProcessId(),
      TAG_KEY_FLOW_NODE_ID, task.getFlowNodeBpmnId(),
      TAG_KEY_USER_ID, keyUserId,
      TAG_KEY_ORGANIZATION_ID, organizationService.getOrganizationIfPresent()
    };
  }

  private String getOrganizationIfPresent() {
    return Optional.ofNullable(tasklistProperties.getAuth0())
        .map(Auth0Properties::getOrganization)
        .orElse(DEFAULT_ORGANIZATION);
  }
}
