/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.cache;

import java.util.function.Supplier;

/** Metrics interface to observer the {@link FlowNodeInstanceTreePathCache} access and results. */
public interface TreePathCacheMetrics {

  /**
   * Report the cache access and the corresponding result, whether it was a {@link CacheResult#HIT}
   * or {@link CacheResult#MISS}.
   *
   * @param partitionId the corresponding partition the cache belongs to
   * @param result the cache access result
   */
  void reportCacheResult(int partitionId, CacheResult result);

  /**
   * Record the cache resolution, implementations can track the time that is elapsed during the
   * call.
   *
   * @param resolving the method to resolve the searched value either by look up in the cache or
   *     using any other resolution strategy
   * @return the result of the resolution
   */
  default String recordTimeOfTreePathResolvement(
      final int partitionId, final Supplier<String> resolving) {
    return resolving.get();
  }

  /**
   * Report the cache size for a specific partition, to indicate how many key-value pairs are
   * currently stored.
   *
   * @param partitionId the partition to which the cache size corresponds
   * @param size the reported cache size
   */
  void reportCacheSize(int partitionId, int size);

  enum CacheResult {
    /** Entry was found in the cache */
    HIT,
    /** Entry was not found in the cache */
    MISS
  }
}
