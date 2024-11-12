/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.cache;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import io.camunda.operate.zeebeimport.cache.TreePathCacheMetrics.CacheResult;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ObservableFlowNodeInstanceTreePathCacheTest {

  private HashMap<Long, String> spyTreePathResolver;
  private FlowNodeInstanceTreePathCache treePathCache;
  private NoopCacheMetrics treePathCacheMetrics;

  @BeforeEach
  void setup() {
    // treePathResolver is in this case a simple map, but in production it might be
    // the ES flow node store to query elastic to find the treePath
    spyTreePathResolver = spy(new HashMap<>());
    treePathCacheMetrics = Mockito.spy(new NoopCacheMetrics());
    treePathCache =
        new FlowNodeInstanceTreePathCache(
            List.of(1, 2), 10, spyTreePathResolver::get, treePathCacheMetrics);
  }

  @Test
  public void shouldResolveTreePathForRootLevelFNI() {
    // given
    // flow scope key and PI key is equal - no need to resolve tree path
    final var flowNodeInstanceRecord = new FNITreePathCacheCompositeKey(1, 0xCAFE, 0xABCD, 0xABCD);

    // when
    final String treePath = treePathCache.resolveParentTreePath(flowNodeInstanceRecord);

    // then
    assertThat(treePath).isEqualTo(Long.toString(0xABCD));

    Mockito.verifyNoInteractions(spyTreePathResolver);
    // the FNI is on root level and the treePath is resolve via PI key
    // without accessing the cache
    Mockito.verify(treePathCacheMetrics, times(0)).reportCacheResult(anyInt(), any());
  }

  @Test
  public void shouldResolveParentTreePathFromPreviousRecordWhenCached() {
    // given
    // root fni are added to the cache
    final var rootFlowNodeInstanceRecord =
        new FNITreePathCacheCompositeKey(1, 0xCAFE, 0xABCD, 0xABCD);
    final String treePath = String.join("/", Long.toString(0xABCD), Long.toString(0xCAFE));
    treePathCache.cacheTreePath(rootFlowNodeInstanceRecord, treePath);

    final var leafFlowNodeInstanceRecord =
        new FNITreePathCacheCompositeKey(1, 0xFACE, 0xCAFE, 0xABCD);

    // when
    // fni with flow scope key of previous root FNI
    final String secondTreePath = treePathCache.resolveParentTreePath(leafFlowNodeInstanceRecord);

    // then cache should resolve without using the resolver
    assertThat(secondTreePath).isEqualTo(treePath);

    Mockito.verifyNoInteractions(spyTreePathResolver);
    Mockito.verify(treePathCacheMetrics).reportCacheResult(1, CacheResult.HIT);
  }

  @Test
  public void shouldReturnProcessInstanceWhenNothingCachedAndStored() {
    // given
    // resolver can't resolve value - returned tree Path is equal to process instance key
    final var flowNodeInstanceRecord = new FNITreePathCacheCompositeKey(1, 0xCAFE, 0xABCD, 0xEFDA);

    // when
    final String treePath = treePathCache.resolveParentTreePath(flowNodeInstanceRecord);

    // then
    assertThat(treePath).isEqualTo(Long.toString(0xEFDA));

    Mockito.verify(spyTreePathResolver, times(1)).get(eq(0xABCDL));
    Mockito.verify(treePathCacheMetrics).reportCacheResult(1, CacheResult.MISS);
  }

  @Test
  public void shouldResolveParentTreePathViaResolver() {
    // given
    // resolver can resolve tree path
    final String expectedTreePath = String.join("/", Long.toString(0xABCD), Long.toString(0xEFDA));
    spyTreePathResolver.put(0xABCDL, expectedTreePath);
    final var flowNodeInstanceRecord = new FNITreePathCacheCompositeKey(1, 0xCAFE, 0xABCD, 0xEFDA);

    // when
    final String treePath = treePathCache.resolveParentTreePath(flowNodeInstanceRecord);

    // then
    assertThat(treePath).isEqualTo(expectedTreePath);

    Mockito.verify(spyTreePathResolver, times(1)).get(eq(0xABCDL));
    Mockito.verify(treePathCacheMetrics).reportCacheResult(1, CacheResult.MISS);
  }

  @Test
  public void shouldNotResolveTreePathTwice() {
    // given
    // cache is empty and resolver can resolve tree path
    final String expectedTreePath = String.join("/", Long.toString(0xEFDA), Long.toString(0xABCD));
    spyTreePathResolver.put(0xABCDL, expectedTreePath);
    final var flowNodeInstanceRecord = new FNITreePathCacheCompositeKey(1, 0xCAFE, 0xABCD, 0xEFDA);
    final String firstTreePath = treePathCache.resolveParentTreePath(flowNodeInstanceRecord);

    // when
    final String secondTreePath = treePathCache.resolveParentTreePath(flowNodeInstanceRecord);

    // then
    assertThat(firstTreePath).isEqualTo(secondTreePath).isEqualTo(expectedTreePath);

    Mockito.verify(spyTreePathResolver, times(1)).get(eq(0xABCDL));
    Mockito.verify(treePathCacheMetrics).reportCacheResult(1, CacheResult.MISS);
    Mockito.verify(treePathCacheMetrics).reportCacheResult(1, CacheResult.HIT);
  }

  @Test
  public void shouldThrowErrorWhenPartitionIdDoesNotFit() {
    // given
    // partition Id doesn't correspond to expected
    final var flowNodeInstanceRecord = new FNITreePathCacheCompositeKey(3, 0xCAFE, 0xABCD, 0xEFDA);

    // when - then
    assertThatThrownBy(() -> treePathCache.resolveParentTreePath(flowNodeInstanceRecord))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected to find treePath cache for partitionId 3, but found nothing. Possible partition Ids are: '[1, 2]'.");

    Mockito.verifyNoInteractions(spyTreePathResolver);
    Mockito.verifyNoInteractions(treePathCacheMetrics);
  }
}
