/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/** Write path (staging/promote/discard) and point-read resolution across the layers. */
final class LayeredKeyValueStoreTest {

  private static final String STORE = "cf";

  private final InMemoryDurableState state = new InMemoryDurableState();

  private LayeredKeyValueStore newStore() {
    return newStore(state.store(STORE));
  }

  private LayeredKeyValueStore newStore(final BytesStore delegate) {
    return new LayeredKeyValueStore(STORE, delegate, 1024 * 1024, false, 4);
  }

  @Test
  void shouldRejectInvalidConstructorArguments() {
    // given
    final BytesStore delegate = state.store(STORE);

    // when / then
    assertThatThrownBy(() -> new LayeredKeyValueStore(STORE, delegate, 0, false, 4))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxBytes");
    assertThatThrownBy(() -> new LayeredKeyValueStore(STORE, delegate, 1024, false, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pipelineSegmentLimit");
  }

  @Test
  void shouldReadOwnWritesFromStagingBeforePromote() {
    // given
    final LayeredKeyValueStore store = newStore();

    // when
    store.put(bytes("key"), bytes("value"));

    // then
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("value"));
    assertThat(store.exists(bytes("key"))).isTrue();
    assertThat(state.committedSize(STORE)).isZero();
  }

  @Test
  void shouldReturnNullForAbsentKey() {
    // given
    final LayeredKeyValueStore store = newStore();

    // when / then
    assertThat(store.get(bytes("missing"))).isNull();
    assertThat(store.exists(bytes("missing"))).isFalse();
  }

  @Test
  void shouldDiscardOnlyStaging() {
    // given -- an earlier batch already promoted key1
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("key1"), bytes("committed"));
    store.promote();

    // when -- the next batch overwrites key1, adds key2, then fails
    store.put(bytes("key1"), bytes("dirty"));
    store.put(bytes("key2"), bytes("dirty"));
    store.discard();

    // then -- the failed batch is gone, the earlier batch's state is untouched
    assertThat(store.get(bytes("key1"))).isEqualTo(bytes("committed"));
    assertThat(store.get(bytes("key2"))).isNull();
  }

  @Test
  void shouldDiscardStagingTombstone() {
    // given
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("key"), bytes("committed"));
    store.promote();

    // when
    store.delete(bytes("key"));
    store.discard();

