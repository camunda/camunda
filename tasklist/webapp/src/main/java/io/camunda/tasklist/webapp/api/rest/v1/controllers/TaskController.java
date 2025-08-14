/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.tasklist.webapp.mapper.TaskMapper.TASK_DESCRIPTION;
import static io.camunda.tasklist.webapp.permission.TasklistPermissionServices.WILDCARD_RESOURCE;
import static java.util.Objects.requireNonNullElse;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.TaskByCandidateUserOrGroup;
import io.camunda.tasklist.util.LazySupplier;
import io.camunda.tasklist.webapp.api.rest.v1.entities.IncludeVariable;
import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskAssignRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskCompleteRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariablesSearchRequest;
import io.camunda.tasklist.webapp.dto.TaskDTO;
import io.camunda.tasklist.webapp.group.UserGroupService;
import io.camunda.tasklist.webapp.mapper.TaskMapper;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.TasklistAuthenticationUtil;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.TaskService;
import io.camunda.tasklist.webapp.service.VariableService;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Task", description = "API to query and manage tasks.")
@RestController
@RequestMapping(value = TasklistURIs.TASKS_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class TaskController extends ApiErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskController.class);
  private static final String ZEEBE_USER_TASK_OPERATIONS_NOT_SUPPORTED =
      "This operation is not supported using Tasklist V1 API. Please use the latest API. For more information, refer to the documentation: %s";
  private static final String USER_DOES_NOT_HAVE_ACCESS_TO_THIS_TASK_ERROR =
      "User does not have permission to perform on this task.";

  @Autowired private TaskService taskService;
  @Autowired private VariableService variableService;
  @Autowired private TaskMapper taskMapper;
  @Autowired private CamundaAuthenticationProvider authenticationProvider;
  @Autowired private UserGroupService userGroupService;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private TasklistPermissionServices permissionServices;

  @Operation(
      summary = "Search tasks",
      description =
          "Returns the list of tasks that satisfy search request params.<br>"
              + "<ul><li>If an empty body is provided, all tasks are returned.</li>"
              + "<li>Only one of `[searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual]` search options must be present in request.</li></ul>",
      responses = {
        @ApiResponse(
            description = "On success returned.",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when more than one search parameters among "
                    + "`[searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual]` are present in request",
            responseCode = "400",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PostMapping("search")
  public ResponseEntity<List<TaskSearchResponse>> searchTasks(
      @RequestBody(required = false) final TaskSearchRequest searchRequest) {

    if (!permissionServices.hasPermissionToReadUserTask(WILDCARD_RESOURCE)) {
      // We return an empty list here to match the behaviour of V2
      return ResponseEntity.ok(Collections.emptyList());
    }

    final var query =
        taskMapper.toTaskQuery(requireNonNullElse(searchRequest, new TaskSearchRequest()));

    final var camundaAuthentication = authenticationProvider.getCamundaAuthentication();
    final var userName = camundaAuthentication.authenticatedUsername();

    if (tasklistProperties.getIdentity() != null
        && tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled()
        // In the case of M2M tokens the userId is null, so we do not apply the user group
        // restrictions
        // this is backwards compatible with previous versions, but in the future this will change
        && userName != null
        && !userName.isEmpty()) {
      final List<String> listOfUserGroups = userGroupService.getUserGroups();
      final TaskByCandidateUserOrGroup taskByCandidateUserOrGroup =
          new TaskByCandidateUserOrGroup();
      taskByCandidateUserOrGroup.setUserGroups(listOfUserGroups.toArray(String[]::new));
      taskByCandidateUserOrGroup.setUserName(userName);
      query.setTaskByCandidateUserOrGroup(taskByCandidateUserOrGroup);
    }

    // TODO  This is a temporary solution to include the taskDescription in the contextVariables
    // In the future we may have more than one context variable, so we add dynamically here
    final Map<String, Boolean> contextVariables = Map.of(TASK_DESCRIPTION, false);

    final Map<String, Boolean> variableNamesToReturnFullValue = new HashMap<>();

    variableNamesToReturnFullValue.put(TASK_DESCRIPTION, false);

    variableNamesToReturnFullValue.putAll(
        Optional.ofNullable(searchRequest)
            .map(TaskSearchRequest::getIncludeVariables)
            .map(
                variables ->
                    Arrays.stream(variables)
                        .collect(
                            Collectors.toMap(
                                IncludeVariable::getName,
                                IncludeVariable::isAlwaysReturnFullValue)))
            .orElse(Collections.emptyMap()));

    final Set<String> includeVariableNames = variableNamesToReturnFullValue.keySet();

    final boolean fetchFullValuesFromDB =
        variableNamesToReturnFullValue.entrySet().stream().anyMatch(Map.Entry::getValue);

    final var tasks =
        taskService.getTasks(query, includeVariableNames, fetchFullValuesFromDB).stream()
            .map(taskMapper::toTaskSearchResponse)
            .collect(Collectors.toList());

    tasks.stream()
        .map(TaskSearchResponse::getVariables)
        .filter(Objects::nonNull)
        .flatMap(Arrays::stream)
        .forEach(resp -> unsetBigVariableValuesIfNeeded(resp, variableNamesToReturnFullValue));
    return ResponseEntity.ok(tasks);
  }

  private void unsetBigVariableValuesIfNeeded(
      final VariableSearchResponse resp,
      final Map<String, Boolean> variableNamesToReturnFullValue) {
    final boolean returnFullValue =
        Optional.ofNullable(variableNamesToReturnFullValue.get(resp.getName())).orElse(false);
    if (resp.getIsValueTruncated() && !returnFullValue) {
      resp.resetValue();
    }

    final var draft = resp.getDraft();
    if (draft != null && draft.getIsValueTruncated() && !returnFullValue) {
      draft.resetValue();
    }
  }

  @Operation(
      summary = "Get a task",
      description = "Get one task by id. Returns task or error when task does not exist.",
      responses = {
        @ApiResponse(
            description = "On success returned.",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description = "An error is returned when the task with the `taskId` is not found.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description = "User has no permission to access the task (Self-managed only).",
            responseCode = "403",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @GetMapping("{taskId}")
  public ResponseEntity<TaskResponse> getTaskById(
      @PathVariable @Parameter(description = "The ID of the task.", required = true)
          final String taskId) {
    final var taskSupplier = getTaskSupplier(taskId);
    if (!isUserRestrictionEnabled() || hasAccessToTask(taskSupplier)) {
      return ResponseEntity.ok(taskMapper.toTaskResponse(taskSupplier.get()));
    } else {
      throw new ForbiddenActionException(USER_DOES_NOT_HAVE_ACCESS_TO_THIS_TASK_ERROR);
    }
  }

  private void checkTaskImplementation(final LazySupplier<TaskDTO> taskSupplier) {
    if (taskSupplier.get().getImplementation() != TaskImplementation.JOB_WORKER
        && TasklistAuthenticationUtil.isApiUser()) {
      final TaskDTO task = taskSupplier.get();
      LOGGER.warn(
          "V1 API is used for task with id={} implementation={}",
          task.getId(),
          task.getImplementation());
      throw new InvalidRequestException(
          String.format(
              ZEEBE_USER_TASK_OPERATIONS_NOT_SUPPORTED,
              tasklistProperties.getDocumentation().getApiMigrationDocsUrl()));
    }
  }

  private boolean hasAccessToTask(final LazySupplier<TaskDTO> taskSupplier) {
    final var currentAuthentication = authenticationProvider.getCamundaAuthentication();
    final var userName = currentAuthentication.authenticatedUsername();
    if (userName == null || userName.isEmpty()) {
      // In the case of M2M tokens the userId is null, so we do not apply the user group
      // restrictions
      // this is backwards compatible with previous versions, but in the future this will change
      return true;
    }
    final List<String> listOfUserGroups = userGroupService.getUserGroups();
    final var task = taskSupplier.get();
    final boolean allUsersTask =
        task.getCandidateUsers() == null && task.getCandidateGroups() == null;
    final boolean candidateGroupTasks =
        task.getCandidateGroups() != null
            && !Collections.disjoint(Arrays.asList(task.getCandidateGroups()), listOfUserGroups);
    final boolean candidateUserTasks =
        task.getCandidateUsers() != null
            && Arrays.asList(task.getCandidateUsers()).contains(userName);
    final boolean assigneeTasks = task.getAssignee() != null && task.getAssignee().equals(userName);

    return candidateUserTasks || assigneeTasks || candidateGroupTasks || allUsersTask;
  }

  @Operation(
      summary = "Assign a task",
      description =
          "Assign a task with `taskId` to `assignee` or the active user. Returns the task.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description =
                  "When using REST API with JWT authentication token following request body parameters may be used."),
      responses = {
        @ApiResponse(
            description = "On success returned.",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the task is not active (not in the CREATED state).<br>"
                    + "An error is returned when task was already assigned, except the case when JWT authentication token used and `allowOverrideAssignment = true`.",
            responseCode = "400",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description =
                "An error is returned when user doesn't have the permission to assign another user to this task.",
            responseCode = "403",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description = "An error is returned when the task with the `taskId` is not found.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PatchMapping("{taskId}/assign")
  public ResponseEntity<TaskResponse> assignTask(
      @PathVariable @Parameter(description = "The ID of the task.", required = true)
          final String taskId,
      @RequestBody(required = false) final TaskAssignRequest assignRequest) {
    checkTaskImplementation(getTaskSupplier(taskId));
    final var safeAssignRequest = requireNonNullElse(assignRequest, new TaskAssignRequest());
    final var assignedTask =
        taskService.assignTask(
            taskId, safeAssignRequest.getAssignee(), safeAssignRequest.isAllowOverrideAssignment());
    return ResponseEntity.ok(taskMapper.toTaskResponse(assignedTask));
  }

  @Operation(
      summary = "Unassign a task",
      description = "Unassign a task with `taskId`. Returns the task.",
      responses = {
        @ApiResponse(
            description = "On success returned.",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the task is not active (not in the CREATED state).<br>"
                    + "An error is returned if the task was not claimed (assigned) before.",
            responseCode = "400",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description = "An error is returned when the task with the `taskId` is not found.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PatchMapping("{taskId}/unassign")
  public ResponseEntity<TaskResponse> unassignTask(
      @PathVariable @Parameter(description = "The ID of the task.", required = true)
          final String taskId) {
    checkTaskImplementation(getTaskSupplier(taskId));
    final var unassignedTask = taskService.unassignTask(taskId);
    return ResponseEntity.ok(taskMapper.toTaskResponse(unassignedTask));
  }

  @Operation(
      summary = "Complete a task",
      description = "Complete a task with `taskId` and optional `variables`. Returns the task.",
      responses = {
        @ApiResponse(
            description = "On success returned.",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the task is not active (not in the CREATED state).<br>"
                    + "An error is returned if the task was not claimed (assigned) before.<br>"
                    + "An error is returned if the task is not assigned to the current user.",
            responseCode = "400",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description = "User has no permission to access the task (Self-managed only).",
            responseCode = "403",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description = "An error is returned when the task with the `taskId` is not found.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PatchMapping("{taskId}/complete")
  public ResponseEntity<TaskResponse> completeTask(
      @PathVariable @Parameter(description = "The ID of the task.", required = true)
          final String taskId,
      @RequestBody(required = false) final TaskCompleteRequest taskCompleteRequest) {
    final var variables =
        requireNonNullElse(taskCompleteRequest, new TaskCompleteRequest()).getVariables();
    final var taskSupplier = getTaskSupplier(taskId);
    checkTaskImplementation(taskSupplier);
    if (!isUserRestrictionEnabled() || hasAccessToTask(taskSupplier)) {
      final var completedTask = taskService.completeTask(taskId, variables, true);
      return ResponseEntity.ok(taskMapper.toTaskResponse(completedTask));
    } else {
      throw new ForbiddenActionException(USER_DOES_NOT_HAVE_ACCESS_TO_THIS_TASK_ERROR);
    }
  }

  private boolean isUserRestrictionEnabled() {
    if (tasklistProperties.getIdentity() != null) {
      return tasklistProperties.getIdentity().isUserAccessRestrictionsEnabled();
    } else {
      return false;
    }
  }

  @Operation(
      summary = "Save draft variables",
      description =
          "This operation performs several actions: <br/>"
              + "<ol><li>Validates the task and draft variables.</li><li>Deletes existing draft variables for the task.</li><li>Checks for new draft variables. If a new variable's `name` matches an existing one but the `value` differs, it is saved. In case of duplicate draft variable names, the last variable's value is kept.</li></ol><b>NOTE:</b><ul><li>Invoking this method successively will overwrite all existing draft variables. Only draft variables submitted in the most recent request body will be persisted. Therefore, ensure you include all necessary variables in each request to maintain the intended variable set.</li><li>The UI does not currently display the values for draft variables that are created via this endpoint.</li></ul>",
      responses = {
        @ApiResponse(
            responseCode = "204",
            description = "On success returned.",
            content = @Content(mediaType = MediaType.ALL_VALUE)),
        @ApiResponse(
            responseCode = "400",
            description =
                "An error is returned when the task is not active (not in the `CREATED` state).<br/>"
                    + "An error is returned if the task was not claimed (assigned) before, except the case when JWT authentication token used.<br/>"
                    + "An error is returned if the task is not assigned to the current user, except the case when JWT authentication token used.",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            responseCode = "404",
            description = "An error is returned when the task with the `taskId` is not found.",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            responseCode = "500",
            description =
                "An error is returned if an unexpected error occurs while persisting draft task variables.",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PostMapping("{taskId}/variables")
  public ResponseEntity<Void> saveDraftTaskVariables(
      @PathVariable @Parameter(description = "The ID of the task.", required = true)
          final String taskId,
      @RequestBody final SaveVariablesRequest saveVariablesRequest) {
    final var taskSupplier = getTaskSupplier(taskId);
    if (permissionServices.hasPermissionToUpdateUserTask(
            new TaskEntity().setBpmnProcessId(taskSupplier.get().getBpmnProcessId()))
        && (!isUserRestrictionEnabled() || hasAccessToTask(taskSupplier))) {
      variableService.persistDraftTaskVariables(taskId, saveVariablesRequest.getVariables());
    } else {
      throw new ForbiddenActionException(USER_DOES_NOT_HAVE_ACCESS_TO_THIS_TASK_ERROR);
    }
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Search task variables",
      description =
          "This method returns a list of task variables for the specified `taskId` and `variableName`.<br>"
              + "If the request body is not provided or if the `variableNames` parameter in the request is empty, "
              + "all variables associated with the task will be returned.",
      responses = {
        @ApiResponse(
            description = "On success returned.",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description = "An error is returned when the task with the `taskId` is not found.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PostMapping("{taskId}/variables/search")
  public ResponseEntity<List<VariableSearchResponse>> searchTaskVariables(
      @PathVariable @Parameter(description = "The ID of the task.", required = true)
          final String taskId,
      @RequestBody(required = false) final VariablesSearchRequest variablesSearchRequest) {
    if (!permissionServices.hasPermissionToReadUserTask(WILDCARD_RESOURCE)) {
      // We return an empty list here to match the behaviour of V2
      return ResponseEntity.ok(Collections.emptyList());
    }

    final Map<String, Boolean> variableNamesToReturnFullValue;
    if (variablesSearchRequest != null) {
      if (CollectionUtils.isNotEmpty(variablesSearchRequest.getVariableNames())
          && CollectionUtils.isNotEmpty(variablesSearchRequest.getIncludeVariables())) {
        throw new InvalidRequestException(
            "Only one of [variableNames, includeVariables] must be present in request.");
      } else if (CollectionUtils.isNotEmpty(variablesSearchRequest.getVariableNames())) {
        variableNamesToReturnFullValue =
            variablesSearchRequest.getVariableNames().stream()
                .collect(Collectors.toMap(Function.identity(), k -> false));
      } else if (CollectionUtils.isNotEmpty(variablesSearchRequest.getIncludeVariables())) {
        variableNamesToReturnFullValue =
            variablesSearchRequest.getIncludeVariables().stream()
                .collect(
                    Collectors.toMap(
                        IncludeVariable::getName, IncludeVariable::isAlwaysReturnFullValue));
      } else {
        variableNamesToReturnFullValue = Collections.emptyMap();
      }
    } else {
      variableNamesToReturnFullValue = Collections.emptyMap();
    }

    final List<VariableSearchResponse> variables =
        variableService.getVariableSearchResponses(taskId, variableNamesToReturnFullValue.keySet());

    variables.forEach(resp -> unsetBigVariableValuesIfNeeded(resp, variableNamesToReturnFullValue));

    return ResponseEntity.ok(variables);
  }

  private LazySupplier<TaskDTO> getTaskSupplier(final String taskId) {
    return LazySupplier.of(() -> taskService.getTask(taskId));
  }
}
