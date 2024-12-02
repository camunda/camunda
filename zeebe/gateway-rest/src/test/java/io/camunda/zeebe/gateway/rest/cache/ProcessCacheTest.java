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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.gateway.rest.util.XmlUtil.ProcessFlowNode;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProcessCacheTest extends ProcessCacheTestBase {

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
    final var actual = processCache.getCacheItems(Set.of(1L, 2L, 3L));

    // then
    verify(xmlUtil).extractFlowNodeNames(eq(Set.of(1L, 2L, 3L)), any());
    assertThat(actual.processCacheItems()).hasSize(3);
    assertThat(actual.processCacheItems().keySet()).containsOnly(1L, 2L, 3L);
    assertThat(actual.processCacheItems().get(1L).flowNodes()).containsOnly(entry("id1", "Name 1"));
    assertThat(actual.processCacheItems().get(2L).flowNodes())
        .containsOnly(entry("id21", "Name 21"), entry("id22", "Name 22"));
    assertThat(actual.processCacheItems().get(3L).flowNodes()).containsOnly(entry("id3", "Name 3"));
    assertThat(processCache.getCache().asMap()).hasSize(3);
    assertThat(processCache.getCache().asMap()).containsOnlyKeys(1L, 2L, 3L);
    assertThat(processCache.getCache().get(1L).flowNodes()).containsOnly(entry("id1", "Name 1"));
    assertThat(processCache.getCache().get(2L).flowNodes())
        .containsOnly(entry("id21", "Name 21"), entry("id22", "Name 22"));
    assertThat(processCache.getCache().get(3L).flowNodes()).containsOnly(entry("id3", "Name 3"));
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
  void shouldResolveAnyProcessDefinitionAndFlowNode() {
    // given
    mockLoad(
        Tuple.of(1L, new ProcessFlowNode("id1", "Name 1")),
        Tuple.of(2L, new ProcessFlowNode("id2", "Name 2")));
    final var cacheItem = processCache.getCacheItems(Set.of(1L, 2L));

    // when
    final var actual = cacheItem.getFlowNodeName(99L, "non-existing");

    // then
    assertThat(actual).isNotNull();
    assertThat(actual).isEqualTo("non-existing");
  }
}
