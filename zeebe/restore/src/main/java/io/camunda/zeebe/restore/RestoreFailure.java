/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

/**
 * A restore failure that names its reason, so a caller can tell failures apart without matching
 * their messages. The standalone restore app turns the reason into its exit code.
 *
 * <p>Each implementation extends the exception type the same failure was raised as before it had a
 * reason, so callers that catch or assert on those types keep working.
 */
public interface RestoreFailure {

  Reason reason();

  enum Reason {
    /** The request or the configuration is invalid; correcting it resolves the failure. */
    INVALID_REQUEST,
    /**
     * The backups do not cover the requested restore point; an earlier point, or another time
     * range, may still be restored.
     */
    RESTORE_POINT_UNAVAILABLE,
    /**
     * The backups are missing or inconsistent, so no restore point helps until the backup store is
     * repaired or other backups are used.
     */
    BACKUPS_INCONSISTENT,
    /** The backup store could not be reached; retrying may succeed. */
    BACKUP_STORE_UNAVAILABLE
  }

  final class InvalidArgument extends IllegalArgumentException implements RestoreFailure {

    public InvalidArgument(final String message) {
      super(message);
    }

    @Override
    public Reason reason() {
      return Reason.INVALID_REQUEST;
    }
  }

  /** What the backup store failed with, wrapped to name its reason. */
  final class BackupStoreFailure extends RuntimeException implements RestoreFailure {
    private final Reason reason;

    public BackupStoreFailure(final Reason reason, final Throwable cause) {
      super(cause);
      this.reason = reason;
    }

    @Override
    public Reason reason() {
      return reason;
    }

    /** Keeps the store failure's own description in logs. */
    @Override
    public String toString() {
      return String.valueOf(getCause());
    }
  }
}
