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
 * A pinned, point-in-time read view over the durable store, spanning all named stores (column
 * families) of one database. Writes committed after the snapshot was taken are invisible through it
 * — this is what shields an asynchronous reader from persist bursts running concurrently (the
 * phantom/ghost tear described in {@link ReadOnlyView}).
 *
 * <p>Snapshots are cheap (a pinned sequence number) but pin old versions against compaction while
 * held: hold exactly one per reader and {@link #close()} it when the next one arrives.
 *
 * <p><b>Threading:</b> safe to read from any thread. {@link #close()} must be called exactly once,
 * by the owner of the rotation (see {@link LayeredStoreCoordinator}).
 */
public interface ReadSnapshot extends AutoCloseable {

  /** The value for {@code key} in store {@code storeName} at the pinned point, or null. */
  byte[] get(String storeName, byte[] key);

  /**
   * Visits every entry of store {@code storeName} at the pinned point whose key starts with {@code
   * prefix}, in unsigned-byte key order.
   */
  void prefixScan(String storeName, byte[] prefix, BiConsumer<byte[], byte[]> visitor);

  /** Releases the pin. Idempotent implementations are encouraged but not required. */
  @Override
  void close();
}
