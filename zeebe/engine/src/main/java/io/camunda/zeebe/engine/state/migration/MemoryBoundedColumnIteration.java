/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import java.util.function.BiConsumer;
import org.agrona.collections.MutableLong;

public final class MemoryBoundedColumnIteration {

  private static final long DEFAULT_MEMORY_LIMIT = 50 * 1024 * 1024L;
  private final long memoryLimitBytes;

  public MemoryBoundedColumnIteration() {
    this(DEFAULT_MEMORY_LIMIT);
  }

  public MemoryBoundedColumnIteration(final long memoryLimitBytes) {
    this.memoryLimitBytes = memoryLimitBytes;
  }

  /**
   * Drains iterates through all key value pairs of a column family and feeds them a consumer. Once
   * the consumer returns, it will remove the key from the column.
   *
   * <p>This iteration is done in chunks to minimize memory usage of the transaction. The usage is
   * estimated based on the key and value length, which is only roughly accurate; do improve this in
   * the future when there's a need (e.g. fanning out a key-value pair to multiple columns, for
   * example).
   *
   * @param columnFamily the column family to drain
   * @param consumer the consumer which handles the drained pairs
   * @param <KeyT> the type of the key
   * @param <ValueT> the type of the value
   */
  public <KeyT extends DbKey, ValueT extends DbValue> void drain(
      final ColumnFamily<KeyT, ValueT> columnFamily, final BiConsumer<KeyT, ValueT> consumer) {
    while (!columnFamily.isEmpty()) {
      final var memoryUsage = new MutableLong();
      columnFamily.whileTrue(
          (key, value) -> {
            consumer.accept(key, value);
            columnFamily.deleteExisting(key);
            return memoryUsage.addAndGet(key.getLength() + value.getLength()) < memoryLimitBytes;
          });
    }
  }
}
