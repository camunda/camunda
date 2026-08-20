/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RdbmsEntityCacheLoaderTest {

  @Test
  void shouldLoadSingleEntity() throws Exception {
    // given
    final var entities = Map.of(1L, new TestEntity(1L, "order-process"));
    final var loader =
        new RdbmsEntityCacheLoader<Long, TestEntity, CachedTestEntity, TestBulkQuery>(
            "Process",
            key -> Optional.ofNullable(entities.get(key)),
            TestEntity::key,
            entity -> new CachedTestEntity(entity.name()));

    // when
    final var cachedEntity = loader.load(1L);

    // then
    assertThat(cachedEntity).isEqualTo(new CachedTestEntity("order-process"));
  }

  @Test
  void shouldReturnNullWhenEntityDoesNotExist() throws Exception {
    // given
    final var loader =
        new RdbmsEntityCacheLoader<Long, TestEntity, CachedTestEntity, TestBulkQuery>(
            "Process",
            key -> Optional.empty(),
            TestEntity::key,
            entity -> new CachedTestEntity(entity.name()));

    // when
    final var cachedEntity = loader.load(42L);

    // then
    assertThat(cachedEntity).isNull();
  }

  @Test
  void shouldLoadAllEntitiesWithBulkQueryWhenConfigured() throws Exception {
    // given
    final var entities =
        Map.of(
            1L, new TestEntity(1L, "order-process"),
            2L, new TestEntity(2L, "invoice-process"));
    final var loader =
        new RdbmsEntityCacheLoader<Long, TestEntity, CachedTestEntity, TestBulkQuery>(
            "Process",
            key -> Optional.ofNullable(entities.get(key)),
            TestBulkQuery::new,
            query -> query.keys().stream().map(entities::get).filter(Objects::nonNull).toList(),
            TestEntity::key,
            entity -> new CachedTestEntity(entity.name()));

    // when
    final var cachedEntities = loader.loadAll(Set.of(1L, 2L, 3L));

    // then
    assertThat(cachedEntities)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                1L, new CachedTestEntity("order-process"),
                2L, new CachedTestEntity("invoice-process")));
  }

  private record TestBulkQuery(Set<? extends Long> keys) {}

  private record TestEntity(Long key, String name) {}

  private record CachedTestEntity(String name) {}
}
