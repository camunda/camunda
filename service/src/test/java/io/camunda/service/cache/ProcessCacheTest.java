/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.cache.ProcessCache.Configuration;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProcessCacheTest {

  private ProcessCache processCache;
  private ProcessCache.Configuration configuration;
  private ProcessDefinitionServices processDefinitionServices;
  private BrokerTopologyManager brokerTopologyManager;
  private MeterRegistry meterRegistry;

  private ProcessDefinitionEntity entity;

  @BeforeEach
  public void setUp() throws IOException {
    entity =
        Instancio.of(ProcessDefinitionEntity.class)
            .set(field(ProcessDefinitionEntity::bpmnXml), loadBpmn("xmlUtil-test1.bpmn"))
            .set(field(ProcessDefinitionEntity::processDefinitionId), "testProcess")
            .create();

    configuration = ProcessCache.Configuration.getDefault();
    processDefinitionServices = mock(ProcessDefinitionServices.class);
    brokerTopologyManager = mock(BrokerTopologyManager.class);
    meterRegistry = new SimpleMeterRegistry();
    processCache =
        new ProcessCache(
            configuration, processDefinitionServices, brokerTopologyManager, meterRegistry);

    when(processDefinitionServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(processDefinitionServices);
    when(processDefinitionServices.getByKey(entity.processDefinitionKey())).thenReturn(entity);
  }

  @AfterEach
  public void tearDown() {
    getCache().cleanUp();
    getCache().invalidateAll();
    getCache().cleanUp();
  }

  private LoadingCache<Long, ProcessCacheItem> getCache() {
    return processCache.getRawCache();
  }

  private ConcurrentMap<Long, ProcessCacheItem> getCacheMap() {
    return getCache().asMap();
  }

  @Test
  void shouldPopulateCache() {
    // when
    final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey());

    // then - extractElementNames not called again
    verify(processDefinitionServices, times(1)).getByKey(entity.processDefinitionKey());
    assertThat(cacheItem).isNotEqualTo(ProcessCacheItem.EMPTY);
  }

  @Test
  void shouldNotPopulateCacheWhenNotFound() {
    // given
    when(processDefinitionServices.getByKey(entity.processDefinitionKey()))
        .thenThrow(new NoSuchElementException());

    // when
    final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey());

    // then - extractElementNames not called again
    verify(processDefinitionServices, times(1)).getByKey(entity.processDefinitionKey());
    assertThat(cacheItem).isEqualTo(ProcessCacheItem.EMPTY);
  }

  @Test
  void shouldNotRepopulateWhenAlreadyCached() {
    // when
    processCache.getCacheItem(entity.processDefinitionKey());
    getCache().cleanUp();
    processCache.getCacheItem(entity.processDefinitionKey());

    // then - extractElementNames not called again
    verify(processDefinitionServices, times(1)).getByKey(entity.processDefinitionKey());
  }

  @Test
  void shouldPopulateCacheWithMultiple() throws IOException {
    final var otherEntity =
        Instancio.of(ProcessDefinitionEntity.class)
            .set(field(ProcessDefinitionEntity::bpmnXml), loadBpmn("xmlUtil-test2.bpmn"))
            .set(field(ProcessDefinitionEntity::processDefinitionId), "parent_process_v1")
            .create();

    when(processDefinitionServices.search(any()))
        .thenReturn(SearchQueryResult.of(entity, otherEntity));

    // when
    final var cacheResult =
        processCache.getCacheItems(
            Set.of(entity.processDefinitionKey(), otherEntity.processDefinitionKey()));

    // then
    verify(processDefinitionServices, times(1)).search(any());
    final var cacheMap = getCacheMap();
    assertThat(cacheMap)
        .containsOnlyKeys(entity.processDefinitionKey(), otherEntity.processDefinitionKey());
  }

  @Test
  void shouldRepopulateAndRemoveLeastRecentlyUsed() {
    // given
    configuration = new Configuration(2, 100L);
    processCache =
        new ProcessCache(
            configuration, processDefinitionServices, brokerTopologyManager, meterRegistry);
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

  private String loadBpmn(final String name) throws IOException {
    try (final InputStream inputStream = getClass().getClassLoader().getResourceAsStream(name)) {
      assert inputStream != null;
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Nested
  class GetElementName {
    @Test
    void shouldResolveName() {
      // when
      final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey());

      // then - extractElementNames not called again
      assertThat(cacheItem.getElementName("StartEvent_1")).isEqualTo("Start");
      assertThat(cacheItem.getElementName("taskB")).isEqualTo("Task B");
    }

    @Test
    void shouldResolveDefaultName() {
      // given
      final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey());

      // then - extractElementNames not called again
      assertThat(cacheItem.getElementName("non-existing")).isEqualTo("non-existing");
    }

    @Test
    void shouldResolveMultiple() throws IOException {
      final var otherEntity =
          Instancio.of(ProcessDefinitionEntity.class)
              .set(field(ProcessDefinitionEntity::bpmnXml), loadBpmn("xmlUtil-test2.bpmn"))
              .set(field(ProcessDefinitionEntity::processDefinitionId), "parent_process_v1")
              .create();

      when(processDefinitionServices.search(any()))
          .thenReturn(SearchQueryResult.of(entity, otherEntity));

      // when
      final var cacheResult =
          processCache.getCacheItems(
              Set.of(entity.processDefinitionKey(), otherEntity.processDefinitionKey()));

      // then
      assertThat(
              cacheResult
                  .getProcessItem(entity.processDefinitionKey())
                  .getElementName("StartEvent_1"))
          .isEqualTo("Start");

      assertThat(
              cacheResult
                  .getProcessItem(otherEntity.processDefinitionKey())
                  .getElementName("call_activity"))
          .isEqualTo("Call Activity");
    }
  }
}
