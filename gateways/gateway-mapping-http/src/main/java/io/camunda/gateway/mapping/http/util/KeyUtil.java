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
import org.jspecify.annotations.Nullable;

public class KeyUtil {

  public static @Nullable Long keyToLong(final @Nullable String key) {
    return key != null ? Long.parseLong(key) : null;
  }

  public static Function<String, @Nullable Long> mapKeyToLong(
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

  public static String keyToString(final long value) {
    return String.valueOf(value);
  }

  public static @Nullable String keyToString(final @Nullable Long value) {
    return value != null ? String.valueOf(value) : null;
  }

  public static Optional<Long> tryParseLong(final @Nullable String key) {
    try {
      return Optional.ofNullable(keyToLong(key));
    } catch (final NumberFormatException e) {
      return Optional.empty();
    }
  }
}
