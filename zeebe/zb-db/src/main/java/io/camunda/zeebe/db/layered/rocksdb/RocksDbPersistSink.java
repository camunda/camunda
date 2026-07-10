/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.rocksdb;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.layered.PersistBatch;
import io.camunda.zeebe.db.layered.PersistSink;
import java.nio.ByteBuffer;
import java.util.Map;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

/**
 * The {@link PersistSink} over one database: batches are RocksDB {@code WriteBatch}es committed in
 * a single atomic write, and the recovery anchor lives under a single reserved key in the dedicated
 * anchor column family.
 */
final class RocksDbPersistSink implements PersistSink {

  /**
   * The one reserved key in the anchor column family. A single zero byte — the column family holds
   * nothing else, so no collision is possible.
   */
  static final byte[] ANCHOR_KEY = {0x00};

  private final RocksDB db;
  private final WriteOptions writeOptions;
  private final Map<String, ColumnFamilyHandle> storeHandles;
  private final ColumnFamilyHandle anchorHandle;

  RocksDbPersistSink(
      final RocksDB db,
      final WriteOptions writeOptions,
      final Map<String, ColumnFamilyHandle> storeHandles,
      final ColumnFamilyHandle anchorHandle) {
    this.db = db;
    this.writeOptions = writeOptions;
    this.storeHandles = storeHandles;
    this.anchorHandle = anchorHandle;
  }

  @Override
  public PersistBatch newBatch() {
    return new RocksDbPersistBatch(db, writeOptions, storeHandles, anchorHandle);
  }

  @Override
  public long readAnchor() {
    final byte[] encoded;
    try {
      encoded = db.get(anchorHandle, ANCHOR_KEY);
    } catch (final RocksDBException e) {
      throw new ZeebeDbException("Failed to read the recovery anchor", e);
    }
    return encoded == null ? -1 : decodePosition(encoded);
  }

  /** Positions are stored as 8-byte big-endian, matching unsigned key order for debuggability. */
  static byte[] encodePosition(final long position) {
    return ByteBuffer.allocate(Long.BYTES).putLong(position).array();
  }

  private static long decodePosition(final byte[] encoded) {
    return ByteBuffer.wrap(encoded).getLong();
  }
}
