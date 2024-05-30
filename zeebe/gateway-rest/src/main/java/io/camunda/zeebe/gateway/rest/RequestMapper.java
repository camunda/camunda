/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import io.camunda.zeebe.auth.api.JwtAuthorizationBuilder;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskCompletionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequestChangeset;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.util.Either;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class RequestMapper {

  private static final String ERROR_MESSAGE_EMPTY_ATTRIBUTE = "No %s provided";
  private static final String ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE =
      "The value for %s is '%s' but must be %s";
  private static final String ERROR_MESSAGE_DATE_PARSING =
      "The provided %s '%s' cannot be parsed as a date according to RFC 3339, section 5.6.";
  private static final String ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET =
      """
      No update data provided. Provide at least an "action" or a non-null value \
      for a supported attribute in the "changeset".""";

  public static Either<ProblemDetail, BrokerUserTaskCompletionRequest> toUserTaskCompletionRequest(
      final UserTaskCompletionRequest completionRequest, final long userTaskKey) {

    final var brokerRequest =
        new BrokerUserTaskCompletionRequest(
            userTaskKey,
            getDocumentOrEmpty(completionRequest, UserTaskCompletionRequest::getVariables),
            getStringOrEmpty(completionRequest, UserTaskCompletionRequest::getAction));

    final String authorizationToken = getAuthorizationToken();
    brokerRequest.setAuthorization(authorizationToken);

    return Either.right(brokerRequest);
  }

  public static Either<ProblemDetail, BrokerUserTaskAssignmentRequest> toUserTaskAssignmentRequest(
      final UserTaskAssignmentRequest assignmentRequest, final long userTaskKey) {

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

    final String authorizationToken = getAuthorizationToken();
    brokerRequest.setAuthorization(authorizationToken);

    return Either.right(brokerRequest);
  }

  public static Either<ProblemDetail, BrokerUserTaskAssignmentRequest>
      toUserTaskUnassignmentRequest(final long userTaskKey) {
    final BrokerUserTaskAssignmentRequest brokerRequest =
        new BrokerUserTaskAssignmentRequest(userTaskKey, "", "unassign", UserTaskIntent.ASSIGN);

    final String authorizationToken = getAuthorizationToken();
    brokerRequest.setAuthorization(authorizationToken);

    return Either.right(brokerRequest);
  }

  public static Either<ProblemDetail, BrokerUserTaskUpdateRequest> toUserTaskUpdateRequest(
      final UserTaskUpdateRequest updateRequest, final long userTaskKey) {

    final var validationErrorResponse = validateUpdateRequest(updateRequest);
    if (validationErrorResponse.isPresent()) {
      return Either.left(validationErrorResponse.get());
    }

    final var brokerRequest =
        new BrokerUserTaskUpdateRequest(
            userTaskKey,
            getRecordWithChangedAttributes(updateRequest),
            getStringOrEmpty(updateRequest, UserTaskUpdateRequest::getAction));

    brokerRequest.setAuthorization(getAuthorizationToken());

    return Either.right(brokerRequest);
  }

  public static Either<ProblemDetail, BrokerActivateJobsRequest> toJobsActivationRequest(
      final JobActivationRequest activationRequest) {

    final var validationErrorResponse = validateJobActivationRequest(activationRequest);
    if (validationErrorResponse.isPresent()) {
      return Either.left(validationErrorResponse.get());
    }

    final var brokerRequest =
        new BrokerActivateJobsRequest(activationRequest.getType())
            .setMaxJobsToActivate(activationRequest.getMaxJobsToActivate())
            .setTenantIds(
                getStringListOrEmpty(activationRequest, JobActivationRequest::getTenantIds))
            .setTimeout(activationRequest.getTimeout())
            .setWorker(getStringOrEmpty(activationRequest, JobActivationRequest::getWorker))
            .setVariables(
                getStringListOrEmpty(activationRequest, JobActivationRequest::getFetchVariable));

    final String authorizationToken = getAuthorizationToken();
    brokerRequest.setAuthorization(authorizationToken);

    return Either.right(brokerRequest);
  }

  private static Optional<ProblemDetail> validateAssignmentRequest(
      final UserTaskAssignmentRequest assignmentRequest) {
    if (assignmentRequest.getAssignee() == null || assignmentRequest.getAssignee().isBlank()) {
      final ProblemDetail problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("assignee"),
              INVALID_ARGUMENT.name());
      return Optional.of(problemDetail);
    }
    return Optional.empty();
  }

  private static Optional<ProblemDetail> validateUpdateRequest(
      final UserTaskUpdateRequest updateRequest) {
    final List<String> violations = new ArrayList<>();
    if (updateRequest == null
        || (updateRequest.getAction() == null && isEmpty(updateRequest.getChangeset()))) {
      violations.add(ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET);
    }
    if (updateRequest != null && !isEmpty(updateRequest.getChangeset())) {
      final UserTaskUpdateRequestChangeset changeset = updateRequest.getChangeset();
      validateDate(changeset.getDueDate(), "due date", violations);
      validateDate(changeset.getFollowUpDate(), "follow-up date", violations);
    }
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        RestErrorMapper.createProblemDetail(
            HttpStatus.BAD_REQUEST, String.join(" ", violations), INVALID_ARGUMENT.name()));
  }

  private static Optional<ProblemDetail> validateJobActivationRequest(
      final JobActivationRequest activationRequest) {
    final List<String> violations = new ArrayList<>();
    if (activationRequest.getType() == null || activationRequest.getType().isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("type"));
    }
    if (activationRequest.getTimeout() == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("timeout"));
    } else if (activationRequest.getTimeout() < 1) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "timeout", activationRequest.getTimeout(), "greater than 0"));
    }
    if (activationRequest.getMaxJobsToActivate() == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("maxJobsToActivate"));
    } else if (activationRequest.getMaxJobsToActivate() < 1) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "maxJobsToActivate", activationRequest.getTimeout(), "greater than 0"));
    }
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        RestErrorMapper.createProblemDetail(
            HttpStatus.BAD_REQUEST, String.join(". ", violations), INVALID_ARGUMENT.name()));
  }

  private static void validateDate(
      final String dateString, final String attributeName, final List<String> violations) {
    if (dateString != null && !dateString.isEmpty()) {
      try {
        ZonedDateTime.parse(dateString);
      } catch (final DateTimeParseException ex) {
        violations.add(ERROR_MESSAGE_DATE_PARSING.formatted(attributeName, dateString));
      }
    }
  }

  private static boolean isEmpty(final UserTaskUpdateRequestChangeset changeset) {
    return changeset == null
        || (changeset.getFollowUpDate() == null
            && changeset.getDueDate() == null
            && changeset.getCandidateGroups() == null
            && changeset.getCandidateUsers() == null);
  }

  private static String getAuthorizationToken() {
    final List<String> authorizedTenants = TenantAttributeHolder.tenantIds();

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
    final UserTaskUpdateRequestChangeset changeset = updateRequest.getChangeset();
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

  private static <R> List<String> getStringListOrEmpty(
      final R request, final Function<R, List<String>> valueExtractor) {
    final List<String> value = request == null ? null : valueExtractor.apply(request);
    return value == null ? Collections.emptyList() : value;
  }
}
