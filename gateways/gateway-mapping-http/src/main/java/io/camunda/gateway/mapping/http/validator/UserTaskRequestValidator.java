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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAssignmentRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskUpdateRequestStrictContract;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class UserTaskRequestValidator {

  public static Optional<ProblemDetail> validateAssignmentRequest(
      final GeneratedUserTaskAssignmentRequestStrictContract request) {
    return validate(
        violations -> {
          if (request.assignee() == null || request.assignee().isBlank()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("assignee"));
          }
        });
  }

  public static Optional<ProblemDetail> validateUpdateRequest(
      final GeneratedUserTaskUpdateRequestStrictContract request) {
    final var changeset = request != null ? request.changeset() : null;
    final boolean changesetEmpty =
        changeset == null
            || (changeset.followUpDate() == null
                && changeset.dueDate() == null
                && changeset.candidateGroups() == null
                && changeset.candidateUsers() == null
                && changeset.priority() == null);
    return validate(
        violations -> {
          if (request == null || (request.action() == null && changesetEmpty)) {
            violations.add(ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET);
          }
          if (!changesetEmpty) {
            validateDate(changeset.dueDate(), "due date", violations);
            validateDate(changeset.followUpDate(), "follow-up date", violations);
          }
        });
  }
}
