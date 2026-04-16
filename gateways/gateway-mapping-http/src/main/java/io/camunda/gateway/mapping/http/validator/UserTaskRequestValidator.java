/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;

import io.camunda.gateway.protocol.model.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.UserTaskUpdateRequest;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class UserTaskRequestValidator {

  public static Optional<ProblemDetail> validateAssignmentRequest(
      final UserTaskAssignmentRequest request) {
    return validate(
        violations -> {
          if (request.getAssignee() == null || request.getAssignee().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("assignee"));
          }
        });
  }

  public static Optional<ProblemDetail> validateUpdateRequest(final UserTaskUpdateRequest request) {
    final var changeset = request != null ? request.getChangeset() : null;
    final boolean changesetEmpty =
        changeset == null
            || (changeset.getFollowUpDate() == null
                && changeset.getDueDate() == null
                && changeset.getCandidateGroups() == null
                && changeset.getCandidateUsers() == null
                && changeset.getPriority() == null);
    return validate(
        violations -> {
          if (request == null || (request.getAction() == null && changesetEmpty)) {
            violations.add(ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET);
          }
          if (!changesetEmpty) {
            validateDate(changeset.getDueDate(), "due date", violations);
            validateDate(changeset.getFollowUpDate(), "follow-up date", violations);
          }
        });
  }
}
