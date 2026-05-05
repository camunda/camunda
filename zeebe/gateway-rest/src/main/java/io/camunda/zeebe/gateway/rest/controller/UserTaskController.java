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
import io.camunda.gateway.protocol.model.AuditLogSearchQueryResult;
import io.camunda.gateway.protocol.model.FormResult;
import io.camunda.gateway.protocol.model.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.UserTaskAuditLogSearchQueryRequest;
import io.camunda.gateway.protocol.model.UserTaskCompletionRequest;
import io.camunda.gateway.protocol.model.UserTaskEffectiveVariableSearchQueryRequest;
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
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenant;
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
      @RequestBody(required = false) final UserTaskCompletionRequest completionRequest,
      @PhysicalTenant final String physicalTenantId) {

    return completeUserTask(
        RequestMapper.toUserTaskCompletionRequest(completionRequest, userTaskKey),
        physicalTenantId);
  }

  @CamundaPostMapping(path = "/{userTaskKey}/assignment")
  public CompletableFuture<ResponseEntity<Object>> assignUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody final UserTaskAssignmentRequest assignmentRequest,
      @PhysicalTenant final String physicalTenantId) {

    return RequestMapper.toUserTaskAssignmentRequest(assignmentRequest, userTaskKey)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> assignUserTask(req, physicalTenantId));
  }

  @CamundaDeleteMapping(path = "/{userTaskKey}/assignee")
  public CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      @PathVariable final long userTaskKey, @PhysicalTenant final String physicalTenantId) {

    return unassignUserTask(
        RequestMapper.toUserTaskUnassignmentRequest(userTaskKey), physicalTenantId);
  }

  @CamundaPatchMapping(path = "/{userTaskKey}")
  public CompletableFuture<ResponseEntity<Object>> updateUserTask(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskUpdateRequest updateRequest,
      @PhysicalTenant final String physicalTenantId) {

    return RequestMapper.toUserTaskUpdateRequest(updateRequest, userTaskKey)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> updateUserTask(req, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<UserTaskSearchQueryResult> searchUserTasks(
      @RequestBody(required = false) final UserTaskSearchQuery query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toUserTaskQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(q, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{userTaskKey}")
  public ResponseEntity<UserTaskResult> getByKey(
      @PathVariable("userTaskKey") final Long userTaskKey,
      @PhysicalTenant final String physicalTenantId) {
    try {
      final var userTask =
          userTaskServices.getByKey(
              userTaskKey, authenticationProvider.getCamundaAuthentication(), physicalTenantId);

      return ResponseEntity.ok().body(SearchQueryResponseMapper.toUserTask(userTask));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{userTaskKey}/form")
  public ResponseEntity<FormResult> getFormByUserTaskKey(
      @PathVariable("userTaskKey") final long userTaskKey,
      @PhysicalTenant final String physicalTenantId) {
    try {
      return userTaskServices
          .getUserTaskForm(
              userTaskKey, authenticationProvider.getCamundaAuthentication(), physicalTenantId)
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
          final boolean truncateValues,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toUserTaskVariableQuery(userTaskVariablesSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query ->
                searchUserTaskVariableQuery(userTaskKey, query, truncateValues, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{userTaskKey}/effective-variables/search")
  public ResponseEntity<VariableSearchQueryResult> searchEffectiveVariables(
      @PathVariable("userTaskKey") final long userTaskKey,
      @RequestBody(required = false)
          final UserTaskEffectiveVariableSearchQueryRequest
              userTaskEffectiveVariablesSearchQueryRequest,
      @RequestParam(name = "truncateValues", required = false, defaultValue = "true")
          final boolean truncateValues,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toUserTaskEffectiveVariableQuery(
            userTaskEffectiveVariablesSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query ->
                searchUserTaskEffectiveVariableQuery(
                    userTaskKey, query, truncateValues, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{userTaskKey}/audit-logs/search")
  public ResponseEntity<AuditLogSearchQueryResult> searchAuditLogs(
      @PathVariable final long userTaskKey,
      @RequestBody(required = false)
          final UserTaskAuditLogSearchQueryRequest auditLogSearchQueryRequest,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toUserTaskAuditLogQuery(auditLogSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> searchUserTaskAuditLogQuery(userTaskKey, query, physicalTenantId));
  }

  private ResponseEntity<UserTaskSearchQueryResult> search(
      final UserTaskQuery query, final String physicalTenantId) {
    try {
      final var result =
          userTaskServices.search(
              query, authenticationProvider.getCamundaAuthentication(), physicalTenantId);

      return ResponseEntity.ok(SearchQueryResponseMapper.toUserTaskSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<VariableSearchQueryResult> searchUserTaskVariableQuery(
      final long userTaskKey,
      final VariableQuery query,
      final boolean truncateValues,
      final String physicalTenantId) {
    try {
      final var result =
          userTaskServices.searchUserTaskVariables(
              userTaskKey,
              query,
              authenticationProvider.getCamundaAuthentication(),
              physicalTenantId);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(result, truncateValues));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<VariableSearchQueryResult> searchUserTaskEffectiveVariableQuery(
      final long userTaskKey,
      final VariableQuery query,
      final boolean truncateValues,
      final String physicalTenantId) {
    try {
      final var result =
          userTaskServices.searchUserTaskEffectiveVariables(
              userTaskKey,
              query,
              authenticationProvider.getCamundaAuthentication(),
              physicalTenantId);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(result, truncateValues));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<AuditLogSearchQueryResult> searchUserTaskAuditLogQuery(
      final long userTaskKey, final AuditLogQuery query, final String physicalTenantId) {
    try {
      final var result =
          userTaskServices.searchUserTaskAuditLogs(
              userTaskKey,
              query,
              authenticationProvider.getCamundaAuthentication(),
              physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toAuditLogSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> assignUserTask(
      final AssignUserTaskRequest request, final String physicalTenantId) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices.assignUserTask(
                request.userTaskKey(),
                request.assignee(),
                request.action(),
                request.allowOverride(),
                authenticationProvider.getCamundaAuthentication(),
                physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> completeUserTask(
      final CompleteUserTaskRequest request, final String physicalTenantId) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices.completeUserTask(
                request.userTaskKey(),
                request.variables(),
                request.action(),
                authenticationProvider.getCamundaAuthentication(),
                physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      final AssignUserTaskRequest request, final String physicalTenantId) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices.unassignUserTask(
                request.userTaskKey(),
                request.action(),
                authenticationProvider.getCamundaAuthentication(),
                physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> updateUserTask(
      final UpdateUserTaskRequest request, final String physicalTenantId) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices.updateUserTask(
                request.userTaskKey(),
                request.changeset(),
                request.action(),
                authenticationProvider.getCamundaAuthentication(),
                physicalTenantId));
  }
}
