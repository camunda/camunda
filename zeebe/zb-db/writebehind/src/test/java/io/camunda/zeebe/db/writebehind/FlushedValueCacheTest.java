/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class FlushedValueCacheTest {

  private CountingRawDbAccess storage;
  private WriteBehindStore<RawKey, DbValue> store;

  @BeforeEach
  void setUp() {
    storage = new CountingRawDbAccess(new InMemoryRawDbAccess());
    store =
        new WriteBehindStore<>(
            new RawBackend(storage),
            WriteBehindConfiguration.defaults().withKeyIndexColumnFamilies(Set.of()));
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void shouldServeCommittedValueFromCacheAfterItIsFlushed() {
    // given
    store.commit(new RawKey[] {key(1)}, new DbValue[] {valueOf(5)});
    store.awaitFlushed();

    // when
    final var value = store.get(key(1), new DbLong());

    // then
    assertThat(((DbLong) value).getValue()).isEqualTo(5);
    assertThat(storage.gets()).isZero();
  }

  @Test
  void shouldNotKeepDeletedKeyInCacheAfterItIsFlushed() {
    // given
    final var registry = new SimpleMeterRegistry();
    store.exportMetrics(registry, "test");
    store.commit(new RawKey[] {key(1)}, new DbValue[] {valueOf(5)});
    store.awaitFlushed();
    assertThat(cacheEntries(registry)).isEqualTo(1);

    // when
    store.commit(new RawKey[] {key(1)}, new DbValue[1]);
    store.awaitFlushed();

    // then
    assertThat(cacheEntries(registry)).isZero();
  }

  @Test
  void shouldReadDeletedKeyAsAbsentFromStorageAfterItIsFlushed() {
    // given
    store.commit(new RawKey[] {key(1)}, new DbValue[] {valueOf(5)});
    store.commit(new RawKey[] {key(1)}, new DbValue[1]);
    store.awaitFlushed();

    // when
    final var value = store.get(key(1), new DbLong());

    // then
    assertThat(value).isNull();
    assertThat(storage.gets()).isEqualTo(1);
  }

  private static double cacheEntries(final SimpleMeterRegistry registry) {
    return registry.get("zeebe.writebehind.cache.entries").gauge().value();
  }

  private static RawKey key(final long id) {
    final var buffer = ByteBuffer.allocate(2 * Long.BYTES).order(ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    return RawKey.of(buffer.putLong(7).putLong(id).array());
  }

  private static DbLong valueOf(final long value) {
    final var result = new DbLong();
    result.wrapLong(value);
    return result;
  }
}
