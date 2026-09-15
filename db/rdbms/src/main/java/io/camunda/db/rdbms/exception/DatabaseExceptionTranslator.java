/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.zeebe.util.exception.RecoverableException;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

/**
 * Translates known database exceptions into domain-specific exceptions with appropriate semantics.
 *
 * <p>Currently handles:
 *
 * <ul>
 *   <li>ORA-01795: maximum number of expressions in a list is 1000
 *   <li>JDBC connection failures as recoverable exceptions
 * </ul>
 */
public final class DatabaseExceptionTranslator {

  /** Oracle error code for "maximum number of expressions in a list is 1000". */
  static final int ORA_01795_IN_LIST_TOO_LARGE = 1795;

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
    return e;
  }

  /**
   * Translates a database connection failure into a recoverable exception. Other exceptions,
   * including explicitly classified failures, are returned unchanged.
   *
   * @param e the exception to inspect
   * @param operation the operation that failed
   * @return a recoverable exception for a connection failure, or {@code e} unchanged
   */
  public static RuntimeException translateConnectionFailureIfNeeded(
      final RuntimeException e, final String operation) {
    if (e instanceof RecoverableException || e instanceof UnrecoverableException) {
      return e;
    }

    if (isConnectionFailure(e)) {
      return new RecoverableException(operation, e);
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

  private static boolean isConnectionFailure(final Throwable e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof SQLTransientConnectionException
          || cause instanceof SQLRecoverableException) {
        return true;
      }

      if (cause instanceof final SQLException sqlException
          && hasConnectionExceptionSqlState(sqlException.getSQLState())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private static boolean hasConnectionExceptionSqlState(final String sqlState) {
    return sqlState != null && sqlState.startsWith("08");
  }
}
