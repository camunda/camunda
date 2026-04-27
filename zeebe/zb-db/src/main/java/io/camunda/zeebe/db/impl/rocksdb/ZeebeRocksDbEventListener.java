/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import org.rocksdb.AbstractEventListener;
import org.rocksdb.FlushJobInfo;
import org.rocksdb.MemTableInfo;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;

/**
 * Logs RocksDB flush and memtable lifecycle events to help diagnose optimistic transaction
 * conflict-check failures. When a memtable is flushed, its sequence-number history is discarded
 * (if max_write_buffer_size_to_maintain is 0), which causes conflict checks to fail for any
 * transaction that started before the flushed range.
 */
public final class ZeebeRocksDbEventListener extends AbstractEventListener {

  private static final Logger LOG = Loggers.DB_LOGGER;

  public ZeebeRocksDbEventListener() {
    super(
        EnabledEventCallback.ON_FLUSH_BEGIN,
        EnabledEventCallback.ON_FLUSH_COMPLETED,
        EnabledEventCallback.ON_MEMTABLE_SEALED);
  }

  @Override
  public void onFlushBegin(final RocksDB db, final FlushJobInfo flushJobInfo) {
    LOG.debug(
        "RocksDB memtable flush started: columnFamily={}, reason={}",
        flushJobInfo.getColumnFamilyName(),
        flushJobInfo.getFlushReason());
  }

  @Override
  public void onFlushCompleted(final RocksDB db, final FlushJobInfo flushJobInfo) {
    LOG.info(
        "RocksDB memtable flush completed: columnFamily={}, reason={}, smallestSeqno={}, largestSeqno={}",
        flushJobInfo.getColumnFamilyName(),
        flushJobInfo.getFlushReason(),
        flushJobInfo.getSmallestSeqno(),
        flushJobInfo.getLargestSeqno());
  }

  /**
   * Called when a memtable becomes immutable (write buffer full). The sequence range logged here
   * ({@code firstSeqno} to the next flush's {@code largestSeqno}) is the range that will be lost
   * from in-memory conflict history once the flush completes.
   */
  @Override
  public void onMemTableSealed(final MemTableInfo memTableInfo) {
    LOG.debug(
        "RocksDB memtable sealed (now immutable): columnFamily={}, firstSeqno={}, earliestSeqno={}, numEntries={}",
        memTableInfo.getColumnFamilyName(),
        memTableInfo.getFirstSeqno(),
        memTableInfo.getEarliestSeqno(),
        memTableInfo.getNumEntries());
  }
}
