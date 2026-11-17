/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.backup;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Request validation shared by the per-physical-tenant and the cluster-wide history backup
 * services, so {@code /v2} and {@code /cluster/v2} reject the same input with the same message.
 *
 * <p>The OpenAPI {@code minimum: 1} and {@code ^\d*\*$} constraints are documentation only —
 * nothing enforces them at runtime, which is why these checks exist at all.
 */
@NullMarked
public final class HistoryBackupRequests {

  /** Matches the wildcard the runtime backup endpoints use for "every backup". */
  public static final String WILDCARD = "*";

  private HistoryBackupRequests() {}

  /**
   * @param backupId nullable because the request body may omit it; unlike runtime backups, history
   *     backups have no generated-id mode, so an absent id is a bad request rather than a signal
   * @throws ServiceException with {@link Status#INVALID_ARGUMENT} if the id is absent or not
   *     positive
   */
  public static long requireValidBackupId(final @Nullable Long backupId) {
    if (backupId == null || backupId <= 0) {
      throw new ServiceException(
          "A backupId must be provided and it must be > 0", Status.INVALID_ARGUMENT);
    }
    return backupId;
  }

  /**
   * @return the prefix to query the store with: the given one, or the wildcard when none was given
   * @throws ServiceException with {@link Status#INVALID_ARGUMENT} if the prefix does not end in the
   *     wildcard
   */
  public static String requireValidPrefix(final @Nullable String prefix) {
    if (prefix == null) {
      return WILDCARD;
    }
    if (!prefix.endsWith(WILDCARD)) {
      throw new ServiceException(
          "Expected a prefix ending with '*', but got '%s'".formatted(prefix),
          Status.INVALID_ARGUMENT);
    }
    return prefix;
  }
}
