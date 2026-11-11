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
 * bound memory; {@code batchSize} only shapes the writer's groups.
 *
 * @param cacheMaxBytes the size of the read cache, EhCache's heap tier
 * @param maxPendingBytes the key and value bytes of queued transactions above which commits wait
 * @param batchSize the most distinct keys the writer applies in one storage transaction; a larger
 *     transaction is written alone
 * @param maxWriteDelay how long the oldest queued transaction waits for its group to fill
 * @param snapshotCatchUpTimeout how long a snapshot waits for the writer to catch up
 */
public record WriteBehindConfiguration(
    long cacheMaxBytes,
    long maxPendingBytes,
    int batchSize,
    Duration maxWriteDelay,
    Duration snapshotCatchUpTimeout) {

  private static final long MEGABYTE = 1024 * 1024;

  public static final WriteBehindConfiguration DEFAULT =
      new WriteBehindConfiguration(
          64 * MEGABYTE, 32 * MEGABYTE, 1_000, Duration.ofMillis(100), Duration.ofSeconds(30));
}
