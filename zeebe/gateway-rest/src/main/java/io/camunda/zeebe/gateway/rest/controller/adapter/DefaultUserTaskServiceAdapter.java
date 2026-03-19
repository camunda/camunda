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
import io.camunda.gateway.mapping.http.search.GeneratedSearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSearchQueryResponseMapper;
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
      final String userTaskKey,
      final GeneratedUserTaskCompletionRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    final var mapped =
        RequestMapper.toUserTaskCompletionRequest(requestStrict, Long.parseLong(userTaskKey));
    return RequestExecutor.executeSync(
        () ->
            userTaskServices.completeUserTask(
                mapped.userTaskKey(), mapped.variables(), mapped.action(), authentication));
  }

  @Override
  public ResponseEntity<Void> assignUserTask(
      final String userTaskKey,
      final GeneratedUserTaskAssignmentRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toUserTaskAssignmentRequest(requestStrict, Long.parseLong(userTaskKey))
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
      final String userTaskKey, final CamundaAuthentication authentication) {
    try {
      final var userTask = userTaskServices.getByKey(Long.parseLong(userTaskKey), authentication);
      return ResponseEntity.ok(GeneratedSearchQueryResponseMapper.toUserTask(userTask));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Void> updateUserTask(
      final String userTaskKey,
      final GeneratedUserTaskUpdateRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toUserTaskUpdateRequest(requestStrict, Long.parseLong(userTaskKey))
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
      final String userTaskKey, final CamundaAuthentication authentication) {
    try {
      return userTaskServices
          .getUserTaskForm(Long.parseLong(userTaskKey), authentication)
          .<Object>map(GeneratedSearchQueryResponseMapper::toFormItem)
          .<ResponseEntity<Object>>map(ResponseEntity::ok)
          .orElseGet(() -> ResponseEntity.noContent().build());
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Void> unassignUserTask(
      final String userTaskKey, final CamundaAuthentication authentication) {
    final var mapped = RequestMapper.toUserTaskUnassignmentRequest(Long.parseLong(userTaskKey));
    return RequestExecutor.executeSync(
        () ->
            userTaskServices.unassignUserTask(
                mapped.userTaskKey(), mapped.action(), authentication));
  }

  @Override
  public ResponseEntity<Object> searchUserTasks(
      final GeneratedUserTaskSearchQueryRequestStrictContract queryStrict,
      final CamundaAuthentication authentication) {
    return GeneratedSearchQueryRequestMapper.toUserTaskQueryStrict(queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> {
              try {
                final var result = userTaskServices.search(q, authentication);
                return ResponseEntity.ok(
                    GeneratedSearchQueryResponseMapper.toUserTaskSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> searchUserTaskVariables(
      final String userTaskKey,
      final Boolean truncateValues,
      final GeneratedUserTaskVariableSearchQueryRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    final boolean truncate = truncateValues == null || truncateValues;
    return GeneratedSearchQueryRequestMapper.toUserTaskVariableQueryStrict(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    userTaskServices.searchUserTaskVariables(
                        Long.parseLong(userTaskKey), query, authentication);
                return ResponseEntity.ok(
                    GeneratedSearchQueryResponseMapper.toVariableSearchQueryResponse(
                        result, truncate));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> searchUserTaskAuditLogs(
      final String userTaskKey,
      final GeneratedUserTaskAuditLogSearchQueryRequestStrictContract requestStrict,
      final CamundaAuthentication authentication) {
    return GeneratedSearchQueryRequestMapper.toUserTaskAuditLogQueryStrict(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    userTaskServices.searchUserTaskAuditLogs(
                        Long.parseLong(userTaskKey), query, authentication);
                return ResponseEntity.ok(
                    GeneratedSearchQueryResponseMapper.toAuditLogSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }
}
