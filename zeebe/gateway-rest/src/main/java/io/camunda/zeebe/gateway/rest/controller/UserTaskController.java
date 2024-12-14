/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.entities.FormEntity;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.service.UserTaskServices;
import io.camunda.zeebe.gateway.protocol.rest.FormItem;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskItem;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskVariableSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.AssignUserTaskRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.CompleteUserTaskRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.UpdateUserTaskRequest;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping(path = {"/v1/user-tasks", "/v2/user-tasks"})
public class UserTaskController {

  private final UserTaskServices userTaskServices;
  private final ProcessCache processCache;

  public UserTaskController(
      final UserTaskServices userTaskServices, final ProcessCache processCache) {
    this.userTaskServices = userTaskServices;
    this.processCache = processCache;
  }

  @PostMapping(
      path = "/{userTaskKey}/completion",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> completeUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskCompletionRequest completionRequest) {

    return completeUserTask(
        RequestMapper.toUserTaskCompletionRequest(completionRequest, userTaskKey));
  }

  @PostMapping(
      path = "/{userTaskKey}/assignment",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> assignUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody final UserTaskAssignmentRequest assignmentRequest) {

    return RequestMapper.toUserTaskAssignmentRequest(assignmentRequest, userTaskKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::assignUserTask);
  }

  @DeleteMapping(path = "/{userTaskKey}/assignee")
  public CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      @PathVariable final long userTaskKey) {

    return unassignUserTask(RequestMapper.toUserTaskUnassignmentRequest(userTaskKey));
  }

  @PatchMapping(
      path = "/{userTaskKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> updateUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskUpdateRequest updateRequest) {

    return RequestMapper.toUserTaskUpdateRequest(updateRequest, userTaskKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateUserTask);
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserTaskSearchQueryResponse> searchUserTasks(
      @RequestBody(required = false) final UserTaskSearchQueryRequest query) {
    return SearchQueryRequestMapper.toUserTaskQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @GetMapping(
      path = "/{userTaskKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<UserTaskItem> getByKey(
      @PathVariable("userTaskKey") final Long userTaskKey) {
    try {
      final var userTask =
          userTaskServices
              .withAuthentication(RequestMapper.getAuthentication())
              .getByKey(userTaskKey);
      final var name = processCache.getUserTaskName(userTask);
      return ResponseEntity.ok().body(SearchQueryResponseMapper.toUserTask(userTask, name));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @GetMapping(
      path = "/{userTaskKey}/form",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<FormItem> getFormByUserTaskKey(
      @PathVariable("userTaskKey") final long userTaskKey) {
    try {
      final Optional<FormEntity> form =
          userTaskServices
              .withAuthentication(RequestMapper.getAuthentication())
              .getUserTaskForm(userTaskKey);
      return form.map(SearchQueryResponseMapper::toFormItem)
          .map(ResponseEntity::ok)
          .orElseGet(() -> ResponseEntity.noContent().build());
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @PostMapping(
      path = "/{userTaskKey}/variables/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<VariableSearchQueryResponse> searchVariables(
      @PathVariable("userTaskKey") final long userTaskKey,
      @RequestBody(required = false)
          final UserTaskVariableSearchQueryRequest userTaskVariablesSearchQueryRequest) {

    return SearchQueryRequestMapper.toUserTaskVariableQuery(userTaskVariablesSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> searchUserTaskVariableQuery(userTaskKey, query));
  }

  private ResponseEntity<UserTaskSearchQueryResponse> search(final UserTaskQuery query) {
    try {
      final var result =
          userTaskServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      final var processCacheItems = processCache.getUserTaskNames(result.items());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toUserTaskSearchQueryResponse(result, processCacheItems));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<VariableSearchQueryResponse> searchUserTaskVariableQuery(
      final long userTaskKey, final VariableQuery query) {
    try {
      final var result =
          userTaskServices
              .withAuthentication(RequestMapper.getAuthentication())
              .searchUserTaskVariables(userTaskKey, query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toVariableSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> assignUserTask(
      final AssignUserTaskRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(RequestMapper.getAuthentication())
                .assignUserTask(
                    request.userTaskKey(),
                    request.assignee(),
                    request.action(),
                    request.allowOverride()));
  }

  private CompletableFuture<ResponseEntity<Object>> completeUserTask(
      final CompleteUserTaskRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(RequestMapper.getAuthentication())
                .completeUserTask(request.userTaskKey(), request.variables(), request.action()));
  }

  private CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      final AssignUserTaskRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(RequestMapper.getAuthentication())
                .unassignUserTask(request.userTaskKey(), request.action()));
  }

  private CompletableFuture<ResponseEntity<Object>> updateUserTask(
      final UpdateUserTaskRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateUserTask(request.userTaskKey(), request.changeset(), request.action()));
  }
}
