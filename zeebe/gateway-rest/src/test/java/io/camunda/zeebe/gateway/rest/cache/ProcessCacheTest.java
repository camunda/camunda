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
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.ProcessCacheConfiguration;
import io.camunda.zeebe.gateway.rest.util.XmlUtil;
import io.camunda.zeebe.gateway.rest.util.XmlUtil.ProcessFlowNode;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class ProcessCacheTest {

  private ProcessCache processCache;
  private GatewayRestConfiguration configuration;
  private ProcessCacheConfiguration processCacheConfig;
  private XmlUtil xmlUtil;

  @BeforeEach
  void setUp() {
    configuration = mock(GatewayRestConfiguration.class);
    processCacheConfig = mock(ProcessCacheConfiguration.class);
    when(configuration.getProcessCache()).thenReturn(processCacheConfig);
    when(configuration.getProcessCache().getMaxSize()).thenReturn(10);
    xmlUtil = mock(XmlUtil.class);
    processCache = new ProcessCache(configuration, xmlUtil);
    mockLoad(Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")));
  }

  @AfterEach
  void tearDown() {
    processCache.getCache().cleanUp();
    processCache.getCache().invalidateAll();
    processCache.getCache().cleanUp();
  }

  private <T> Answer<T> mockLoadAnswer(final Tuple<Long, ProcessFlowNode>[] nodes) {
    return invocation -> {
      final var consumer = invocation.<BiConsumer<Long, ProcessFlowNode>>getArgument(1);
      Arrays.stream(nodes).forEach(t -> consumer.accept(t.getLeft(), t.getRight()));
      return null;
    };
  }

  @SafeVarargs
  final void mockLoad(final Tuple<Long, ProcessFlowNode>... nodes) {
    doAnswer(mockLoadAnswer(nodes)).when(xmlUtil).extractFlowNodeNames(anyLong(), any());
  }

  @SafeVarargs
  final void mockLoadAll(final Tuple<Long, ProcessFlowNode>... nodes) {
    doAnswer(mockLoadAnswer(nodes)).when(xmlUtil).extractFlowNodeNames(anySet(), any());
  }

  @Test
  void shouldRemoveOldest() {
    // given
    when(processCacheConfig.getMaxSize()).thenReturn(2);
    processCache = new ProcessCache(configuration, xmlUtil);
    processCache.getCacheItem(2L);
    processCache.getCacheItem(1L);
    processCache.getCache().cleanUp();
    assertThat(processCache.getCache().asMap()).hasSize(2);

    // when - adding 3
    processCache.getCacheItem(3L);
    processCache.getCache().cleanUp();

    // then - 1 should be removed
    assertThat(processCache.getCache().asMap()).hasSize(2);
    assertThat(processCache.getCache().asMap().keySet()).containsExactlyInAnyOrder(2L, 3L);
  }

  @Test
  void shouldRefreshReadItemAndRemoveOldest() {
    // given
    when(processCacheConfig.getMaxSize()).thenReturn(2);
    processCache = new ProcessCache(configuration, xmlUtil);
    processCache.getCacheItem(1L);
    processCache.getCacheItem(2L);
    processCache.getCache().cleanUp();
    assertThat(processCache.getCache().asMap()).hasSize(2);

    // when - read 1 and adding 3
    processCache.getCacheItem(1L);
    processCache.getCacheItem(3L);
    processCache.getCache().cleanUp();

    // then - 2 should be removed
    assertThat(processCache.getCache().asMap()).hasSize(2);
    assertThat(processCache.getCache().asMap().keySet()).containsExactlyInAnyOrder(1L, 3L);
  }

  @Test
  void shouldRemoveExpiredItem() throws InterruptedException {
    // given
    when(processCacheConfig.getExpirationMillis()).thenReturn(100L);
    processCache = new ProcessCache(configuration, xmlUtil);
    processCache.getCacheItem(1L);
    processCache.getCache().cleanUp();
    assertThat(processCache.getCache().asMap()).hasSize(1);

    // when - waiting ttl millis
    Thread.sleep(100);
    processCache.getCache().cleanUp();

    // then - cache should be empty
    assertThat(processCache.getCache().asMap()).isEmpty();
  }

  @Test
  void shouldNotLoadIfAvailable() {
    // given
    mockLoad(Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")));
    processCache.getCacheItem(1L);
    processCache.getCache().cleanUp();

    // when
    processCache.getCacheItem(1L);

    // then - extractFlowNodeNames not called again
    verify(xmlUtil).extractFlowNodeNames(eq(1L), any());
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
    verify(xmlUtil).extractFlowNodeNames(eq(1L), any());
    assertThat(actual.flowNodes()).hasSize(2);
    assertThat(actual.flowNodes()).containsOnly(entry("id1", "Name 1"), entry("id2", "Name 2"));
    assertThat(processCache.getCache().asMap()).hasSize(1);
    assertThat(processCache.getCache().asMap()).containsOnlyKeys(1L);
    assertThat(processCache.getCache().get(1L).flowNodes())
        .containsOnly(entry("id1", "Name 1"), entry("id2", "Name 2"));
  }

  @Test
  void shouldLoadFlowNodesForProcessDefinitions() {
    // given
    mockLoadAll(
        Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")),
        Tuple.of(2L, new ProcessFlowNode("id21", "Name 21")),
        Tuple.of(2L, new ProcessFlowNode("id22", "Name 22")),
        Tuple.of(3L, new ProcessFlowNode("id3", "Name 3")));

    // when
    final var actual = processCache.getCacheItems(List.of(1L, 2L, 3L));

    // then
    verify(xmlUtil).extractFlowNodeNames(eq(Set.of(1L, 2L, 3L)), any());
    assertThat(actual).hasSize(3);
    assertThat(actual.keySet()).containsOnly(1L, 2L, 3L);
    assertThat(actual.get(1L).flowNodes()).containsOnly(entry("id1", "Name 1"));
    assertThat(actual.get(2L).flowNodes())
        .containsOnly(entry("id21", "Name 21"), entry("id22", "Name 22"));
    assertThat(actual.get(3L).flowNodes()).containsOnly(entry("id3", "Name 3"));
    assertThat(processCache.getCache().asMap()).hasSize(3);
    assertThat(processCache.getCache().asMap()).containsOnlyKeys(1L, 2L, 3L);
    assertThat(processCache.getCache().get(1L).flowNodes()).containsOnly(entry("id1", "Name 1"));
    assertThat(processCache.getCache().get(2L).flowNodes())
        .containsOnly(entry("id21", "Name 21"), entry("id22", "Name 22"));
    assertThat(processCache.getCache().get(3L).flowNodes()).containsOnly(entry("id3", "Name 3"));
  }
}
