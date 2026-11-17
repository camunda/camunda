/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import java.util.NoSuchElementException;
import org.jspecify.annotations.NullMarked;

/**
 * A restore failure that names its reason, so a caller can tell failures apart without matching
 * their messages. The standalone restore app turns the reason into its exit code.
 *
 * <p>Each implementation extends the exception type the same failure was raised as before it had a
 * reason: the cluster restore API maps validation failures to its responses by that type, and must
 * keep answering as it did.
 */
@NullMarked
public sealed interface RestoreFailure {

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

  final class InvalidState extends IllegalStateException implements RestoreFailure {
    private final Reason reason;

    public InvalidState(final Reason reason, final String message) {
      super(message);
      this.reason = reason;
    }

    public InvalidState(final Reason reason, final String message, final Throwable cause) {
      super(message, cause);
      this.reason = reason;
    }

    @Override
    public Reason reason() {
      return reason;
    }
  }

  final class BackupNotFound extends NoSuchElementException implements RestoreFailure {

    public BackupNotFound(final String message) {
      super(message);
    }

    @Override
    public Reason reason() {
      return Reason.BACKUPS_INCONSISTENT;
    }
  }

  /**
   * A failure of the backup store itself, wrapping what the store failed with. It extends none of
   * the types the cluster restore API maps specifically, as the store's own exceptions do not, so
   * the API still answers with an internal error.
   */
  final class BackupStoreUnavailable extends RuntimeException implements RestoreFailure {

    public BackupStoreUnavailable(final Throwable cause) {
      super(cause);
    }

    @Override
    public Reason reason() {
      return Reason.BACKUP_STORE_UNAVAILABLE;
    }

    /**
     * The cluster restore API reports an internal error by this text, which before this wrapper
     * existed was the store failure's own.
     */
    @Override
    public String toString() {
      return String.valueOf(getCause());
    }
  }
}
