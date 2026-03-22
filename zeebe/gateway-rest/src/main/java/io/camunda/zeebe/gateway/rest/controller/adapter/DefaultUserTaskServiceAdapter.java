/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAssignmentRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAuditLogSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskCompletionRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskVariableSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.UserTaskServices;
import io.camunda.zeebe.gateway.rest.controller.generated.UserTaskServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultUserTaskServiceAdapter implements UserTaskServiceAdapter {

  private final UserTaskServices userTaskServices;

  public DefaultUserTaskServiceAdapter(final UserTaskServices userTaskServices) {
    this.userTaskServices = userTaskServices;
  }

  @Override
  public ResponseEntity<Void> completeUserTask(
      final Long userTaskKey,
      final GeneratedUserTaskCompletionRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    final var mapped = RequestMapper.toUserTaskCompletionRequest(requestStrict, userTaskKey);
    return RequestExecutor.executeSync(
        () ->
            userTaskServices.completeUserTask(
                mapped.userTaskKey(), mapped.variables(), mapped.action(), authentication));
  }

  @Override
  public ResponseEntity<Void> assignUserTask(
      final Long userTaskKey,
      final GeneratedUserTaskAssignmentRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toUserTaskAssignmentRequest(requestStrict, userTaskKey)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () ->
                        userTaskServices.assignUserTask(
                            mapped.userTaskKey(),
                            mapped.assignee(),
                            mapped.action(),
                            mapped.allowOverride(),
                            authentication)));
  }

  @Override
  public ResponseEntity<Object> getUserTask(
      final Long userTaskKey, final CamundaAuthentication authentication) {
    try {
      final var userTask = userTaskServices.getByKey(userTaskKey, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserTask(userTask));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Void> updateUserTask(
      final Long userTaskKey,
      final GeneratedUserTaskUpdateRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toUserTaskUpdateRequest(requestStrict, userTaskKey)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () ->
                        userTaskServices.updateUserTask(
                            mapped.userTaskKey(),
                            mapped.changeset(),
                            mapped.action(),
                            authentication)));
  }

  @Override
  public ResponseEntity<Object> getUserTaskForm(
      final Long userTaskKey, final CamundaAuthentication authentication) {
    try {
      return userTaskServices
          .getUserTaskForm(userTaskKey, authentication)
          .<Object>map(SearchQueryResponseMapper::toFormItem)
          .<ResponseEntity<Object>>map(ResponseEntity::ok)
          .orElseGet(() -> ResponseEntity.noContent().build());
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Void> unassignUserTask(
      final Long userTaskKey, final CamundaAuthentication authentication) {
    final var mapped = RequestMapper.toUserTaskUnassignmentRequest(userTaskKey);
    return RequestExecutor.executeSync(
        () ->
            userTaskServices.unassignUserTask(
                mapped.userTaskKey(), mapped.action(), authentication));
  }

  @Override
  public ResponseEntity<Object> searchUserTasks(
      final GeneratedUserTaskSearchQueryRequestStrictContract queryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toUserTaskQueryStrict(queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> {
              try {
                final var result = userTaskServices.search(q, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toUserTaskSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> searchUserTaskVariables(
      final Long userTaskKey,
      final Boolean truncateValues,
      final GeneratedUserTaskVariableSearchQueryRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    final boolean truncate = truncateValues == null || truncateValues;
    return SearchQueryRequestMapper.toUserTaskVariableQueryStrict(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    userTaskServices.searchUserTaskVariables(userTaskKey, query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toVariableSearchQueryResponse(result, truncate));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> searchUserTaskAuditLogs(
      final Long userTaskKey,
      final GeneratedUserTaskAuditLogSearchQueryRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toUserTaskAuditLogQueryStrict(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    userTaskServices.searchUserTaskAuditLogs(userTaskKey, query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toAuditLogSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }
}
