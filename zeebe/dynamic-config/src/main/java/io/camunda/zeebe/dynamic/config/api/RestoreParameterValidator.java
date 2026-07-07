/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.Nullable;

/**
 * Validates the parameters of a restore. A backup ID and a time range (from/to) are mutually
 * exclusive, and when both bounds of the time range are given, {@code from} must be before {@code
 * to}. Providing neither is allowed (e.g. restoring the latest backup). A time range is only
 * accepted when {@code continuousBackups} is {@code true} (e.g. the configured secondary storage
 * supports it).
 *
 * <p>Shared by the standalone restore application and the cluster-configuration restore request so
 * both enforce the same rules with the same error messages.
 */
public final class RestoreParameterValidator {

  private RestoreParameterValidator() {}

  /**
   * @throws IllegalArgumentException if a backup ID is combined with a time range, if a time range
   *     is given when {@code continuousBackups} is {@code false}, or if {@code from} is after
   *     {@code to}.
   */
  public static void validate(
      final boolean hasBackupId,
      final @Nullable String from,
      final @Nullable String to,
      final boolean continuousBackups) {
    validate(
        hasBackupId, parseTimestamp(from, "from"), parseTimestamp(to, "to"), continuousBackups);
  }

  public static void validate(
      final boolean hasBackupId,
      final @Nullable Instant from,
      final @Nullable Instant to,
      final boolean continuousBackups) {
    final boolean hasTimeRange = from != null || to != null;
    illegalArgument(!hasBackupId && !hasTimeRange, "Must specify either backupId or from/to.");

    illegalArgument(
        hasBackupId && hasTimeRange,
        "Cannot specify both backupId and from/to parameters. Choose one approach.");

    illegalArgument(
        hasTimeRange && !continuousBackups,
        "Time range restore (from/to) is only supported for continuous backups.");

    illegalArgument(
        from != null && to != null && from.isAfter(to),
        "Invalid time range: from (%s) must be before to (%s)".formatted(from, to));
  }

  private static @Nullable Instant parseTimestamp(
      final @Nullable String value, final String field) {
    if (value == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (final DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Invalid %s timestamp '%s': must be an ISO 8601 date-time.".formatted(field, value));
    }
  }

  private static void illegalArgument(final boolean condition, final String s) {
    if (condition) {
      throw new IllegalArgumentException(s);
    }
  }
}
