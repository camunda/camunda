/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessage.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class UserTaskRequestValidator {

  public static Optional<ProblemDetail> validateAssignmentRequest(
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
}
