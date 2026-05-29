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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.UserTaskServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
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

  private final ServiceRegistry registry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public UserTaskController(
      final ServiceRegistry registry, final CamundaAuthenticationProvider authenticationProvider) {
    this.registry = registry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/{userTaskKey}/completion")
  public CompletableFuture<ResponseEntity<Object>> completeUserTask(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskCompletionRequest completionRequest) {

    return completeUserTask(
        registry.userTaskServices(physicalTenantId),
        RequestMapper.toUserTaskCompletionRequest(completionRequest, userTaskKey));
  }

  @CamundaPostMapping(path = "/{userTaskKey}/assignment")
  public CompletableFuture<ResponseEntity<Object>> assignUserTask(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long userTaskKey,
      @RequestBody final UserTaskAssignmentRequest assignmentRequest) {

    return RequestMapper.toUserTaskAssignmentRequest(assignmentRequest, userTaskKey)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> assignUserTask(registry.userTaskServices(physicalTenantId), request));
  }

  @CamundaDeleteMapping(path = "/{userTaskKey}/assignee")
  public CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long userTaskKey) {

    return unassignUserTask(
        registry.userTaskServices(physicalTenantId),
        RequestMapper.toUserTaskUnassignmentRequest(userTaskKey));
  }

  @CamundaPatchMapping(path = "/{userTaskKey}")
  public CompletableFuture<ResponseEntity<Object>> updateUserTask(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskUpdateRequest updateRequest) {

    return RequestMapper.toUserTaskUpdateRequest(updateRequest, userTaskKey)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> updateUserTask(registry.userTaskServices(physicalTenantId), request));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<UserTaskSearchQueryResult> searchUserTasks(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final UserTaskSearchQuery query) {
    return SearchQueryRequestMapper.toUserTaskQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(registry.userTaskServices(physicalTenantId), q));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{userTaskKey}")
  public ResponseEntity<UserTaskResult> getByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("userTaskKey") final Long userTaskKey) {
    try {
      final var userTask =
          registry
              .userTaskServices(physicalTenantId)
              .getByKey(userTaskKey, authenticationProvider.getCamundaAuthentication());

      return ResponseEntity.ok().body(SearchQueryResponseMapper.toUserTask(userTask));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{userTaskKey}/form")
  public ResponseEntity<FormResult> getFormByUserTaskKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("userTaskKey") final long userTaskKey) {
    try {
      return registry
          .userTaskServices(physicalTenantId)
          .getUserTaskForm(userTaskKey, authenticationProvider.getCamundaAuthentication())
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
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("userTaskKey") final long userTaskKey,
      @RequestBody(required = false)
          final UserTaskVariableSearchQueryRequest userTaskVariablesSearchQueryRequest,
      @RequestParam(name = "truncateValues", required = false, defaultValue = "true")
          final boolean truncateValues) {
    return SearchQueryRequestMapper.toUserTaskVariableQuery(userTaskVariablesSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query ->
                searchUserTaskVariableQuery(
                    registry.userTaskServices(physicalTenantId),
                    userTaskKey,
                    query,
                    truncateValues));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{userTaskKey}/effective-variables/search")
  public ResponseEntity<VariableSearchQueryResult> searchEffectiveVariables(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("userTaskKey") final long userTaskKey,
      @RequestBody(required = false)
          final UserTaskEffectiveVariableSearchQueryRequest
              userTaskEffectiveVariablesSearchQueryRequest,
      @RequestParam(name = "truncateValues", required = false, defaultValue = "true")
          final boolean truncateValues) {
    return SearchQueryRequestMapper.toUserTaskEffectiveVariableQuery(
            userTaskEffectiveVariablesSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query ->
                searchUserTaskEffectiveVariableQuery(
                    registry.userTaskServices(physicalTenantId),
                    userTaskKey,
                    query,
                    truncateValues));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{userTaskKey}/audit-logs/search")
  public ResponseEntity<AuditLogSearchQueryResult> searchAuditLogs(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long userTaskKey,
      @RequestBody(required = false)
          final UserTaskAuditLogSearchQueryRequest auditLogSearchQueryRequest) {
    return SearchQueryRequestMapper.toUserTaskAuditLogQuery(auditLogSearchQueryRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query ->
                searchUserTaskAuditLogQuery(
                    registry.userTaskServices(physicalTenantId), userTaskKey, query));
  }

  private ResponseEntity<UserTaskSearchQueryResult> search(
      final UserTaskServices userTaskServices, final UserTaskQuery query) {
    try {
      final var result =
          userTaskServices.search(query, authenticationProvider.getCamundaAuthentication());

      return ResponseEntity.ok(SearchQueryResponseMapper.toUserTaskSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<VariableSearchQueryResult> searchUserTaskVariableQuery(
      final UserTaskServices userTaskServices,
      final long userTaskKey,
      final VariableQuery query,
      final boolean truncateValues) {
    try {
      final var result =
          userTaskServices.searchUserTaskVariables(
              userTaskKey, query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(result, truncateValues));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<VariableSearchQueryResult> searchUserTaskEffectiveVariableQuery(
      final UserTaskServices userTaskServices,
      final long userTaskKey,
      final VariableQuery query,
      final boolean truncateValues) {
    try {
      final var result =
          userTaskServices.searchUserTaskEffectiveVariables(
              userTaskKey, query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(result, truncateValues));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<AuditLogSearchQueryResult> searchUserTaskAuditLogQuery(
      final UserTaskServices userTaskServices, final long userTaskKey, final AuditLogQuery query) {
    try {
      final var result =
          userTaskServices.searchUserTaskAuditLogs(
              userTaskKey, query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toAuditLogSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> assignUserTask(
      final UserTaskServices userTaskServices, final AssignUserTaskRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices.assignUserTask(
                request.userTaskKey(),
                request.assignee(),
                request.action(),
                request.allowOverride(),
                authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> completeUserTask(
      final UserTaskServices userTaskServices, final CompleteUserTaskRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices.completeUserTask(
                request.userTaskKey(),
                request.variables(),
                request.action(),
                authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> unassignUserTask(
      final UserTaskServices userTaskServices, final AssignUserTaskRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices.unassignUserTask(
                request.userTaskKey(),
                request.action(),
                authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> updateUserTask(
      final UserTaskServices userTaskServices, final UpdateUserTaskRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            userTaskServices.updateUserTask(
                request.userTaskKey(),
                request.changeset(),
                request.action(),
                authenticationProvider.getCamundaAuthentication()));
  }
}
