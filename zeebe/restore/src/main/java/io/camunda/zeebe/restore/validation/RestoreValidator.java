/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.validation;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Preconditions;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Validates {@link RestoreRequest}s submitted through the cluster-configuration API while a broker
 * is in recovery mode.
 */
public final class RestoreValidator
    implements ClusterConfigurationRequestValidator<RestoreRequest, RestoreRequest> {

  private final @Nullable BackupStore backupStore;

  public RestoreValidator(final @Nullable BackupStore backupStore) {
    this.backupStore = backupStore;
  }

  @Override
  public Class<RestoreRequest> requestType() {
    return RestoreRequest.class;
  }

  @Override
  public @NonNull Either<Exception, RestoreRequest> validate(
      final @NonNull RestoreRequest request) {
    if (backupStore == null) {
      return Either.left(
          new InvalidRequest("Cannot restore: no backup store is configured on this broker."));
    }
    try {
      validateParameters(request);
    } catch (final IllegalArgumentException e) {
      return Either.left(new InvalidRequest(e.getMessage()));
    }
    return Either.right(request);
  }

  @VisibleForTesting
  static void validateParameters(final RestoreRequest request) {
    final var instantFrom = parseTimestamp(request.from(), "from");
    final var instantTo = parseTimestamp(request.to(), "to");
    final var hasTimeRange = hasTimeRange(instantFrom, instantTo);
    final var hasBackupIds = !request.backupIds().isEmpty();
    Preconditions.test(
        hasBackupIds && hasTimeRange,
        "Cannot specify both backupId and from/to parameters. Choose one approach.");

    final var databaseType = request.databaseType().toLowerCase();
    switch (databaseType) {
      case "rdbms", "none" -> {
        Preconditions.test(
            hasTimeRange && !request.continuousBackups(),
            "Time range restore (from/to) is only supported for continuous backups.");

        Preconditions.test(
            instantFrom != null && instantTo != null && instantFrom.isAfter(instantTo),
            "Invalid time range: from (%s) must be before to (%s)"
                .formatted(instantFrom, instantTo));
      }
      case "elasticsearch", "opensearch" -> {
        Preconditions.test(
            hasTimeRange,
            "Time range restore (from/to) is not supported for %s.".formatted(databaseType));
        Preconditions.test(!hasBackupIds, "No backupId specified");
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
}
