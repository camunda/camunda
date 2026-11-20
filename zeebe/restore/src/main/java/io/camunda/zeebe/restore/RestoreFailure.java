/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A restore failure that names its reason, so a caller can tell failures apart without matching
 * their messages. The standalone restore app turns the reason into its exit code.
 *
 * <p>Each implementation extends the exception type the same failure was raised as before it had a
 * reason, so callers that catch or assert on those types keep working.
 */
@NullMarked
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

  final class InvalidState extends IllegalStateException implements RestoreFailure {
    private final Reason reason;

    public InvalidState(final Reason reason, final String message) {
      super(message);
      this.reason = reason;
    }

    @Override
    public Reason reason() {
      return reason;
    }
  }

  /** What the backup store failed with, wrapped to name its reason. */
  final class BackupStoreFailure extends RuntimeException implements RestoreFailure {
    private final Reason reason;

    public BackupStoreFailure(final Reason reason, final Throwable cause) {
      super(cause);
      this.reason = reason;
    }

    /**
     * What a call to the backup store failed with: a restore failure raised while handling the
     * result is passed on as it is, anything else is the store's own failure and wrapped to name
     * its reason.
     */
    public static RuntimeException of(final CompletionException failure) {
      @Nullable Throwable cause = failure.getCause();
      while (cause instanceof CompletionException && cause.getCause() != null) {
        cause = cause.getCause();
      }
      if (cause == null || cause instanceof RestoreFailure) {
        return failure;
      }
      return new BackupStoreFailure(storeFailureReason(cause), cause);
    }

    /**
     * Why the store failed. Each store wraps what went wrong in an exception of its own, so only
     * the parser's exception in the cause chain tells content it could not parse, such as a corrupt
     * backup manifest, apart from the store being unreachable, where retrying may help.
     */
    private static Reason storeFailureReason(final Throwable failure) {
      final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      for (@Nullable Throwable current = failure;
          current != null && seen.add(current);
          current = current.getCause()) {
        if (current instanceof JsonProcessingException) {
          return Reason.BACKUPS_INCONSISTENT;
        }
      }
      return Reason.BACKUP_STORE_UNAVAILABLE;
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
