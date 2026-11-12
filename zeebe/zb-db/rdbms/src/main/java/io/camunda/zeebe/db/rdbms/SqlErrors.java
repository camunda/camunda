/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import io.camunda.zeebe.db.ZeebeDbException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Set;
import org.apache.ibatis.exceptions.PersistenceException;
import org.jspecify.annotations.Nullable;

/**
 * Decides which database errors can pass if the transaction is tried again: lost connections,
 * timeouts, deadlocks and serialization failures. Those become a {@link ZeebeDbException}, which
 * the stream processor retries. Any other error is unexpected, as it is for RocksDB.
 */
final class SqlErrors {

  /**
   * SQL states that aren't always reported as transient: serialization failure, PostgreSQL's
   * deadlock, lock not available and statement cancelled (by a timeout).
   */
  private static final Set<String> RECOVERABLE_STATES = Set.of("40001", "40P01", "55P03", "57014");

  private static final String CONNECTION_ERROR_CLASS = "08";

  /** Enough to reach the driver's exception through MyBatis' and any pool's wrappers. */
  private static final int MAX_CAUSE_DEPTH = 16;

  private SqlErrors() {}

  /** Returns the exception to throw for {@code error}, a {@link PersistenceException} or SQL. */
  static RuntimeException translate(final Exception error) {
    if (isRecoverable(error)) {
      return new ZeebeDbException(
          "A database error occurred that may pass if the transaction is tried again", error);
    }
    return new RuntimeException("Unexpected error occurred during RDBMS transaction.", error);
  }

  static boolean isRecoverable(final Throwable error) {
    @Nullable Throwable cause = error;
    for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (cause instanceof final SQLException sqlException && isRecoverable(sqlException)) {
        return true;
      }
      cause = cause.getCause() == cause ? null : cause.getCause();
    }
    return false;
  }

  /** Also looks at the exceptions a batch chains after the first one. */
  private static boolean isRecoverable(final SQLException error) {
    @Nullable SQLException next = error;
    for (int depth = 0; next != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (next instanceof SQLTransientException || next instanceof SQLRecoverableException) {
        return true;
      }
      final var state = next.getSQLState();
      if (state != null
          && (state.startsWith(CONNECTION_ERROR_CLASS) || RECOVERABLE_STATES.contains(state))) {
        return true;
      }
      next = next.getNextException();
    }
    return false;
  }
}
