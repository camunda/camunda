/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.util;

import java.nio.charset.StandardCharsets;

public class TruncateUtil {

  public static boolean shouldTruncate(
      final String value, final int sizeLimit, final Integer byteLimit) {
    if (value == null) {
      return false;
    }
    return value.length() > sizeLimit
        || byteLimit != null && value.getBytes(StandardCharsets.UTF_8).length > byteLimit;
  }

  public static String truncateValue(
      final String value, final int sizeLimit, final Integer byteLimit) {
    var truncatedValue = value;

    if (truncatedValue.length() > sizeLimit) {
      truncatedValue = truncatedValue.substring(0, sizeLimit);
    }

    if ((byteLimit != null && truncatedValue.getBytes(StandardCharsets.UTF_8).length > byteLimit)) {
      truncatedValue = truncateBytes(truncatedValue, byteLimit);
    }
    return truncatedValue;
  }

  /**
   * Truncates the given string to ensure that its byte representation does not exceed the specified
   * maximum byte size. This is necessary for DB vendors, which have a limit on the byte size for
   * char columns. Currently, this is needed for Oracle's varchar2 only.
   *
   * @param original the original string to truncate
   * @param maxBytes the maximum allowed byte size
   * @return the truncated string, or an empty string if it cannot be truncated to fit within the
   *     limit
   */
  public static String truncateBytes(final String original, final int maxBytes) {
    final byte[] bytes = original.getBytes(StandardCharsets.UTF_8);

    if (bytes.length <= maxBytes) {
      return original;
    }

    var truncatedVariable = original;

    while (truncatedVariable.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
      truncatedVariable = truncatedVariable.substring(0, truncatedVariable.length() - 1);

      if (truncatedVariable.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
        return truncatedVariable;
      }
    }
    return "";
  }
}
