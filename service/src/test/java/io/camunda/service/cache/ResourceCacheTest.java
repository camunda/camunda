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
import static org.mockito.Mockito.mock;

import com.github.benmanes.caffeine.cache.Cache;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.service.cache.ResourceCache.Configuration;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.ConcurrentMap;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceCacheTest {

  private ResourceCache resourceCache;
  private ResourceCache.Configuration configuration;
  private BrokerTopologyManager brokerTopologyManager;
  private MeterRegistry meterRegistry;

  private DeployedResourceEntity entity;

  @BeforeEach
  public void setUp() {
    entity =
        Instancio.of(DeployedResourceEntity.class)
            .set(field(DeployedResourceEntity::resourceKey), 123L)
            .set(field(DeployedResourceEntity::resourceId), "test-resource")
            .set(field(DeployedResourceEntity::resourceName), "test.bpmn")
            .set(field(DeployedResourceEntity::resourceContent), "<bpmn>...</bpmn>")
            .create();

    configuration = ResourceCache.Configuration.getDefault();
    brokerTopologyManager = mock(BrokerTopologyManager.class);
    meterRegistry = new SimpleMeterRegistry();
    resourceCache = new ResourceCache(configuration, brokerTopologyManager, meterRegistry);
  }

  @AfterEach
  public void tearDown() {
    getCache().cleanUp();
    getCache().invalidateAll();
    getCache().cleanUp();
  }

  private Cache<Long, DeployedResourceEntity> getCache() {
    return resourceCache.getRawCache();
  }

  private ConcurrentMap<Long, DeployedResourceEntity> getCacheMap() {
    return getCache().asMap();
  }

  @Test
  void shouldPopulateCacheWhenPuttingResource() {
    // when
    resourceCache.put(entity.resourceKey(), entity);
    getCache().cleanUp();

    // then
    assertThat(getCacheMap()).hasSize(1);
    assertThat(getCacheMap()).containsKey(entity.resourceKey());
    assertThat(getCacheMap().get(entity.resourceKey())).isEqualTo(entity);
  }

  @Test
  void shouldReturnNullWhenResourceNotCached() {
    // when
    final var cachedEntity = resourceCache.get(entity.resourceKey());

    // then
    assertThat(cachedEntity).isNull();
  }

  @Test
  void shouldReturnCachedResourceWhenPresent() {
    // given
    resourceCache.put(entity.resourceKey(), entity);
    getCache().cleanUp();

    // when
    final var cachedEntity = resourceCache.get(entity.resourceKey());

    // then
    assertThat(cachedEntity).isNotNull();
    assertThat(cachedEntity).isEqualTo(entity);
  }

  @Test
  void shouldNotRepopulateWhenAlreadyCached() {
    // given
    resourceCache.put(entity.resourceKey(), entity);
    getCache().cleanUp();

    // when - get resource twice
    final var firstGet = resourceCache.get(entity.resourceKey());
    final var secondGet = resourceCache.get(entity.resourceKey());

    // then - same instance returned
    assertThat(firstGet).isNotNull();
    assertThat(secondGet).isNotNull();
    assertThat(firstGet).isSameAs(secondGet);
  }

  @Test
  void shouldPopulateCacheWithMultipleResources() {
    final var otherEntity =
        Instancio.of(DeployedResourceEntity.class)
            .set(field(DeployedResourceEntity::resourceKey), 456L)
            .set(field(DeployedResourceEntity::resourceId), "other-resource")
            .set(field(DeployedResourceEntity::resourceName), "other.bpmn")
            .set(field(DeployedResourceEntity::resourceContent), "<bpmn>...</bpmn>")
            .create();

    // when
    resourceCache.put(entity.resourceKey(), entity);
    resourceCache.put(otherEntity.resourceKey(), otherEntity);
    getCache().cleanUp();

    // then
    final var cacheMap = getCacheMap();
    assertThat(cacheMap).hasSize(2);
    assertThat(cacheMap).containsOnlyKeys(entity.resourceKey(), otherEntity.resourceKey());
  }

  @Test
  void shouldRemoveLeastRecentlyUsedWhenMaxSizeExceeded() {
    // given - cache with max size 2
    configuration = new Configuration(2, 100L);
    resourceCache = new ResourceCache(configuration, brokerTopologyManager, meterRegistry);

    final var entity1 =
        Instancio.of(DeployedResourceEntity.class)
            .set(field(DeployedResourceEntity::resourceKey), 1L)
            .create();
    final var entity2 =
        Instancio.of(DeployedResourceEntity.class)
            .set(field(DeployedResourceEntity::resourceKey), 2L)
            .create();
    final var entity3 =
        Instancio.of(DeployedResourceEntity.class)
            .set(field(DeployedResourceEntity::resourceKey), 3L)
            .create();

    resourceCache.put(1L, entity1);
    resourceCache.put(2L, entity2);
    getCache().cleanUp();
    assertThat(getCacheMap()).hasSize(2);

    // when - access entity1 and add entity3, entity2 should be evicted
    resourceCache.get(1L);
    resourceCache.put(3L, entity3);
    getCache().cleanUp();

    // then - entity2 should be removed
    final var cacheMap = getCacheMap();
    assertThat(cacheMap).hasSize(2);
    assertThat(cacheMap.keySet()).containsExactlyInAnyOrder(1L, 3L);
  }

  @Test
  void shouldInvalidateAllEntries() {
    // given
    resourceCache.put(entity.resourceKey(), entity);
    resourceCache.put(456L, entity);
    getCache().cleanUp();
    assertThat(getCacheMap()).hasSize(2);

    // when
    resourceCache.invalidate();
    getCache().cleanUp();

    // then
    assertThat(getCacheMap()).isEmpty();
  }
}
