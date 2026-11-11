/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.TransactionListener;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Keeps rows of a partition across transactions, for the few rows that most transactions read and
 * then update, like the metric counters: a transaction reads such a row only if no earlier one did.
 *
 * <p>The cache relies on the partition being the only writer of these rows, and on its store
 * telling it every write. Like the process definition cache, it follows the transactions of the
 * store: it keeps the rows the current transaction wrote apart from the committed ones, adds them
 * to the committed ones when the transaction commits and drops them when it is rolled back. A
 * deleted row is dropped from the cache, so deleted rows don't pile up; the next read loads it.
 *
 * @param <K> the key of a row
 * @param <R> the row
 */
final class CommittedRowCache<K, R> implements TransactionListener {

  private final Map<K, Optional<R>> committed = new HashMap<>();
  private final Map<K, Optional<R>> written = new HashMap<>();

  /**
   * @param transactionContext the context of the transactions the store writes in
   */
  CommittedRowCache(final JdbcTransactionContext transactionContext) {
    transactionContext.addTransactionListener(this);
  }

  /**
   * Returns the current row of the key: as the transaction wrote it, or else the committed one,
   * loaded on a miss.
   *
   * @param load reads the row of the key from the database; null if there is none
   */
  @Nullable R get(final K key, final Function<K, @Nullable R> load) {
    final var writtenRow = written.get(key);
    if (writtenRow != null) {
      return writtenRow.orElse(null);
    }
    return committed.computeIfAbsent(key, k -> Optional.ofNullable(load.apply(k))).orElse(null);
  }

  /** Remembers that the transaction inserted or updated the row of the key. */
  void put(final K key, final R row) {
    written.put(key, Optional.of(row));
  }

  /** Remembers that the transaction deleted the row of the key. */
  void remove(final K key) {
    written.put(key, Optional.empty());
  }

  @Override
  public void onCommit() {
    written.forEach(
        (key, row) -> {
          if (row.isPresent()) {
            committed.put(key, row);
          } else {
            committed.remove(key);
          }
        });
    written.clear();
  }

  @Override
  public void onRollback() {
    written.clear();
  }
}
