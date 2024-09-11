/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.cache;

import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.SoftHashMap;
import io.camunda.operate.zeebeimport.cache.TreePathCacheMetrics.CacheResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cache for the treePath of flow node instances.
 *
 * <p>The cache can handle multiple different partitions (need to be specified on construction). For
 * each partition a separate internal cache are used, where the given cacheSize is as upper bound to
 * store key-values.
 *
 * <p>On construction a treePath resolver can be specified that should resolve the treePath if it is
 * not part of the cache. As it can be that the value has been evicited due to reaching the cache
 * size or the flow scope haven't been seen before (due to recreation of cache, etc.).
 *
 * <p>When resolving the treePath for a given flow node instance record {@link
 * FNITreePathCacheCompositeKey} the treePath with the corresponding flowScopekey is stored in the
 * cache itself. Be aware that only X elements are to be guaranteed in the cache (corresponding to
 * the given cacheSize on construction).
 */
public final class FlowNodeInstanceTreePathCache implements TreePathCache {
  private static final Logger LOGGER = LoggerFactory.getLogger(FlowNodeInstanceTreePathCache.class);
  private final Map<Integer, Map<String, String>> backedTreePathCache;
  private final Function<Long, String> treePathResolver;
  private final TreePathCacheMetrics treePathCacheMetrics;

  /**
   * Constructs the tree patch cache, backed by caches per given partitions.
   *
   * @param partitionIds a list of partition ids the cache should cover, it might be that the
   *     corresponding Importer is only importing a sparse of existing partition ids
   * @param cacheSize the size of the caches assigned per partition
   * @param treePathResolver the resolver to find corresponding treePath if not existing in the
   *     cache
   */
  public FlowNodeInstanceTreePathCache(
      final List<Integer> partitionIds,
      final int cacheSize,
      final Function<Long, String> treePathResolver) {
    this(partitionIds, cacheSize, treePathResolver, new NoopCacheMetrics());
  }

  /**
   * Constructs the tree patch cache, backed by caches per given partitions.
   *
   * @param partitionIds a list of partition ids the cache should cover, it might be that the
   *     corresponding Importer is only importing a sparse of existing partition ids
   * @param cacheSize the size of the caches assigned per partition
   * @param treePathResolver the resolver to find corresponding treePath if not existing in the
   *     cache
   * @param treePathCacheMetrics metrics that are collected during cache usage
   */
  public FlowNodeInstanceTreePathCache(
      final List<Integer> partitionIds,
      final int cacheSize,
      final Function<Long, String> treePathResolver,
      final TreePathCacheMetrics treePathCacheMetrics) {
    backedTreePathCache = new HashMap<>();
    partitionIds.forEach(
        partitionId ->
            backedTreePathCache.computeIfAbsent(partitionId, (id) -> new SoftHashMap<>(cacheSize)));
    this.treePathResolver = treePathResolver;
    this.treePathCacheMetrics = treePathCacheMetrics;
  }

  /**
   * Resolve the treePath for the given flow node instance (FNI) record {@link
   * FNITreePathCacheCompositeKey}.
   *
   * <p>When the flow scope and process instance key are equal, the value is returned as treePath,
   * as it corresponds to a FNI on root level.
   *
   * <p>When the cache doesn't contain the treePath for a given flowScopeKey, the corresponding
   * treePathResolver is used (specified on cache construction).
   *
   * <p>Does the resolver has also no knowledge about the treePath the process instance key is used.
   *
   * @param compositeKey a composite key that contains information used to resolve the tree path for
   *     the flow node instance
   * @return the treePath of the flow node instance
   * @throws IllegalArgumentException when the flow node instance record doesn't correspond to a
   *     supported partition
   */
  @Override
  public String resolveParentTreePath(final FNITreePathCacheCompositeKey compositeKey) {
    final int partitionId = compositeKey.partitionId();
    final var partitionCache = backedTreePathCache.get(partitionId);
    if (partitionCache == null) {
      final IllegalArgumentException illegalArgumentException =
          new IllegalArgumentException(
              String.format(
                  "Expected to find treePath cache for partitionId %d, but found nothing. Possible partition Ids are: '%s'.",
                  partitionId, backedTreePathCache.keySet()));

      LOGGER.error(
          "Couldn't resolve tree path for given partition id {}",
          partitionId,
          illegalArgumentException);
      throw illegalArgumentException;
    }

    final var treePath =
        treePathCacheMetrics.recordTimeOfTreePathResolvement(
            partitionId, () -> resolveTreePath(partitionCache, compositeKey));
    treePathCacheMetrics.reportCacheSize(partitionId, partitionCache.size());
    return treePath;
  }

  @Override
  public void cacheTreePath(
      final FNITreePathCacheCompositeKey compositeKey, final String treePath) {
    final int partitionId = compositeKey.partitionId();
    final var partitionCache = backedTreePathCache.get(partitionId);
    if (partitionCache == null) {
      final IllegalArgumentException illegalArgumentException =
          new IllegalArgumentException(
              String.format(
                  "Expected to find treePath cache for partitionId %d, but found nothing. Possible partition Ids are: '%s'.",
                  partitionId, backedTreePathCache.keySet()));

      LOGGER.error(
          "Couldn't store tree path {} for given key {}",
          treePath,
          compositeKey,
          illegalArgumentException);
      throw illegalArgumentException;
    }

    partitionCache.put(ConversionUtils.toStringOrNull(compositeKey.recordKey()), treePath);
    treePathCacheMetrics.reportCacheSize(partitionId, partitionCache.size());
  }

  private String resolveTreePath(
      final Map<String, String> partitionCache, final FNITreePathCacheCompositeKey compositeKey) {
    String parentTreePath;
    // if scopeKey differs from processInstanceKey, then it's inner tree level and we need to search
    // for parent 1st
    if (compositeKey.flowScopeKey() == compositeKey.processInstanceKey()) {
      parentTreePath = ConversionUtils.toStringOrNull(compositeKey.processInstanceKey());
    } else {
      var cacheResult = CacheResult.HIT;
      // find parent flow node instance
      parentTreePath =
          partitionCache.get(ConversionUtils.toStringOrNull(compositeKey.flowScopeKey()));

      // cache miss: resolve tree path
      if (parentTreePath == null) {
        cacheResult = CacheResult.MISS;
        parentTreePath = treePathResolver.apply(compositeKey.flowScopeKey());
        LOGGER.debug(
            "Cache miss: resolved treePath {} for flowScopeKey {} via given resolver.",
            parentTreePath,
            compositeKey.flowScopeKey());

        // add missing treePath to cache
        if (parentTreePath != null) {
          partitionCache.put(
              ConversionUtils.toStringOrNull(compositeKey.flowScopeKey()), parentTreePath);
        } else {
          LOGGER.warn(
              "Unable to find parent tree path for flow node instance id [{}], parent flow node instance id [{}]",
              compositeKey.recordKey(),
              compositeKey.flowScopeKey());
          parentTreePath = ConversionUtils.toStringOrNull(compositeKey.processInstanceKey());
        }
      }
      treePathCacheMetrics.reportCacheResult(compositeKey.partitionId(), cacheResult);
    }
    return parentTreePath;
  }
}
