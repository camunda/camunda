/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.util.ProcessElementProvider;
import io.camunda.zeebe.gateway.rest.util.ProcessElementProvider.ProcessElement;
import io.camunda.zeebe.util.collection.Tuple;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

class ProcessCacheTest {

  private ProcessCache processCache;
  private GatewayRestConfiguration configuration;
  private ProcessElementProvider processElementProvider;
  private BrokerTopologyManager brokerTopologyManager;
  private MeterRegistry meterRegistry;

  @BeforeEach
  public void setUp() {
    configuration = new GatewayRestConfiguration();
    processElementProvider = mock(ProcessElementProvider.class);
    brokerTopologyManager = mock(BrokerTopologyManager.class);
    meterRegistry = new SimpleMeterRegistry();
    processCache =
        new ProcessCache(
            configuration, processElementProvider, brokerTopologyManager, meterRegistry);
    mockLoad(Tuple.of(1L, new ProcessElement("id1", "Name 1")));
  }

  @AfterEach
  public void tearDown() {
    getCache().cleanUp();
    getCache().invalidateAll();
    getCache().cleanUp();
  }

  private LoadingCache<Long, ProcessCacheItem> getCache() {
    return (LoadingCache<Long, ProcessCacheItem>)
        ReflectionTestUtils.getField(processCache, "cache");
  }

  private ConcurrentMap<Long, ProcessCacheItem> getCacheMap() {
    return getCache().asMap();
  }

  @SafeVarargs
  private void mockLoad(final Tuple<Long, ProcessElement>... nodes) {
    doAnswer(mockLoadAnswer(nodes))
        .when(processElementProvider)
        .extractElementNames(anyLong(), any());
  }

  @SafeVarargs
  private <T> Answer<T> mockLoadAnswer(final Tuple<Long, ProcessElement>... nodes) {
    return invocation -> {
      final var consumer = invocation.<BiConsumer<Long, ProcessElement>>getArgument(1);
      Arrays.stream(nodes).forEach(t -> consumer.accept(t.getLeft(), t.getRight()));
      return null;
    };
  }

  @Test
  void shouldNotLoadIfAvailable() {
    // given
    mockLoad(Tuple.of(1L, new ProcessElement("id1", "Name 1")));
    processCache.getCacheItem(1L);
    getCache().cleanUp();

    // when
    processCache.getCacheItem(1L);

    // then - extractElementNames not called again
    verify(processElementProvider).extractElementNames(eq(1L), any());
  }

  @Test
  void shouldLoadElementsForProcessDefinition() {
    // given
    mockLoad(
        Tuple.of(1L, new ProcessElement("id1", "Name 1")),
        Tuple.of(1L, new ProcessElement("id2", "Name 2")));

    // when
    final var actual = processCache.getCacheItem(1L);

    // then
    verify(processElementProvider).extractElementNames(eq(1L), any());
    assertThat(actual.elementIdNameMap()).hasSize(2);
    assertThat(actual.elementIdNameMap())
        .containsOnly(entry("id1", "Name 1"), entry("id2", "Name 2"));
    final var cacheMap = getCacheMap();
    assertThat(cacheMap).hasSize(1);
    assertThat(cacheMap).containsOnlyKeys(1L);
    assertThat(cacheMap.get(1L).elementIdNameMap())
        .containsOnly(entry("id1", "Name 1"), entry("id2", "Name 2"));
  }

  @Test
  void shouldLoadElementsForProcessDefinitions() {
    // given
    doAnswer(
            mockLoadAnswer(
                Tuple.of(1L, new ProcessElement("id1", "Name 1")),
                Tuple.of(2L, new ProcessElement("id21", "Name 21")),
                Tuple.of(2L, new ProcessElement("id22", "Name 22")),
                Tuple.of(3L, new ProcessElement("id3", "Name 3"))))
        .when(processElementProvider)
        .extractElementNames(anySet(), any());

    // when
    final var actual = processCache.getCacheItems(Set.of(1L, 2L, 3L));

    // then
    verify(processElementProvider).extractElementNames(eq(Set.of(1L, 2L, 3L)), any());
    assertThat(actual).hasSize(3);
    assertThat(actual.keySet()).containsOnly(1L, 2L, 3L);
    assertThat(actual.get(1L).elementIdNameMap()).containsOnly(entry("id1", "Name 1"));
    assertThat(actual.get(2L).elementIdNameMap())
        .containsOnly(entry("id21", "Name 21"), entry("id22", "Name 22"));
    assertThat(actual.get(3L).elementIdNameMap()).containsOnly(entry("id3", "Name 3"));
    final var cacheMap = getCacheMap();
    assertThat(cacheMap).hasSize(3);
    assertThat(cacheMap).containsOnlyKeys(1L, 2L, 3L);
    assertThat(cacheMap.get(1L).elementIdNameMap()).containsOnly(entry("id1", "Name 1"));
    assertThat(cacheMap.get(2L).elementIdNameMap())
        .containsOnly(entry("id21", "Name 21"), entry("id22", "Name 22"));
    assertThat(cacheMap.get(3L).elementIdNameMap()).containsOnly(entry("id3", "Name 3"));
  }

  @Test
  void shouldResolveAnyElement() {
    // given
    mockLoad(Tuple.of(1L, new ProcessElement("id1", "Name 1")));
    final var cacheItem = processCache.getCacheItem(1L);

    // when
    final var actual = cacheItem.getElementName("non-existing");

    // then
    assertThat(actual).isNotNull();
    assertThat(actual).isEqualTo("non-existing");
  }

  @Test
  void shouldRefreshReadItemAndRemoveLeastRecentlyUsed() {
    // given
    configuration.getProcessCache().setMaxSize(2);
    processCache =
        new ProcessCache(
            configuration, processElementProvider, brokerTopologyManager, meterRegistry);
    processCache.getCacheItem(1L);
    processCache.getCacheItem(2L);
    getCache().cleanUp();
    assertThat(getCacheMap()).hasSize(2);

    // when - read 1 and adding 3
    processCache.getCacheItem(1L);
    processCache.getCacheItem(3L);
    getCache().cleanUp();

    // then - 2 should be removed
    final var cacheMap = getCacheMap();
    assertThat(cacheMap).hasSize(2);
    assertThat(cacheMap.keySet()).containsExactlyInAnyOrder(1L, 3L);
  }
}
