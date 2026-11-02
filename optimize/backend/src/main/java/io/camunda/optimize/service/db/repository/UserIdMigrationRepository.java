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
   * Replaces a failed migration with an exception that names only masked IDs. The original failure
   * carries the query, which holds the full user ID, so it is not kept as the cause.
   */
  static OptimizeRuntimeException maskedFailure(
      final String oldUserId, final String newUserId, final RuntimeException failure) {
    return new OptimizeRuntimeException(
        String.format(
            "Failed to migrate user ID [%s] to [%s]: %s",
            maskForLog(oldUserId), maskForLog(newUserId), failure.getClass().getSimpleName()));
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
