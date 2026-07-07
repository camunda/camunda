/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
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

  public static void validate(final RestoreRequest request) {
    final var instantFrom = parseTimestamp(request.from(), "from");
    final var instantTo = parseTimestamp(request.to(), "to");
    final var hasTimeRange = hasTimeRange(instantFrom, instantTo);
    final var hasBackupIds = !request.backupIds().isEmpty();
    illegalArgument(
        hasBackupIds && hasTimeRange,
        "Cannot specify both backupId and from/to parameters. Choose one approach.");

    final var databaseType =
        request.databaseType() == null ? "" : request.databaseType().toLowerCase(Locale.ROOT);
    switch (databaseType) {
      case "rdbms", "none" -> {
        illegalArgument(
            hasTimeRange && !request.continuousBackups(),
            "Time range restore (from/to) is only supported for continuous backups.");

        illegalArgument(
            instantFrom != null && instantTo != null && instantFrom.isAfter(instantTo),
            "Invalid time range: from (%s) must be before to (%s)"
                .formatted(instantFrom, instantTo));
      }
      case "elasticsearch", "opensearch" -> {
        illegalArgument(
            hasTimeRange,
            "Time range restore (from/to) is not supported for %s.".formatted(databaseType));
        illegalArgument(!hasBackupIds, "No backupId specified");
      }
      default ->
          throw new IllegalArgumentException("Invalid database type: " + request.databaseType());
    }
  }

  private static boolean hasTimeRange(@Nullable final Instant from, @Nullable final Instant to) {
    return from != null || to != null;
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
