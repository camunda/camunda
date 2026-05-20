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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.SearchQueryResult;
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

  private static final String TENANT = "default";

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

    when(processDefinitionServices.getByKey(eq(entity.processDefinitionKey()), any(), any()))
        .thenReturn(entity);
  }

  @AfterEach
  public void tearDown() {
    getCache().cleanUp();
    getCache().invalidateAll();
    getCache().cleanUp();
  }

  private LoadingCache<Long, ProcessCacheItem> getCache() {
    return processCache.getRawCache(TENANT);
  }

  private ConcurrentMap<Long, ProcessCacheItem> getCacheMap() {
    return getCache().asMap();
  }

  @Test
  void shouldPopulateCache() {
    // when
    final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey(), TENANT);

    // then - extractElementNames not called again
    verify(processDefinitionServices, times(1))
        .getByKey(eq(entity.processDefinitionKey()), any(), any());
    assertThat(cacheItem).isNotEqualTo(ProcessCacheItem.EMPTY);
  }

  @Test
  void shouldNotPopulateCacheWhenNotFound() {
    // given
    when(processDefinitionServices.getByKey(eq(entity.processDefinitionKey()), any(), any()))
        .thenThrow(new NoSuchElementException());

    // when
    final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey(), TENANT);

    // then - extractElementNames not called again
    verify(processDefinitionServices, times(1))
        .getByKey(eq(entity.processDefinitionKey()), any(), any());
    assertThat(cacheItem).isEqualTo(ProcessCacheItem.EMPTY);
  }

  @Test
  void shouldNotRepopulateWhenAlreadyCached() {
    // when
    processCache.getCacheItem(entity.processDefinitionKey(), TENANT);
    getCache().cleanUp();
    processCache.getCacheItem(entity.processDefinitionKey(), TENANT);

    // then - extractElementNames not called again
    verify(processDefinitionServices, times(1))
        .getByKey(eq(entity.processDefinitionKey()), any(), any());
  }

  @Test
  void shouldPopulateCacheWithMultiple() throws IOException {
    final var otherEntity =
        Instancio.of(ProcessDefinitionEntity.class)
            .set(field(ProcessDefinitionEntity::bpmnXml), loadBpmn("xmlUtil-test2.bpmn"))
            .set(field(ProcessDefinitionEntity::processDefinitionId), "parent_process_v1")
            .create();

    when(processDefinitionServices.search(any(), any(), any()))
        .thenReturn(SearchQueryResult.of(entity, otherEntity));

    // when
    final var cacheResult =
        processCache.getCacheItems(
            Set.of(entity.processDefinitionKey(), otherEntity.processDefinitionKey()), TENANT);

    // then
    verify(processDefinitionServices, times(1)).search(any(), any(), any());
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
    processCache.getCacheItem(1L, TENANT);
    processCache.getCacheItem(2L, TENANT);
    getCache().cleanUp();
    assertThat(getCacheMap()).hasSize(2);

    // when - read 1 and adding 3
    processCache.getCacheItem(1L, TENANT);
    processCache.getCacheItem(3L, TENANT);
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
      final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey(), TENANT);

      // then - extractElementNames not called again
      assertThat(cacheItem.getElementName("StartEvent_1")).isEqualTo("Start");
      assertThat(cacheItem.getElementName("taskB")).isEqualTo("Task B");
    }

    @Test
    void shouldResolveDefaultName() {
      // given
      final var cacheItem = processCache.getCacheItem(entity.processDefinitionKey(), TENANT);

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

      when(processDefinitionServices.search(any(), any(), any()))
          .thenReturn(SearchQueryResult.of(entity, otherEntity));

      // when
      final var cacheResult =
          processCache.getCacheItems(
              Set.of(entity.processDefinitionKey(), otherEntity.processDefinitionKey()), TENANT);

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

  @Nested
  class PerTenantCache {

    @Test
    void shouldHaveSeparateCachesPerTenant() {
      // when - load key for tenantA only
      processCache.getCacheItem(entity.processDefinitionKey(), "tenantA");

      // then - tenantB's cache is untouched
      assertThat(processCache.getRawCache("tenantB").asMap()).isEmpty();
    }

    @Test
    void shouldLoadSeparatelyForEachTenant() {
      // when - same key loaded for two different tenants
      processCache.getCacheItem(entity.processDefinitionKey(), "tenantA");
      processCache.getCacheItem(entity.processDefinitionKey(), "tenantB");

      // then - service called once per tenant, not served from a shared cache
      verify(processDefinitionServices, times(2))
          .getByKey(eq(entity.processDefinitionKey()), any(), any());
    }

    @Test
    void shouldRespectMaxSizePerTenantIndependently() {
      // given - small cache (max 2 per tenant)
      configuration = new Configuration(2, null);
      processCache =
          new ProcessCache(
              configuration, processDefinitionServices, brokerTopologyManager, meterRegistry);

      processCache.getCacheItem(1L, "tenantA");
      processCache.getCacheItem(2L, "tenantA");
      processCache.getCacheItem(1L, "tenantB");
      processCache.getCacheItem(2L, "tenantB");
      processCache.getRawCache("tenantA").cleanUp();
      processCache.getRawCache("tenantB").cleanUp();
      assertThat(processCache.getRawCache("tenantA").asMap()).hasSize(2);
      assertThat(processCache.getRawCache("tenantB").asMap()).hasSize(2);

      // when - add a third entry to tenantA only
      processCache.getCacheItem(3L, "tenantA");
      processCache.getRawCache("tenantA").cleanUp();
      processCache.getRawCache("tenantB").cleanUp();

      // then - tenantA evicted one entry, tenantB is unaffected
      assertThat(processCache.getRawCache("tenantA").asMap()).hasSize(2);
      assertThat(processCache.getRawCache("tenantB").asMap()).hasSize(2);
    }

    @Test
    void shouldInvalidateAllTenantCaches() {
      // given - both tenants have cached entries
      processCache.getCacheItem(entity.processDefinitionKey(), "tenantA");
      processCache.getCacheItem(entity.processDefinitionKey(), "tenantB");
      assertThat(processCache.getRawCache("tenantA").asMap()).isNotEmpty();
      assertThat(processCache.getRawCache("tenantB").asMap()).isNotEmpty();

      // when
      processCache.invalidate();

      // then - both caches are cleared
      assertThat(processCache.getRawCache("tenantA").asMap()).isEmpty();
      assertThat(processCache.getRawCache("tenantB").asMap()).isEmpty();
    }
  }
}
