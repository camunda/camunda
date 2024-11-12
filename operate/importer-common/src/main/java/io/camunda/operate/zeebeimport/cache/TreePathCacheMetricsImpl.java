/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.cache;

import io.camunda.operate.Metrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class TreePathCacheMetricsImpl implements TreePathCacheMetrics {

  private final Metrics metrics;
  private final Map<Integer, AtomicInteger> cacheSizes;

  public TreePathCacheMetricsImpl(final List<Integer> partitionIds, final Metrics metrics) {
    this.metrics = metrics;
    cacheSizes = new HashMap<>();
    partitionIds.forEach(
        partitionId -> {
          final AtomicInteger cacheSizeRecorder = new AtomicInteger(0);
          cacheSizes.put(partitionId, cacheSizeRecorder);
          // gauges are registered once
          metrics.registerGauge(
              Metrics.GAUGE_NAME_IMPORT_FNI_TREE_PATH_CACHE_SIZE,
              cacheSizeRecorder,
              Number::doubleValue,
              Metrics.TAG_KEY_PARTITION,
              Integer.toString(partitionId));
        });
  }

  @Override
  public void reportCacheResult(final int partitionId, final CacheResult result) {
    metrics.recordCounts(
        Metrics.COUNTER_NAME_IMPORT_FNI_TREE_PATH_CACHE_RESULT,
        1,
        Metrics.TAG_KEY_PARTITION,
        Integer.toString(partitionId),
        "cache.result",
        result.toString());
  }

  @Override
  public String recordTimeOfTreePathResolvement(
      final int partitionId, final Supplier<String> resolving) {
    return metrics
        .getHistogram(
            Metrics.TIMER_NAME_IMPORT_FNI_TREE_PATH_CACHE_ACCESS,
            Metrics.TAG_KEY_PARTITION,
            Integer.toString(partitionId))
        .record(resolving);
  }

  @Override
  public void reportCacheSize(final int partitionId, final int size) {
    cacheSizes.get(partitionId).set(size);
  }
}
