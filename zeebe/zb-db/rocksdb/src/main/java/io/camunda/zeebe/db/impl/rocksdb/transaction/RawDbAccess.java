/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.ZeebeDbException;
import java.io.File;
import org.jspecify.annotations.Nullable;

/**
 * Direct access to the committed state of the RocksDB instance, bypassing transactions. It exposes
 * no RocksDB types, so that code outside this module can build on it. Keys are raw, already
 * prefixed with the column family.
 *
 * <p>A recoverable RocksDB error surfaces as a {@link ZeebeDbException}; any other error surfaces
 * as a {@link RuntimeException}.
 */
public interface RawDbAccess {

  /** Reads the committed value, or returns null if there is none. */
  byte @Nullable [] get(byte[] key, int keyLength);

  /**
   * Returns false only if the key is certainly not in the database; true means that it may be.
   * Cheaper than {@link #get} because it can answer from filters and memtables.
   */
  boolean keyMayExist(byte[] key, int keyLength);

  /** Iterates over the committed state only, restricted to a prefix by the caller. */
  DbIterator newIterator();

  /** Starts a batch that {@link RawBatch#write()} applies atomically. */
  RawBatch newBatch();

  /** Takes a RocksDB checkpoint of the live database into {@code directory}. */
  void createCheckpoint(File directory);

  interface RawBatch extends AutoCloseable {

    void put(byte[] key, byte[] value);

    void delete(byte[] key);

    /** Applies all puts and deletes atomically. */
    void write();

    @Override
    void close();
  }
}
