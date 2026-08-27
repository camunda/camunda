/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

import io.camunda.search.exception.CamundaSearchException;
import java.sql.SQLException;
import org.jspecify.annotations.Nullable;

/**
 * Translates vendor-specific database exceptions into {@link CamundaSearchException} instances with
 * appropriate reasons to allow callers to return meaningful error responses.
 *
 * <p>Currently handles:
 *
 * <ul>
 *   <li>ORA-01795: maximum number of expressions in a list is 1000
 *   <li>SQLState class 08 (connection exception), e.g. connection refused, dropped, or timed out
 * </ul>
 */
public final class DatabaseExceptionTranslator {

  /** Oracle error code for "maximum number of expressions in a list is 1000". */
  static final int ORA_01795_IN_LIST_TOO_LARGE = 1795;

  /** SQLState class for "Connection Exception", shared across JDBC drivers. */
  private static final String CONNECTION_EXCEPTION_SQL_STATE_CLASS = "08";

  private DatabaseExceptionTranslator() {}

  /**
   * Inspects the exception chain for known database errors. If one is found, returns a {@link
   * CamundaSearchException} with the appropriate reason; otherwise returns the original exception
   * unchanged.
   *
   * @param e the exception to inspect
   * @return a {@link CamundaSearchException} if a known database error is detected, or {@code e}
   *     itself
   */
  public static RuntimeException translateIfNeeded(final RuntimeException e) {
    if (isOracleInListSizeExceeded(e)) {
      return new CamundaSearchException(
          "The IN clause filter exceeded the maximum of 1000 elements allowed by Oracle. "
              + "Please reduce the number of values in the filter.",
          e,
          CamundaSearchException.Reason.INVALID_ARGUMENT);
    }
    final var connectionFailure = findConnectionException(e);
    if (connectionFailure != null) {
      return new CamundaSearchException(
          "A database connection failure occurred: " + connectionFailure.getMessage(),
          e,
          CamundaSearchException.Reason.CONNECTION_FAILED);
    }
    return e;
  }

  /**
   * Walks the exception chain to check for an Oracle ORA-01795 SQL error.
   *
   * @param e the root exception
   * @return {@code true} if ORA-01795 is found anywhere in the cause chain
   */
  private static boolean isOracleInListSizeExceeded(final Throwable e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof final SQLException sqlException
          && sqlException.getErrorCode() == ORA_01795_IN_LIST_TOO_LARGE) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  /**
   * Walks the exception chain for a {@link SQLException} whose SQLState is in the standard
   * "Connection Exception" class (08), e.g. a dropped connection or a connection-pool timeout.
   *
   * @param e the root exception
   * @return the connection-exception {@link SQLException} found in the cause chain, or {@code null}
   *     if none is present
   */
  private static @Nullable SQLException findConnectionException(final Throwable e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof final SQLException sqlException
          && sqlException.getSQLState() != null
          && sqlException.getSQLState().startsWith(CONNECTION_EXCEPTION_SQL_STATE_CLASS)) {
        return sqlException;
      }
      cause = cause.getCause();
    }
    return null;
  }
}
