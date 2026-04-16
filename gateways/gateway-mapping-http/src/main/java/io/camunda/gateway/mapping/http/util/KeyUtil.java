/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.util;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_KEY_FORMAT;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class KeyUtil {

  /**
   * Returns a function that converts a key string to Long, collecting errors into {@code
   * validationErrors} instead of throwing.
   */
  public static Function<String, Long> mapKeyToLong(
      final String fieldName, final List<String> validationErrors) {
    return key -> {
      if (key == null) {
        return null;
      }
      try {
        return Long.parseLong(key);
      } catch (final NumberFormatException e) {
        validationErrors.add(ERROR_MESSAGE_INVALID_KEY_FORMAT.formatted(fieldName, key));
        return null;
      }
    };
  }

  public static Long keyToLong(final String key) {
    return key != null ? Long.parseLong(key) : null;
  }

  public static String keyToString(final Long value) {
    return value != null ? String.valueOf(value) : null;
  }

  public static Optional<Long> tryParseLong(final String key) {
    try {
      return Optional.ofNullable(keyToLong(key));
    } catch (final NumberFormatException e) {
      return Optional.empty();
    }
  }
}
