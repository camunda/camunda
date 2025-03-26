/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

<<<<<<< HEAD
import java.util.Optional;

=======
>>>>>>> 0b906ae6 (fix: adjust remaining number types to strings in the API)
public class KeyUtil {

  public static Long keyToLong(final String key) {
    return key != null ? Long.parseLong(key) : null;
  }
<<<<<<< HEAD

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
=======
>>>>>>> 0b906ae6 (fix: adjust remaining number types to strings in the API)
}
