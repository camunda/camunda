/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static io.camunda.zeebe.db.writebehind.TestValues.commit;
import static io.camunda.zeebe.db.writebehind.TestValues.held;
import static io.camunda.zeebe.db.writebehind.TestValues.key;
import static io.camunda.zeebe.db.writebehind.TestValues.longOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class FlushedValueCacheTest {

  private CountingRawDbAccess storage;
  private WriteBehindStore<RawKey, HeldValue, DbValue> store;

  @BeforeEach
  void setUp() {
    storage = new CountingRawDbAccess(new InMemoryRawDbAccess());
    store = new WriteBehindStore<>(new RawBackend(storage), WriteBehindConfiguration.defaults());
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void shouldServeCommittedValueFromCacheAfterItIsFlushed() {
    // given
    commit(store, key(1), held(5));
    store.awaitFlushed();

    // when
    final var value = store.get(key(1));

    // then
    assertThat(longOf(value)).isEqualTo(5);
    assertThat(storage.gets()).isZero();
  }

  @Test
  void shouldServeValueReadFromStorageFromCacheTheNextTime() {
    // given
    commit(store, key(1), held(5));
    store.awaitFlushed();
    store.close();
    store = new WriteBehindStore<>(new RawBackend(storage), WriteBehindConfiguration.defaults());
    store.get(key(1));

    // when
    final var value = store.get(key(1));

    // then
    assertThat(longOf(value)).isEqualTo(5);
    assertThat(storage.gets()).isEqualTo(1);
  }

  @Test
  void shouldNotKeepDeletedKeyInCacheAfterItIsFlushed() {
    // given
    final var registry = new SimpleMeterRegistry();
    store.exportMetrics(registry, "test");
    commit(store, key(1), held(5));
    store.awaitFlushed();
    assertThat(cacheEntries(registry)).isEqualTo(1);

    // when
    commit(store, key(1), null);
    store.awaitFlushed();

    // then
    assertThat(cacheEntries(registry)).isZero();
  }

  @Test
  void shouldReadDeletedKeyAsAbsentFromStorageAfterItIsFlushed() {
    // given
    commit(store, key(1), held(5));
    commit(store, key(1), null);
    store.awaitFlushed();

    // when
    final var value = store.get(key(1));

    // then
    assertThat(value).isNull();
    assertThat(storage.gets()).isEqualTo(1);
  }

  @Test
  void shouldNotCacheKeyThatTheStorageDoesNotHave() {
    // given
    final var registry = new SimpleMeterRegistry();
    store.exportMetrics(registry, "test");

    // when
    final var first = store.get(key(1));
    final var second = store.get(key(1));

    // then
    assertThat(first).isNull();
    assertThat(second).isNull();
    assertThat(cacheEntries(registry)).isZero();
    assertThat(storage.gets()).isEqualTo(2);
  }

  private static double cacheEntries(final SimpleMeterRegistry registry) {
    return registry.get("zeebe.writebehind.cache.entries").gauge().value();
  }
}
