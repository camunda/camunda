/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;

/**
 * Checks whether any Optimize entity document references the given user ID, and rewrites all
 * occurrences from oldUserId to newUserId across every entity-owning index.
 */
public interface UserIdMigrationRepository {

  /**
   * Returns true if at least one document in any entity index still references the given user ID
   * (as owner, lastModifier, or collection role member). Used to skip the migration when it has
   * already been applied.
   */
  boolean hasDocumentsWithUserId(String userId);

  /**
   * Rewrites every occurrence of oldUserId to newUserId across all entity-owning indices:
   * collection role membership, owner, and lastModifier fields.
   */
  void migrateUserId(String oldUserId, String newUserId);

  /**
   * Wraps a failed migration in an exception that names only masked IDs. The original failure can
   * carry the query, which holds the full user IDs, so the cause is the {@link #sanitize sanitized}
   * copy of it.
   */
  static OptimizeRuntimeException maskedFailure(
      final String oldUserId, final String newUserId, final Throwable failure) {
    return new OptimizeRuntimeException(
        String.format(
            "Failed to migrate user ID [%s] to [%s]", maskForLog(oldUserId), maskForLog(newUserId)),
        sanitize(failure, oldUserId, newUserId));
  }

  /**
   * Copies a failure and its causes with every given user ID masked in the messages. The copies
   * keep the original class name in the message and the original stack traces, so triage does not
   * need DEBUG logs.
   */
  static Throwable sanitize(final Throwable failure, final String... userIds) {
    return sanitize(failure, userIds, 0);
  }

  private static Throwable sanitize(
      final Throwable failure, final String[] userIds, final int depth) {
    if (failure == null || depth > 10) {
      return null;
    }
    final String message = failure.getMessage();
    final OptimizeRuntimeException copy =
        new OptimizeRuntimeException(
            failure.getClass().getName() + (message == null ? "" : ": " + mask(message, userIds)),
            sanitize(
                failure.getCause() == failure ? null : failure.getCause(), userIds, depth + 1));
    copy.setStackTrace(failure.getStackTrace());
    return copy;
  }

  private static String mask(final String message, final String... userIds) {
    String masked = message;
    for (final String userId : userIds) {
      if (userId != null && !userId.isEmpty()) {
        masked = masked.replace(userId, maskForLog(userId));
      }
    }
    return masked;
  }

  /**
   * Masks the local part of an email, so log lines and exception messages do not carry it. A user
   * ID can be an email address, for example after a username claim change.
   */
  static String maskForLog(final String userId) {
    if (userId == null) {
      return null;
    }
    final int at = userId.lastIndexOf('@');
    if (at < 0) {
      return userId;
    }
    final int localStart = userId.lastIndexOf('|', at) + 1;
    final String visible =
        at > localStart ? userId.substring(0, localStart + 1) : userId.substring(0, localStart);
    return visible + "***" + userId.substring(at);
  }
}
