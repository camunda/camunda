/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

/**
 * Thrown when an {@code OptimisticTransactionDB} commit is rejected because of a write-write
 * conflict ({@code Busy}) or because the memtable history is too small to verify the absence of
 * conflicts ({@code TryAgain}). Retrying the same transaction will not resolve the error; the
 * correct recovery is a Raft leader step-down so the new leader can replay from the last committed
 * position.
 */
public final class TransactionConflictException extends RuntimeException {
  public TransactionConflictException(final Throwable cause) {
    super(
        "RocksDB transaction commit failed due to transaction conflict; see cause for more", cause);
  }
}
