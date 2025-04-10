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
import io.camunda.zeebe.gateway.rest.util.ProcessFlowNodeProvider;
import io.camunda.zeebe.gateway.rest.util.ProcessFlowNodeProvider.ProcessFlowNode;
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
  private ProcessFlowNodeProvider processFlowNodeProvider;
  private BrokerTopologyManager brokerTopologyManager;
  private MeterRegistry meterRegistry;

  @BeforeEach
  public void setUp() {
    configuration = new GatewayRestConfiguration();
    processFlowNodeProvider = mock(ProcessFlowNodeProvider.class);
    brokerTopologyManager = mock(BrokerTopologyManager.class);
    meterRegistry = new SimpleMeterRegistry();
    processCache =
        new ProcessCache(
            configuration, processFlowNodeProvider, brokerTopologyManager, meterRegistry);
    mockLoad(Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")));
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
  private void mockLoad(final Tuple<Long, ProcessFlowNode>... nodes) {
    doAnswer(mockLoadAnswer(nodes))
        .when(processFlowNodeProvider)
        .extractFlowNodeNames(anyLong(), any());
  }

  @SafeVarargs
  private <T> Answer<T> mockLoadAnswer(final Tuple<Long, ProcessFlowNode>... nodes) {
    return invocation -> {
      final var consumer = invocation.<BiConsumer<Long, ProcessFlowNode>>getArgument(1);
      Arrays.stream(nodes).forEach(t -> consumer.accept(t.getLeft(), t.getRight()));
      return null;
    };
  }

  @Test
  void shouldNotLoadIfAvailable() {
    // given
    mockLoad(Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")));
    processCache.getCacheItem(1L);
    getCache().cleanUp();

    // when
    processCache.getCacheItem(1L);

    // then - extractFlowNodeNames not called again
    verify(processFlowNodeProvider).extractFlowNodeNames(eq(1L), any());
  }

  @Test
  void shouldLoadFlowNodesForProcessDefinition() {
    // given
    mockLoad(
        Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")),
        Tuple.of(1L, new ProcessFlowNode("id2", "Name 2")));

    // when
    final var actual = processCache.getCacheItem(1L);

    // then
    verify(processFlowNodeProvider).extractFlowNodeNames(eq(1L), any());
    assertThat(actual.flowNodeIdNameMap()).hasSize(2);
    assertThat(actual.flowNodeIdNameMap())
        .containsOnly(entry("id1", "Name 1"), entry("id2", "Name 2"));
    final var cacheMap = getCacheMap();
    assertThat(cacheMap).hasSize(1);
    assertThat(cacheMap).containsOnlyKeys(1L);
    assertThat(cacheMap.get(1L).flowNodeIdNameMap())
        .containsOnly(entry("id1", "Name 1"), entry("id2", "Name 2"));
  }

  @Test
  void shouldLoadFlowNodesForProcessDefinitions() {
    // given
    doAnswer(
            mockLoadAnswer(
                Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")),
                Tuple.of(2L, new ProcessFlowNode("id21", "Name 21")),
                Tuple.of(2L, new ProcessFlowNode("id22", "Name 22")),
                Tuple.of(3L, new ProcessFlowNode("id3", "Name 3"))))
        .when(processFlowNodeProvider)
        .extractFlowNodeNames(anySet(), any());

    // when
    final var actual = processCache.getCacheItems(Set.of(1L, 2L, 3L));

    // then
    verify(processFlowNodeProvider).extractFlowNodeNames(eq(Set.of(1L, 2L, 3L)), any());
    assertThat(actual).hasSize(3);
    assertThat(actual.keySet()).containsOnly(1L, 2L, 3L);
    assertThat(actual.get(1L).flowNodeIdNameMap()).containsOnly(entry("id1", "Name 1"));
    assertThat(actual.get(2L).flowNodeIdNameMap())
        .containsOnly(entry("id21", "Name 21"), entry("id22", "Name 22"));
    assertThat(actual.get(3L).flowNodeIdNameMap()).containsOnly(entry("id3", "Name 3"));
    final var cacheMap = getCacheMap();
    assertThat(cacheMap).hasSize(3);
    assertThat(cacheMap).containsOnlyKeys(1L, 2L, 3L);
    assertThat(cacheMap.get(1L).flowNodeIdNameMap()).containsOnly(entry("id1", "Name 1"));
    assertThat(cacheMap.get(2L).flowNodeIdNameMap())
        .containsOnly(entry("id21", "Name 21"), entry("id22", "Name 22"));
    assertThat(cacheMap.get(3L).flowNodeIdNameMap()).containsOnly(entry("id3", "Name 3"));
  }

  @Test
  void shouldResolveAnyFlowNode() {
    // given
    mockLoad(Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")));
    final var cacheItem = processCache.getCacheItem(1L);

    // when
    final var actual = cacheItem.getFlowNodeName("non-existing");

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
            configuration, processFlowNodeProvider, brokerTopologyManager, meterRegistry);
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
