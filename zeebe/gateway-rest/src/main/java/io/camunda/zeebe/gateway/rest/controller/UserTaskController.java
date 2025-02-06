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
import io.camunda.zeebe.gateway.protocol.rest.FormResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskVariableSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.AssignUserTaskRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.CompleteUserTaskRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.UpdateUserTaskRequest;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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

  @CamundaPostMapping(path = "/{userTaskKey}/completion")
  public CompletableFuture<ResponseEntity<Object>> completeUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskCompletionRequest completionRequest) {

    return completeUserTask(
        RequestMapper.toUserTaskCompletionRequest(completionRequest, userTaskKey));
  }

  @CamundaPostMapping(path = "/{userTaskKey}/assignment")
  public CompletableFuture<ResponseEntity<Object>> assignUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody final UserTaskAssignmentRequest assignmentRequest) {

    return RequestMapper.toUserTaskAssignmentRequest(assignmentRequest, userTaskKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::assignUserTask);
  }

  @CamundaDeleteMapping(path = "/{userTaskKey}/assignee")
  public CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      @PathVariable final long userTaskKey) {

    return unassignUserTask(RequestMapper.toUserTaskUnassignmentRequest(userTaskKey));
  }

  @CamundaPatchMapping(path = "/{userTaskKey}")
  public CompletableFuture<ResponseEntity<Object>> updateUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskUpdateRequest updateRequest) {

    return RequestMapper.toUserTaskUpdateRequest(updateRequest, userTaskKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateUserTask);
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<UserTaskSearchQueryResult> searchUserTasks(
      @RequestBody(required = false) final UserTaskSearchQuery query) {
    return SearchQueryRequestMapper.toUserTaskQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @CamundaGetMapping(path = "/{userTaskKey}")
  public ResponseEntity<UserTaskResult> getByKey(
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

  @CamundaGetMapping(path = "/{userTaskKey}/form")
  public ResponseEntity<FormResult> getFormByUserTaskKey(
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

  @CamundaPostMapping(path = "/{userTaskKey}/variables/search")
  public ResponseEntity<VariableSearchQueryResult> searchVariables(
      @PathVariable("userTaskKey") final long userTaskKey,
      @RequestBody(required = false)
          final UserTaskVariableSearchQueryRequest userTaskVariablesSearchQueryRequest) {

    return SearchQueryRequestMapper.toUserTaskVariableQuery(userTaskVariablesSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> searchUserTaskVariableQuery(userTaskKey, query));
  }

  private ResponseEntity<UserTaskSearchQueryResult> search(final UserTaskQuery query) {
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

  private ResponseEntity<VariableSearchQueryResult> searchUserTaskVariableQuery(
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
