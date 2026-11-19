/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;

/**
 * Same semantics as the RocksDB transaction context, over a {@link WriteBehindTransaction}.
 *
 * <p>Besides {@link #runInTransaction}, the column families use {@link #begin}, {@link
 * #commitIfBegun} and {@link #endIfBegun} with the same semantics, so that an operation needs no
 * lambda on the hot path:
 *
 * <pre>{@code
 * final boolean begun = context.begin();
 * try {
 *   // use context.currentTransaction()
 *   context.commitIfBegun(begun);
 * } finally {
 *   context.endIfBegun(begun);
 * }
 * }</pre>
 */
final class WriteBehindTransactionContext implements TransactionContext {

  private final WriteBehindTransaction transaction;

  WriteBehindTransactionContext(final WriteBehindTransaction transaction) {
    this.transaction = transaction;
  }

  @Override
  public void runInTransaction(final TransactionOperation operations) {
    final boolean begun = begin();
    try {
      operations.run();
      commitIfBegun(begun);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException(
          "Unexpected error occurred during zeebe db transaction operation.", e);
    } finally {
      endIfBegun(begun);
    }
  }

  @Override
  public ZeebeDbTransaction getCurrentTransaction() {
    return currentTransaction();
  }

  /** The transaction of this context, started if none is open. */
  WriteBehindTransaction currentTransaction() {
    if (!transaction.isInCurrentTransaction()) {
      transaction.resetTransaction();
    }
    return transaction;
  }

  /** Starts a transaction unless one is open. Returns whether it started one. */
  boolean begin() {
    if (transaction.isInCurrentTransaction()) {
      return false;
    }
    transaction.resetTransaction();
    return true;
  }

  /** Commits the transaction if {@link #begin} started it. */
  void commitIfBegun(final boolean begun) {
    if (begun) {
      transaction.commitInternal();
    }
  }

  /**
   * Rolls back the transaction if {@link #begin} started it: this only resets it after a commit,
   * and drops its writes after a failure. Call it in a finally block.
   */
  void endIfBegun(final boolean begun) {
    if (begun) {
      transaction.rollbackInternal();
    }
  }
}
