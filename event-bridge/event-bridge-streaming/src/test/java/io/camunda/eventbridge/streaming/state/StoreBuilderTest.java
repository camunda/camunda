/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.eventbridge.streaming.state.api.Checkpointable;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.cache.CachingKeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryStateStoreProvider;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import org.junit.jupiter.api.Test;

final class StoreBuilderTest {

  private final InMemoryStateStoreProvider<TestColumnFamilies> provider =
      new InMemoryStateStoreProvider<>();

  @Test
  void shouldMaterializeAPlainStoreWithoutCaching() {
    // when
    final KeyValueStore<DbLong, DbString> store =
        StoreBuilder.keyValueStore(TestColumnFamilies.KV, DbLong::new, DbString::new)
            .build(provider);

    // then — the delegate itself, not a cache wrapper
    assertThat(store).isNotInstanceOf(CachingKeyValueStore.class);
    final DbLong key = new DbLong();
    key.wrapLong(1L);
    final DbString value = new DbString();
    value.wrapString("v");
    store.put(key, value);
    assertThat(store.get(key)).hasValueSatisfying(v -> assertThat(v).hasToString("v"));
  }

  @Test
  void shouldWrapInABoundedCacheWhenCachingIsEnabled() {
    // when
    final KeyValueStore<DbLong, DbString> store =
        StoreBuilder.keyValueStore(TestColumnFamilies.CELLS, DbLong::new, DbString::new)
            .withCaching(1024)
            .build(provider);

    // then — a checkpointable caching store
    assertThat(store).isInstanceOf(CachingKeyValueStore.class).isInstanceOf(Checkpointable.class);
  }

  @Test
  void shouldRejectNonPositiveCacheBudget() {
    assertThatThrownBy(
            () ->
                StoreBuilder.keyValueStore(TestColumnFamilies.KV, DbLong::new, DbString::new)
                    .withCaching(0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
