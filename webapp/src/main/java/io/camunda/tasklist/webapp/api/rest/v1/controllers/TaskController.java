/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static java.util.Objects.requireNonNullElse;

import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskAssignRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskCompleteRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariablesSearchRequest;
import io.camunda.tasklist.webapp.mapper.TaskMapper;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.TaskService;
import io.camunda.tasklist.webapp.service.VariableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Task", description = "API to query and manage tasks")
@RestController
@RequestMapping(value = TasklistURIs.TASKS_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class TaskController extends ApiErrorController {

  @Autowired private TaskService taskService;
  @Autowired private VariableService variableService;
  @Autowired private TaskMapper taskMapper;

  @Operation(
      summary = "Returns the list of tasks that satisfy search request params",
      description =
          "Returns the list of tasks that satisfy search request params.</br>"
              + "<b>NOTE:</b> Only one of `[searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual]`"
              + "search options must be present in request.",
      responses = {
        @ApiResponse(
            description = "On success returned",
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
      @RequestBody(required = false) TaskSearchRequest searchRequest) {

    final var query =
        taskMapper.toTaskQuery(requireNonNullElse(searchRequest, new TaskSearchRequest()));
    final var tasks =
        taskService.getTasks(query, List.of()).stream()
            .map(taskMapper::toTaskSearchResponse)
            .collect(Collectors.toList());
    return ResponseEntity.ok(tasks);
  }

  @Operation(
      summary = "Get one task by id. Returns task or error when task does not exist.",
      responses = {
        @ApiResponse(
            description = "On success returned",
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
  @GetMapping("{taskId}")
  public ResponseEntity<TaskResponse> getTaskById(@PathVariable String taskId) {
    final var task = taskMapper.toTaskResponse(taskService.getTask(taskId, List.of()));
    return ResponseEntity.ok(task);
  }

  @Operation(
      summary = "Assign a task with id to assignee. Returns the task.",
      description = "Assign a task with `taskId` to `assignee` or the active user.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description =
                  "When using REST API with JWT authentication token following request body parameters may be used."),
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the task is not active (not in the CREATED state).</br>"
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
  @PreAuthorize("hasPermission('write')")
  @PatchMapping("{taskId}/assign")
  public ResponseEntity<TaskResponse> assignTask(
      @PathVariable String taskId, @RequestBody(required = false) TaskAssignRequest assignRequest) {
    final var safeAssignRequest = requireNonNullElse(assignRequest, new TaskAssignRequest());
    final var assignedTask =
        taskService.assignTask(
            taskId, safeAssignRequest.getAssignee(), safeAssignRequest.isAllowOverrideAssignment());
    return ResponseEntity.ok(taskMapper.toTaskResponse(assignedTask));
  }

  @Operation(
      summary = "Unassign a task with provided id. Returns the task.",
      description = "Unassign a task with `taskId`.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the task is not active (not in the CREATED state).</br>"
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
  @PreAuthorize("hasPermission('write')")
  @PatchMapping("{taskId}/unassign")
  public ResponseEntity<TaskResponse> unassignTask(@PathVariable String taskId) {
    final var unassignedTask = taskService.unassignTask(taskId);
    return ResponseEntity.ok(taskMapper.toTaskResponse(unassignedTask));
  }

  @Operation(
      summary = "Complete a task with taskId and optional variables. Returns the task.",
      description = "Complete a task with `taskId` and optional `variables`",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the task is not active (not in the CREATED state).</br>"
                    + "An error is returned if the task was not claimed (assigned) before.</br>"
                    + "An error is returned if the task is not assigned to the current user.",
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
  @PreAuthorize("hasPermission('write')")
  @PatchMapping("{taskId}/complete")
  public ResponseEntity<TaskResponse> completeTask(
      @PathVariable String taskId,
      @RequestBody(required = false) TaskCompleteRequest taskCompleteRequest) {
    final var variables =
        requireNonNullElse(taskCompleteRequest, new TaskCompleteRequest()).getVariables();
    final var completedTask = taskService.completeTask(taskId, variables, true);
    return ResponseEntity.ok(taskMapper.toTaskResponse(completedTask));
  }

  @Operation(
      summary = "Saves draft variables for a task.",
      description =
          "This operation validates the task and draft variables, deletes existing draft variables for the task, "
              + "and then checks for new draft variables. If a new variable's `name` matches an existing one but the "
              + "`value` differs, it is saved. In case of duplicate draft variable names, the last variable's value is kept.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "204",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the task is not active (not in the `CREATED` state).</br>"
                    + "An error is returned if the task was not claimed (assigned) before, except the case when JWT authentication token used.</br>"
                    + "An error is returned if the task is not assigned to the current user, except the case when JWT authentication token used.",
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
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description =
                "An error is returned if an unexpected error occurs while persisting draft task variables.",
            responseCode = "500",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PreAuthorize("hasPermission('write')")
  @PostMapping("{taskId}/variables")
  public ResponseEntity<Void> saveDraftTaskVariables(
      @PathVariable String taskId, @RequestBody SaveVariablesRequest saveVariablesRequest) {
    variableService.persistDraftTaskVariables(taskId, saveVariablesRequest.getVariables());
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Returns the list of task variables",
      description =
          "This method returns a list of task variables for the specified `taskId` and `variableName`.</br>"
              + "If the request body is not provided or if the `variableNames` parameter in the request is empty, "
              + "all variables associated with the task will be returned.",
      responses = {
        @ApiResponse(
            description = "On success returned",
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
      @PathVariable String taskId,
      @RequestBody(required = false) VariablesSearchRequest variablesSearchRequest) {
    final List<String> variableNames =
        Optional.ofNullable(variablesSearchRequest)
            .map(VariablesSearchRequest::getVariableNames)
            .orElse(Collections.emptyList());

    final List<VariableSearchResponse> variables =
        variableService.getVariableSearchResponses(taskId, variableNames);

    variables.forEach(
        resp -> {
          if (resp.getIsValueTruncated()) {
            resp.resetValue();
          }

          final var draft = resp.getDraft();
          if (draft != null && draft.getIsValueTruncated()) {
            draft.resetValue();
          }
        });

    return ResponseEntity.ok(variables);
  }
}
