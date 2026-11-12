/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import java.util.List;
import java.util.Map;

/**
 * A committed transaction waiting for the writer.
 *
 * @param seq the transaction's commit sequence, which all its versions share
 * @param position the log's last written position when it committed; it's written to the storage
 *     once the log has committed this position
 * @param versions the transaction's versions, which share their arrays with the DirtyIndex
 * @param bytes the key and value bytes of {@code versions}
 * @param enqueuedNanos when it was queued, by {@link System#nanoTime()}
 */
record CommittedTransaction(
    long seq,
    long position,
    List<Map.Entry<CacheKey, CachedValue>> versions,
    long bytes,
    long enqueuedNanos) {

  static CommittedTransaction of(
      final long seq, final long position, final List<Map.Entry<CacheKey, CachedValue>> versions) {
    long bytes = 0;
    for (final var version : versions) {
      final var value = version.getValue().bytes();
      bytes += version.getKey().key().length + (value == null ? 0 : value.length);
    }
    return new CommittedTransaction(seq, position, versions, bytes, System.nanoTime());
  }
}
