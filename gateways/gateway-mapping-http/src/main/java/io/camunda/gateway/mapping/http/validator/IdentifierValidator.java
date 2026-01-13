/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_ILLEGAL_CHARACTER;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_TOO_MANY_CHARACTERS;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * @deprecated use {@link io.camunda.security.validation.IdentifierValidator} instead
 */
@Deprecated
public class IdentifierValidator {

  /**
   * Stricter validation for tenant IDs, matching {@link
   * io.camunda.gateway.mapping.http.validator.MultiTenancyValidator}.
   */
  public static final Pattern TENANT_ID_MASK = Pattern.compile("^[\\w\\.-]{1,31}$");

  private static final int MAX_LENGTH = 256;
  private static final int TENANT_ID_MAX_LENGTH = 31;

  public static void validateId(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Pattern idPattern) {
    validateId(id, propertyName, violations, idPattern, s -> false);
  }

  public static void validateId(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Pattern idPattern,
      final Function<String, Boolean> alternativeCheck) {
    validateIdInternal(id, propertyName, violations, idPattern, alternativeCheck, MAX_LENGTH);
  }

  public static void validateTenantId(
      final String id,
      final List<String> violations,
      final Function<String, Boolean> alternativeCheck) {
    validateIdInternal(
        id, "tenantId", violations, TENANT_ID_MASK, alternativeCheck, TENANT_ID_MAX_LENGTH);
  }

  private static void validateIdInternal(
      final String id,
      final String propertyName,
      final List<String> violations,
      final Pattern idPattern,
      final Function<String, Boolean> alternativeCheck,
      final int maxLength) {
    if (id == null || id.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted(propertyName));
    } else if (id.length() > maxLength) {
      violations.add(ERROR_MESSAGE_TOO_MANY_CHARACTERS.formatted(propertyName, maxLength));
    } else if (!(idPattern.matcher(id).matches() || alternativeCheck.apply(id))) {
      violations.add(ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted(propertyName, idPattern));
    }
  }
}
