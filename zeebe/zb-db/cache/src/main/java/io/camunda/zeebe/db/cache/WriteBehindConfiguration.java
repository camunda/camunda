/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import java.time.Duration;

/**
 * Settings of one write-behind cache. All sizes are per cache, so per partition. The byte limits
 * bound memory; the entry counts only shape batching.
 *
 * @param cacheMaxBytes the size of EhCache's heap tier
 * @param maxPendingBytes the key and value bytes of unflushed versions above which commits wait
 * @param batchSize the most writes the writer applies in one backend transaction
 * @param maxWriteDelay how long the writer waits for a batch to fill before applying it
 * @param queueSize the pending writes per queue before commits block
 * @param concurrencyLevel the number of writer queues and threads
 * @param coalescing whether a batch keeps only the latest version of a key
 * @param writeThroughMaxKeys transactions with more keys bypass the cache
 * @param writeThroughMaxBytes transactions with more key and value bytes bypass the cache
 * @param drainTimeout how long a snapshot or a write-through waits for the writer to drain
 */
public record WriteBehindConfiguration(
    long cacheMaxBytes,
    long maxPendingBytes,
    int batchSize,
    Duration maxWriteDelay,
    int queueSize,
    int concurrencyLevel,
    boolean coalescing,
    int writeThroughMaxKeys,
    long writeThroughMaxBytes,
    Duration drainTimeout) {

  private static final long MEGABYTE = 1024 * 1024;

  public static final WriteBehindConfiguration DEFAULT =
      new WriteBehindConfiguration(
          64 * MEGABYTE,
          32 * MEGABYTE,
          1_000,
          Duration.ofMillis(100),
          10_000,
          1,
          true,
          10_000,
          16 * MEGABYTE,
          Duration.ofSeconds(30));
}
