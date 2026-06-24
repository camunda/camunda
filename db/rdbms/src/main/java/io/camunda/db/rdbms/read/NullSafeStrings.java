/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read;

/**
 * Oracle treats empty strings as NULL. This utility converts null values back to empty strings for
 * fields that are required (non-nullable) in the API specification but may legitimately be empty
 * (e.g., protobuf default values).
 */
public final class NullSafeStrings {

  private NullSafeStrings() {
    // utility class — no instantiation
  }

  /** Returns the given string, or {@code ""} if it is {@code null}. */
  public static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }
}
