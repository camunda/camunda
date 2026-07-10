/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import java.util.function.BiConsumer;

/**
 * The durable delegate under one {@link LayeredKeyValueStore} — typically one RocksDB column
 * family. Keys and values are opaque serialized bytes; scans iterate in unsigned-byte key order,
 * matching RocksDB's default comparator.
 *
 * <p>Reads on this interface see only committed (persisted) data — none of the layered store's
 * in-memory buffering. Writes on this interface are used exclusively by the persist path (see
 * {@link PersistBatch} for the atomic variant used by persist rounds); the processing write path
 * never writes here directly.
 *
 * <p><b>Threading:</b> implementations must tolerate reads from the owner thread concurrently with
 * writes from the persist IO thread (RocksDB provides this natively).
 */
public interface BytesStore {

  /** The committed value for {@code key}, or {@code null} if absent. */
  byte[] get(byte[] key);

  void put(byte[] key, byte[] value);

  void delete(byte[] key);

  /**
   * Visits every committed entry whose key starts with {@code prefix}, in unsigned-byte key order.
   * An empty prefix visits everything.
   */
  void prefixScan(byte[] prefix, BiConsumer<byte[], byte[]> visitor);
}
