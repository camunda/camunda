/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.boot.ExitCodeExceptionMapper;
import org.springframework.stereotype.Component;

/**
 * The exit code of a failed restore, by the {@link RestoreFailure.Reason} it failed for, so a
 * caller that automates a restore can react without parsing the log. The codes are a documented
 * contract and must not change:
 *
 * <ul>
 *   <li>{@code 1} — any other failure
 *   <li>{@code 10} — the request or the configuration is invalid
 *   <li>{@code 11} — the backups do not cover the requested restore point
 *   <li>{@code 12} — the backup store is unavailable; retrying may succeed
 *   <li>{@code 13} — the backups are missing or inconsistent
 * </ul>
 *
 * <p>Every code but {@code 1} comes from checks on the request and the backups. A restore that
 * fails one of them clears the data directory, so it can run again on the same volume.
 *
 * <p>The classified codes start at {@code 10} to stay clear of codes the JVM exits with on its own:
 * {@code -XX:+ExitOnOutOfMemoryError}, which {@code bin/restore} sets, exits with {@code 3}, and an
 * out-of-memory error can strike after the restore has written to the data directory.
 */
@Component
public final class RestoreExitCodes implements ExitCodeExceptionMapper {

  static final int UNEXPECTED_FAILURE = 1;
  static final int INVALID_REQUEST = 10;
  static final int RESTORE_POINT_UNAVAILABLE = 11;
  static final int BACKUP_STORE_UNAVAILABLE = 12;
  static final int BACKUPS_INCONSISTENT = 13;

  @Override
  public int getExitCode(final Throwable exception) {
    final var failure = findRestoreFailure(exception);
    if (failure == null) {
      return UNEXPECTED_FAILURE;
    }
    return switch (failure.reason()) {
      case INVALID_REQUEST -> INVALID_REQUEST;
      case RESTORE_POINT_UNAVAILABLE -> RESTORE_POINT_UNAVAILABLE;
      case BACKUP_STORE_UNAVAILABLE -> BACKUP_STORE_UNAVAILABLE;
      case BACKUPS_INCONSISTENT -> BACKUPS_INCONSISTENT;
    };
  }

  /** The outermost restore failure in the cause chain, e.g. behind a failed restore task. */
  private static RestoreFailure findRestoreFailure(final Throwable exception) {
    final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable current = exception;
        current != null && seen.add(current);
        current = current.getCause()) {
      if (current instanceof final RestoreFailure failure) {
        return failure;
      }
    }
    return null;
  }
}
