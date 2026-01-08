/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.RequestMapper.AssignUserTaskRequest;
import io.camunda.gateway.mapping.http.RequestMapper.CompleteUserTaskRequest;
import io.camunda.gateway.mapping.http.RequestMapper.UpdateUserTaskRequest;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.AuditLogSearchQueryRequest;
import io.camunda.gateway.protocol.model.AuditLogSearchQueryResult;
import io.camunda.gateway.protocol.model.FormResult;
import io.camunda.gateway.protocol.model.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.UserTaskCompletionRequest;
import io.camunda.gateway.protocol.model.UserTaskResult;
import io.camunda.gateway.protocol.model.UserTaskSearchQuery;
import io.camunda.gateway.protocol.model.UserTaskSearchQueryResult;
import io.camunda.gateway.protocol.model.UserTaskUpdateRequest;
import io.camunda.gateway.protocol.model.UserTaskVariableSearchQueryRequest;
import io.camunda.gateway.protocol.model.VariableSearchQueryResult;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UserTaskServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping(path = {"/v1/user-tasks", "/v2/user-tasks"})
public class UserTaskController {

  private final UserTaskServices userTaskServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public UserTaskController(
      final UserTaskServices userTaskServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.userTaskServices = userTaskServices;
    this.authenticationProvider = authenticationProvider;
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

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<UserTaskSearchQueryResult> searchUserTasks(
      @RequestBody(required = false) final UserTaskSearchQuery query) {
    return SearchQueryRequestMapper.toUserTaskQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{userTaskKey}")
  public ResponseEntity<UserTaskResult> getByKey(
      @PathVariable("userTaskKey") final Long userTaskKey) {
    try {
      final var userTask =
          userTaskServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .getByKey(userTaskKey);

      return ResponseEntity.ok().body(SearchQueryResponseMapper.toUserTask(userTask));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{userTaskKey}/form")
  public ResponseEntity<FormResult> getFormByUserTaskKey(
      @PathVariable("userTaskKey") final long userTaskKey) {
    try {
      return userTaskServices
          .withAuthentication(authenticationProvider.getCamundaAuthentication())
          .getUserTaskForm(userTaskKey)
          .map(SearchQueryResponseMapper::toFormItem)
          .map(ResponseEntity::ok)
          .orElseGet(() -> ResponseEntity.noContent().build());
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{userTaskKey}/variables/search")
  public ResponseEntity<VariableSearchQueryResult> searchVariables(
      @PathVariable("userTaskKey") final long userTaskKey,
      @RequestBody(required = false)
          final UserTaskVariableSearchQueryRequest userTaskVariablesSearchQueryRequest,
      @RequestParam(name = "truncateValues", required = false, defaultValue = "true")
          final boolean truncateValues) {
    return SearchQueryRequestMapper.toUserTaskVariableQuery(userTaskVariablesSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> searchUserTaskVariableQuery(userTaskKey, query, truncateValues));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{userTaskKey}/audit-logs/search")
  public ResponseEntity<AuditLogSearchQueryResult> searchAuditLogs(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final AuditLogSearchQueryRequest auditLogSearchQueryRequest) {
    return SearchQueryRequestMapper.toAuditLogQuery(auditLogSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> searchUserTaskAuditLogQuery(userTaskKey, query));
  }

  private ResponseEntity<UserTaskSearchQueryResult> search(final UserTaskQuery query) {
    try {
      final var result =
          userTaskServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);

      return ResponseEntity.ok(SearchQueryResponseMapper.toUserTaskSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<VariableSearchQueryResult> searchUserTaskVariableQuery(
      final long userTaskKey, final VariableQuery query, final boolean truncateValues) {
    try {
      final var result =
          userTaskServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchUserTaskVariables(userTaskKey, query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(result, truncateValues));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<AuditLogSearchQueryResult> searchUserTaskAuditLogQuery(
      final long userTaskKey, final AuditLogQuery query) {
    try {
      final var result =
          userTaskServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchUserTaskAuditLogs(userTaskKey, query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toAuditLogSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> assignUserTask(
      final AssignUserTaskRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .assignUserTask(
                    request.userTaskKey(),
                    request.assignee(),
                    request.action(),
                    request.allowOverride()));
  }

  private CompletableFuture<ResponseEntity<Object>> completeUserTask(
      final CompleteUserTaskRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .completeUserTask(request.userTaskKey(), request.variables(), request.action()));
  }

  private CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      final AssignUserTaskRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .unassignUserTask(request.userTaskKey(), request.action()));
  }

  private CompletableFuture<ResponseEntity<Object>> updateUserTask(
      final UpdateUserTaskRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateUserTask(request.userTaskKey(), request.changeset(), request.action()));
  }
}
