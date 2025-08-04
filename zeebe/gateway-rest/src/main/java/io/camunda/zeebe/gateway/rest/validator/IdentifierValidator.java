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

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class IdentifierValidator {

  public static final int MAX_LENGTH = 256;

  public static void validateId(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Pattern idPattern) {
    validateId(id, propertyName, violations, idPattern, s -> true);
  }

  public static void validateId(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Pattern idPattern,
      final Function<String, Boolean> additionalCheck) {
    if (id == null || id.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(propertyName));
    } else if (id.length() > MAX_LENGTH) {
      violations.add(ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(propertyName, MAX_LENGTH));
    } else if (!idPattern.matcher(id).matches() && additionalCheck.apply(id)) {
      violations.add(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted(propertyName, idPattern));
    }
  }
}
