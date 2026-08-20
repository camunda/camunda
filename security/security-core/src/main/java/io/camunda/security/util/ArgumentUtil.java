/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.util;

public class ArgumentUtil {
  public static void ensureNotNull(final String property, final Object value) {
    if (value == null) {
      throw new IllegalArgumentException(property + " must not be null");
    }
  }

  public static void ensureNotEmpty(final String property, final String value) {
    if (value.isEmpty()) {
      throw new IllegalArgumentException(property + " must not be empty");
    }
  }

  public static void ensureNotNullOrEmpty(final String property, final String value) {
    ensureNotNull(property, value);
    ensureNotEmpty(property, value);
  }
}
