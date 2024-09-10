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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import io.camunda.operate.zeebeimport.cache.TreePathCacheMetrics.CacheResult;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ObservableFlowNodeInstanceTreePathCacheTest {

  private HashMap<Long, String> spyResolverCache;
  private FlowNodeInstanceTreePathCache treePathCache;
  private TreePathCacheMetrics treePathCacheMetrics;

  @BeforeEach
  void setup() {
    spyResolverCache = spy(new HashMap<Long, String>());
    treePathCacheMetrics = Mockito.spy(new NoopCacheMetrics());
    treePathCache =
        new FlowNodeInstanceTreePathCache(
            List.of(1, 2), 10, spyResolverCache::get, treePathCacheMetrics);
  }

  @Test
  public void shouldResolveTreePathForRootLevelFNI() {
    // given
    // flow scope key and PI key is equal - no need to resolve tree path
    final var flowNodeInstanceRecord = new FlowNodeInstanceRecord(1, 0xCAFE, 0xABCD, 0xABCD);

    // when
    final String treePath = treePathCache.resolveTreePath(flowNodeInstanceRecord);

    // then
    assertThat(treePath).isEqualTo(Long.toString(0xABCD));

    Mockito.verifyNoInteractions(spyResolverCache);
    Mockito.verify(treePathCacheMetrics).observeCacheResult(CacheResult.FOUND);
  }

  @Test
  public void shouldResolveTreePathFromPreviousRecord() {
    // given
    // root fni are added to the cache
    final var rootFlowNodeInstanceRecord = new FlowNodeInstanceRecord(1, 0xCAFE, 0xABCD, 0xABCD);
    final String firstTreePath = treePathCache.resolveTreePath(rootFlowNodeInstanceRecord);

    final var leafFlowNodeInstanceRecord = new FlowNodeInstanceRecord(1, 0xFACE, 0xCAFE, 0xABCD);

    // when
    // fni with flow scope key of previous root FNI
    final String secondTreePath = treePathCache.resolveTreePath(leafFlowNodeInstanceRecord);

    // then cache should resolve without using the resolver
    assertThat(secondTreePath)
        .contains(firstTreePath)
        .isEqualTo(String.join("/", Long.toString(0xABCD), Long.toString(0xCAFE)));

    Mockito.verifyNoInteractions(spyResolverCache);
    Mockito.verify(treePathCacheMetrics, times(2)).observeCacheResult(CacheResult.FOUND);
  }

  @Test
  public void shouldTryToResolve() {
    // given
    // resolver can't resolve value - returned tree Path is equal to process instance key
    final var flowNodeInstanceRecord = new FlowNodeInstanceRecord(1, 0xCAFE, 0xABCD, 0xEFDA);

    // when
    final String treePath = treePathCache.resolveTreePath(flowNodeInstanceRecord);

    // then
    assertThat(treePath).isEqualTo(Long.toString(0xEFDA));

    Mockito.verify(spyResolverCache, times(1)).get(eq(0xABCDL));
    Mockito.verify(treePathCacheMetrics, times(1)).observeCacheResult(CacheResult.MISS);
  }

  @Test
  public void shouldResolveTreePath() {
    // given
    // resolver can resolve tree path
    final String expectedTreePath = String.join("/", Long.toString(0xABCD), Long.toString(0xEFDA));
    spyResolverCache.put(0xABCDL, expectedTreePath);
    final var flowNodeInstanceRecord = new FlowNodeInstanceRecord(1, 0xCAFE, 0xABCD, 0xEFDA);

    // when
    final String treePath = treePathCache.resolveTreePath(flowNodeInstanceRecord);

    // then
    assertThat(treePath).isEqualTo(expectedTreePath);

    Mockito.verify(spyResolverCache, times(1)).get(eq(0xABCDL));
    Mockito.verify(treePathCacheMetrics, times(1)).observeCacheResult(CacheResult.MISS);
  }

  @Test
  public void shouldNotResolveTreePathTwice() {
    // given
    // cache is empty and resolver can resolve tree path
    final String expectedTreePath = String.join("/", Long.toString(0xABCD), Long.toString(0xEFDA));
    spyResolverCache.put(0xABCDL, expectedTreePath);
    final var flowNodeInstanceRecord = new FlowNodeInstanceRecord(1, 0xCAFE, 0xABCD, 0xEFDA);
    final String firstTreePath = treePathCache.resolveTreePath(flowNodeInstanceRecord);

    // when
    final String secondTreePath = treePathCache.resolveTreePath(flowNodeInstanceRecord);

    // then
    assertThat(firstTreePath).isEqualTo(secondTreePath).isEqualTo(expectedTreePath);

    Mockito.verify(spyResolverCache, times(1)).get(eq(0xABCDL));
    Mockito.verify(treePathCacheMetrics, times(1)).observeCacheResult(CacheResult.MISS);
    Mockito.verify(treePathCacheMetrics, times(1)).observeCacheResult(CacheResult.FOUND);
  }

  @Test
  public void shouldThrowErrorWhenPartitionIdDoesNotFit() {
    // given
    // partition Id doesn't correspond to expected
    final var flowNodeInstanceRecord = new FlowNodeInstanceRecord(3, 0xCAFE, 0xABCD, 0xEFDA);

    // when - then
    assertThatThrownBy(() -> treePathCache.resolveTreePath(flowNodeInstanceRecord))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected to find treePath cache for partitionId 3, but found nothing. Possible partition Ids are: '[1, 2]'.");
    Mockito.verify(treePathCacheMetrics, times(1)).observeCacheResult(CacheResult.FAILURE);
  }

  @Test
  public void shouldObserveCacheResolvementTime() {
    // given
    final AtomicLong measurement = new AtomicLong(0);
    final var metrics =
        spy(
            new TreePathCacheMetrics() {
              @Override
              public void observeCacheResult(final CacheResult result) {}

              @Override
              public String observeTimeOfTreePathResolvement(final Supplier<String> resolving) {
                final long start = System.currentTimeMillis();
                final var result = resolving.get();
                measurement.set(System.currentTimeMillis() - start);
                return result;
              }
            });
    final var observalTreePathCache =
        new FlowNodeInstanceTreePathCache(List.of(1, 2), 10, createDelayedSupplier(), metrics);
    // partition Id doesn't correspond to expected
    final var flowNodeInstanceRecord = new FlowNodeInstanceRecord(1, 0xCAFE, 0xABCD, 0xEFDA);

    // when
    final String treePath = observalTreePathCache.resolveTreePath(flowNodeInstanceRecord);

    // then
    assertThat(treePath).isEqualTo(Long.toString(0xEFDA));

    assertThat(measurement.get()).isGreaterThanOrEqualTo(10);

    Mockito.verify(spyResolverCache, times(1)).get(eq(0xABCDL));
    Mockito.verify(metrics, times(1)).observeCacheResult(CacheResult.MISS);
    Mockito.verify(metrics).observeTimeOfTreePathResolvement(any());
  }

  private Function<Long, String> createDelayedSupplier() {
    return (key) -> {
      try {
        // we add an artificial delay - expected to be measured
        Thread.sleep(10);
      } catch (final InterruptedException e) {
        throw new RuntimeException(e);
      }
      return spyResolverCache.get(key);
    };
  }
}
