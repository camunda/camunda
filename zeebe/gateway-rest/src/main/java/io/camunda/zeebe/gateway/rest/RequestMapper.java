/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import io.camunda.zeebe.auth.api.JwtAuthorizationBuilder;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.gateway.protocol.rest.Changeset;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.gateway.rest.impl.broker.request.BrokerUserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.rest.impl.broker.request.BrokerUserTaskCompletionRequest;
import io.camunda.zeebe.gateway.rest.impl.broker.request.BrokerUserTaskUpdateRequest;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ServerWebExchange;

public class RequestMapper {

  // TODO: create proper multi-tenancy handling, e.g. via HTTP filter
  public static final String TENANT_CTX_KEY = "io.camunda.zeebe.broker.rest.tenantIds";

  public static Either<ProblemDetail, BrokerUserTaskCompletionRequest> toUserTaskCompletionRequest(
      final UserTaskCompletionRequest completionRequest,
      final long userTaskKey,
      final ServerWebExchange context) {

    final var brokerRequest =
        new BrokerUserTaskCompletionRequest(
            userTaskKey,
            getDocumentOrEmpty(completionRequest, UserTaskCompletionRequest::getVariables),
            getStringOrEmpty(completionRequest, UserTaskCompletionRequest::getAction));

    final String authorizationToken = getAuthorizationToken(context);
    brokerRequest.setAuthorization(authorizationToken);

    return Either.right(brokerRequest);
  }

  public static Either<ProblemDetail, BrokerUserTaskAssignmentRequest> toUserTaskAssignmentRequest(
      final UserTaskAssignmentRequest assignmentRequest,
      final long userTaskKey,
      final ServerWebExchange context) {

    final var validationErrorResponse = validateAssignmentRequest(assignmentRequest);
    if (validationErrorResponse.isPresent()) {
      return Either.left(validationErrorResponse.get());
    }

    String action = getStringOrEmpty(assignmentRequest, UserTaskAssignmentRequest::getAction);
    if (action.isBlank()) {
      action = "assign";
    }

    final UserTaskIntent intent =
        assignmentRequest.getAllowOverride() == null || assignmentRequest.getAllowOverride()
            ? UserTaskIntent.ASSIGN
            : UserTaskIntent.CLAIM;

    final BrokerUserTaskAssignmentRequest brokerRequest =
        new BrokerUserTaskAssignmentRequest(
            userTaskKey, assignmentRequest.getAssignee(), action, intent);

    final String authorizationToken = getAuthorizationToken(context);
    brokerRequest.setAuthorization(authorizationToken);

    return Either.right(brokerRequest);
  }

  public static Either<ProblemDetail, BrokerUserTaskAssignmentRequest>
      toUserTaskUnassignmentRequest(final long userTaskKey, final ServerWebExchange context) {
    final BrokerUserTaskAssignmentRequest brokerRequest =
        new BrokerUserTaskAssignmentRequest(userTaskKey, "", "unassign", UserTaskIntent.ASSIGN);

    final String authorizationToken = getAuthorizationToken(context);
    brokerRequest.setAuthorization(authorizationToken);

    return Either.right(brokerRequest);
  }

  private static Optional<ProblemDetail> validateAssignmentRequest(
      final UserTaskAssignmentRequest assignmentRequest) {
    if (assignmentRequest.getAssignee() == null || assignmentRequest.getAssignee().isBlank()) {
      final String message = "No assignee provided";
      final ProblemDetail problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, message, INVALID_ARGUMENT.name());
      return Optional.of(problemDetail);
    }
    return Optional.empty();
  }

  public static Either<ProblemDetail, BrokerUserTaskUpdateRequest> toUserTaskUpdateRequest(
      final UserTaskUpdateRequest updateRequest,
      final long userTaskKey,
      final ServerWebExchange context) {

    final var validationErrorResponse = validateUpdateRequest(updateRequest);
    if (validationErrorResponse.isPresent()) {
      return Either.left(validationErrorResponse.get());
    }

    final var brokerRequest =
        new BrokerUserTaskUpdateRequest(
            userTaskKey,
            getRecordWithChangedAttributes(updateRequest),
            getStringOrEmpty(updateRequest, UserTaskUpdateRequest::getAction));

    brokerRequest.setAuthorization(getAuthorizationToken(context));

    return Either.right(brokerRequest);
  }

  private static Optional<ProblemDetail> validateUpdateRequest(
      final UserTaskUpdateRequest updateRequest) {
    if (updateRequest == null
        || (updateRequest.getAction() == null && isEmpty(updateRequest.getChangeset()))) {
      final String message =
          "No update data provided. Provide at least an \"action\" or a non-null value for a supported attribute in the \"changeset\".";
      final ProblemDetail problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, message, INVALID_ARGUMENT.name());
      return Optional.of(problemDetail);
    }
    return Optional.empty();
  }

  private static boolean isEmpty(final Changeset changeset) {
    return changeset == null
        || (changeset.getFollowUpDate() == null
            && changeset.getDueDate() == null
            && changeset.getCandidateGroups() == null
            && changeset.getCandidateUsers() == null);
  }

  private static String getAuthorizationToken(final ServerWebExchange context) {
    final List<String> authorizedTenants =
        context.getAttributeOrDefault(
            TENANT_CTX_KEY, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    return Authorization.jwtEncoder()
        .withIssuer(JwtAuthorizationBuilder.DEFAULT_ISSUER)
        .withAudience(JwtAuthorizationBuilder.DEFAULT_AUDIENCE)
        .withSubject(JwtAuthorizationBuilder.DEFAULT_SUBJECT)
        .withClaim(Authorization.AUTHORIZED_TENANTS, authorizedTenants)
        .encode();
  }

  private static UserTaskRecord getRecordWithChangedAttributes(
      final UserTaskUpdateRequest updateRequest) {
    final var record = new UserTaskRecord();
    if (updateRequest == null || updateRequest.getChangeset() == null) {
      return record;
    }
    final Changeset changeset = updateRequest.getChangeset();
    if (changeset.getCandidateGroups() != null) {
      record.setCandidateGroupsList(changeset.getCandidateGroups()).setCandidateGroupsChanged();
    }
    if (changeset.getCandidateUsers() != null) {
      record.setCandidateUsersList(changeset.getCandidateUsers()).setCandidateUsersChanged();
    }
    if (changeset.getDueDate() != null) {
      record.setDueDate(changeset.getDueDate()).setDueDateChanged();
    }
    if (changeset.getFollowUpDate() != null) {
      record.setFollowUpDate(changeset.getFollowUpDate()).setFollowUpDateChanged();
    }
    return record;
  }

  private static DirectBuffer getDocumentOrEmpty(
      final UserTaskCompletionRequest request,
      final Function<UserTaskCompletionRequest, Map<String, Object>> variablesExtractor) {
    final Map<String, Object> value = request == null ? null : variablesExtractor.apply(request);
    return value == null || value.isEmpty()
        ? DocumentValue.EMPTY_DOCUMENT
        : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
  }

  private static <R> String getStringOrEmpty(
      final R request, final Function<R, String> valueExtractor) {
    final String value = request == null ? null : valueExtractor.apply(request);
    return value == null ? "" : value;
  }
}
