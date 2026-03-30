/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.util;

import java.util.Optional;

public class KeyUtil {

  public static Long keyToLong(final String key) {
    if (key == null) {
      return null;
    }
    try {
      return Long.parseLong(key);
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException(
          "The provided value '%s' is not a valid key.".formatted(key), e);
    }
  }

  public static String keyToString(final Long value) {
    return value != null ? String.valueOf(value) : null;
  }

  public static Optional<Long> tryParseLong(final String key) {
    try {
      return Optional.ofNullable(keyToLong(key));
    } catch (final IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
