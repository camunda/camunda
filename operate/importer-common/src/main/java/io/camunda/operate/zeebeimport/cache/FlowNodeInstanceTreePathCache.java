/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport.cache;

import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.SoftHashMap;
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
 * FlowNodeInstanceRecord} the treePath with the corresponding flowScopekey is stored in the cache
 * itself. Be aware that only X elements are to be guaranteed in the cache (corresponding to the
 * given cacheSize on construction).
 */
public final class FlowNodeInstanceTreePathCache {
  private static final Logger LOGGER = LoggerFactory.getLogger(FlowNodeInstanceTreePathCache.class);
  private final Map<Integer, Map<String, String>> backedTreePathCache;
  private final Function<Long, String> treePathResolver;

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
    backedTreePathCache = new HashMap<>();
    partitionIds.forEach(
        partitionId ->
            backedTreePathCache.computeIfAbsent(partitionId, (id) -> new SoftHashMap<>(cacheSize)));
    this.treePathResolver = treePathResolver;
  }

  /**
   * Resolve the treePath for the given flow node instance (FNI) record {@link
   * FlowNodeInstanceRecord}.
   *
   * <p>When the flow scope and process instance key are equal, the value is returned as treePath,
   * as it corresponds to a FNI on root level.
   *
   * <p>When the cache doesn't contain the treePath for a given flowScopeKey, the corresponding
   * treePathResolver is used (specified on cache construction).
   *
   * <p>Does the resolver has also no knowledge about the treePath the process instance key is used.
   *
   * @param flowNodeInstanceRecord record that contains information used to resolve the tree path
   *     for the flow node instance
   * @return the treePath of the flow node instance
   * @throws IllegalArgumentException when the flow node instance record doesn't correspond to a
   *     supported partition
   */
  public String resolveTreePath(final FlowNodeInstanceRecord flowNodeInstanceRecord) {
    final var partitionCache = backedTreePathCache.get(flowNodeInstanceRecord.partitionId());
    if (partitionCache == null) {
      final IllegalArgumentException illegalArgumentException =
          new IllegalArgumentException(
              String.format(
                  "Expected to find treePath cache for partitionId %d, but found nothing. Possible partition Ids are: '%s'.",
                  flowNodeInstanceRecord.partitionId(), backedTreePathCache.keySet()));

      LOGGER.error(
          "Couldn't resolve tree path for given partition id {}",
          flowNodeInstanceRecord.partitionId(),
          illegalArgumentException);
      throw illegalArgumentException;
    }

    return resolveTreePath(partitionCache, flowNodeInstanceRecord);
  }

  private String resolveTreePath(
      final Map<String, String> partitionCache,
      final FlowNodeInstanceRecord flowNodeInstanceRecord) {
    String parentTreePath;
    // if scopeKey differs from processInstanceKey, then it's inner tree level and we need to search
    // for parent 1st
    if (flowNodeInstanceRecord.flowScopeKey() == flowNodeInstanceRecord.processInstanceKey()) {
      parentTreePath = ConversionUtils.toStringOrNull(flowNodeInstanceRecord.processInstanceKey());
    } else {
      // find parent flow node instance
      parentTreePath =
          partitionCache.get(ConversionUtils.toStringOrNull(flowNodeInstanceRecord.flowScopeKey()));

      // cache miss: resolve tree path
      if (parentTreePath == null) {
        parentTreePath = treePathResolver.apply(flowNodeInstanceRecord.flowScopeKey());
        LOGGER.debug(
            "Cache miss: resolved treePath {} for flowScopeKey {} via given resolver.",
            parentTreePath,
            flowNodeInstanceRecord.flowScopeKey());
      }

      if (parentTreePath == null) {
        LOGGER.warn(
            "Unable to find parent tree path for flow node instance id [{}], parent flow node instance id [{}]",
            flowNodeInstanceRecord.recordKey(),
            flowNodeInstanceRecord.flowScopeKey());
        parentTreePath =
            ConversionUtils.toStringOrNull(flowNodeInstanceRecord.processInstanceKey());
      }
    }
    partitionCache.put(
        ConversionUtils.toStringOrNull(flowNodeInstanceRecord.recordKey()),
        String.join(
            "/",
            parentTreePath,
            ConversionUtils.toStringOrNull(flowNodeInstanceRecord.recordKey())));
    return parentTreePath;
  }
}