    // then
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("committed"));
  }

  @Test
  void shouldSupersedeActiveEntryOnPromote() {
    // given
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("key"), bytes("old"));
    store.promote();

    // when
    store.put(bytes("key"), bytes("new"));
    store.promote();

    // then
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("new"));
  }

  @Test
  void shouldHideDelegateValueWithStagingTombstone() {
    // given
    state.store(STORE).put(bytes("key"), bytes("committed"));
    final LayeredKeyValueStore store = newStore();

    // when
    store.delete(bytes("key"));

    // then -- hidden for reads, but the delegate itself is untouched
    assertThat(store.get(bytes("key"))).isNull();
    assertThat(store.exists(bytes("key"))).isFalse();
    assertThat(scanAll(store)).doesNotContainKey("key");
    assertThat(state.committedValue(STORE, bytes("key"))).isEqualTo(bytes("committed"));
  }

  @Test
  void shouldHideDelegateValueWithActiveTombstone() {
    // given
    state.store(STORE).put(bytes("key"), bytes("committed"));
    final LayeredKeyValueStore store = newStore();

    // when
    store.delete(bytes("key"));
    store.promote();

    // then
    assertThat(store.get(bytes("key"))).isNull();
    assertThat(store.exists(bytes("key"))).isFalse();
    assertThat(scanAll(store)).doesNotContainKey("key");
  }

  @Test
  void shouldHideDelegateValueWithPipelineTombstone() {
    // given
    state.store(STORE).put(bytes("key"), bytes("committed"));
    final LayeredKeyValueStore store = newStore();

    // when
    store.delete(bytes("key"));
    store.promote();
    store.freeze(1L);

    // then
    assertThat(store.get(bytes("key"))).isNull();
    assertThat(store.exists(bytes("key"))).isFalse();
    assertThat(scanAll(store)).doesNotContainKey("key");
  }

  @Test
  void shouldAgreeAcrossGetExistsAndScan() {
    // given -- one live key per layer plus a tombstoned one
    state.store(STORE).put(bytes("delegate"), bytes("v1"));
    state.store(STORE).put(bytes("gone"), bytes("v2"));
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("pipeline"), bytes("v3"));
    store.promote();
    store.freeze(1L);
    store.put(bytes("active"), bytes("v4"));
    store.promote();
    store.put(bytes("staging"), bytes("v5"));
    store.delete(bytes("gone"));

    // when
    final Map<String, String> scanned = scanAll(store);

    // then
    for (final String visible : new String[] {"delegate", "pipeline", "active", "staging"}) {
      assertThat(store.exists(bytes(visible))).as(visible).isTrue();
      assertThat(store.get(bytes(visible))).as(visible).isNotNull();
      assertThat(scanned).as(visible).containsKey(visible);
    }
    assertThat(store.exists(bytes("gone"))).isFalse();
    assertThat(store.get(bytes("gone"))).isNull();
    assertThat(scanned).doesNotContainKey("gone");
  }

  @Test
  void shouldPopulateCleanCacheOnReadThrough() {
    // given
    state.store(STORE).put(bytes("key"), bytes("committed"));
    final CountingBytesStore counting = new CountingBytesStore(state.store(STORE));
    final LayeredKeyValueStore store = newStore(counting);

    // when
    final byte[] first = store.get(bytes("key"));
    final byte[] second = store.get(bytes("key"));

    // then -- the second read is served by the clean cache
    assertThat(first).isEqualTo(bytes("committed"));
    assertThat(second).isEqualTo(bytes("committed"));
    assertThat(counting.gets).isEqualTo(1);
  }

  // replaces shouldNotCacheDelegateMisses: misses are now cached as negatives, so repeated reads
  // of an absent key cost one delegate probe instead of one per read
  @Test
  void shouldCacheDelegateMissAndSkipDelegateOnRepeatedReads() {
    // given
    final CountingBytesStore counting = new CountingBytesStore(state.store(STORE));
    final LayeredKeyValueStore store = newStore(counting);

    // when -- the first miss caches a negative; later reads hit it without a delegate probe
    final byte[] first = store.get(bytes("missing"));
    final byte[] second = store.get(bytes("missing"));
    final boolean exists = store.exists(bytes("missing"));

    // then -- one probe total, and the negative is accounted at key bytes only
    assertThat(first).isNull();
    assertThat(second).isNull();
    assertThat(exists).isFalse();
    assertThat(counting.gets).isEqualTo(1);
    assertThat(store.approximateBytes()).isEqualTo("missing".length());
  }

  @Test
  void shouldShadowCleanCacheWithNewerWrite() {
    // given -- a delegate-backed clean entry
    state.store(STORE).put(bytes("key"), bytes("committed"));
    final LayeredKeyValueStore store = newStore();
    store.get(bytes("key"));

    // when
    store.put(bytes("key"), bytes("newer"));

    // then
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("newer"));
    assertThat(scanAll(store)).containsEntry("key", "newer");
  }

  @Test
  void shouldShadowCachedNegativeWithNewerWrite() {
    // given -- a read miss cached a negative for the key
    final LayeredKeyValueStore store = newStore();
    assertThat(store.get(bytes("key"))).isNull();

    // when -- the write evicts the sentinel (the write shadows it)
    store.put(bytes("key"), bytes("value"));

    // then
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("value"));
    assertThat(store.exists(bytes("key"))).isTrue();
    assertThat(scanAll(store)).containsEntry("key", "value");
  }

  private static Map<String, String> scanAll(final LayeredKeyValueStore store) {
    final Map<String, String> result = new LinkedHashMap<>();
    store.forEach((key, value) -> result.put(new String(key, UTF_8), new String(value, UTF_8)));
    return result;
  }

  private static byte[] bytes(final String value) {
    return value.getBytes(UTF_8);
  }

  /** Counts delegate point reads, to prove which reads the clean cache absorbs. */
  private static final class CountingBytesStore implements BytesStore {

    private final BytesStore delegate;
    private int gets;

    private CountingBytesStore(final BytesStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public byte[] get(final byte[] key) {
      gets++;
      return delegate.get(key);
    }

    @Override
    public void put(final byte[] key, final byte[] value) {
      delegate.put(key, value);
    }

    @Override
    public void delete(final byte[] key) {
      delegate.delete(key);
    }

    @Override
    public void prefixScan(final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
      delegate.prefixScan(prefix, visitor);
    }
  }
}
