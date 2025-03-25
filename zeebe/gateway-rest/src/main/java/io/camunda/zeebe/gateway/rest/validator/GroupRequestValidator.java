/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ILLEGAL_CHARACTER;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_PATTERN;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.ID_REGEX;
import static io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns.MAX_LENGTH;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.GroupCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.GroupUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class GroupRequestValidator {

  private GroupRequestValidator() {}

  private static void validateGroupId(final String groupId, final List<String> violations) {
    if (groupId == null || groupId.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("groupId"));
    } else if (groupId.length() > MAX_LENGTH) {
      violations.add(ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted("id", MAX_LENGTH));
    } else if (!ID_PATTERN.matcher(groupId).matches()) {
      violations.add(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted("id", ID_REGEX));
    }
  }

  private static void validateGroupName(final String name, final List<String> violations) {
    if (name == null || name.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("name"));
    }
  }

  public static Optional<ProblemDetail> validateUpdateRequest(final GroupUpdateRequest request) {
    return validate(
        violations -> {
          validateGroupName(request.getChangeset().getName(), violations);
        });
  }

  public static Optional<ProblemDetail> validateCreateRequest(final GroupCreateRequest request) {
    return validate(
        violations -> {
          validateGroupId(request.getGroupId(), violations);
          validateGroupName(request.getName(), violations);
        });
  }
}
