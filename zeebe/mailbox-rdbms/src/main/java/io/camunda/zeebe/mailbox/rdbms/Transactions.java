/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import java.sql.SQLException;
import java.util.function.Function;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Runs each store operation as one transaction in a session of its own, as the stores promise that
 * every operation is atomic. A session is never kept, since its connection goes back to the pool.
 */
final class Transactions {

  /** SQLSTATE class for integrity constraint violations, e.g. a duplicate key. */
  private static final String INTEGRITY_CONSTRAINT_VIOLATION = "23";

  private final SqlSessionFactory sessionFactory;

  Transactions(final SqlSessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  /**
   * Runs the work and commits. Rolls back if the work throws.
   *
   * @param operation names the operation in the exception if it fails
   * @throws MailboxStoreException if the database fails
   */
  <T> T run(final String operation, final Function<SqlSession, T> work) {
    try (final var session = sessionFactory.openSession(false)) {
      try {
        final var result = work.apply(session);
        session.commit(true);
        return result;
      } catch (final RuntimeException e) {
        session.rollback(true);
        throw e;
      }
    } catch (final PersistenceException e) {
      throw new MailboxStoreException("Failed to " + operation, e);
    }
  }

  /**
   * Whether the insert failed because the key exists already. The caller must roll back, as some
   * databases, like PostgreSQL, refuse further statements in the transaction.
   */
  static boolean isDuplicateKey(final Throwable error) {
    for (var cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof final SQLException sqlException) {
        final var sqlState = sqlException.getSQLState();
        if (sqlState != null && sqlState.startsWith(INTEGRITY_CONSTRAINT_VIOLATION)) {
          return true;
        }
      }
    }
    return false;
  }
}
