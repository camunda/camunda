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

import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Merged prefix scans across staging, active, pipeline segments and the delegate stream. */
final class LayeredKeyValueStoreScanTest {

  private static final String STORE = "cf";

  private final InMemoryDurableState state = new InMemoryDurableState();

  private LayeredKeyValueStore newStore() {
    return new LayeredKeyValueStore(STORE, state.store(STORE), 1024 * 1024, false, 10);
  }

  @Test
  void shouldMergeAllLayersInKeyOrder() {
    // given -- keys interleaved across delegate, two segments, active and staging
    state.store(STORE).put(bytes("a1"), bytes("delegate"));
    state.store(STORE).put(bytes("a6"), bytes("delegate"));
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("a2"), bytes("segment1"));
    store.promote();
    store.freeze(1L);
    store.put(bytes("a4"), bytes("segment2"));
    store.promote();
    store.freeze(2L);
    store.put(bytes("a3"), bytes("active"));
    store.promote();
    store.put(bytes("a5"), bytes("staging"));

    // when
    final Map<String, String> scanned = scan(store, "");

    // then -- every layer contributes, in unsigned key order
    assertThat(scanned.keySet()).containsExactly("a1", "a2", "a3", "a4", "a5", "a6");
    assertThat(scanned)
        .containsEntry("a1", "delegate")
        .containsEntry("a2", "segment1")
        .containsEntry("a3", "active")
        .containsEntry("a4", "segment2")
        .containsEntry("a5", "staging")
        .containsEntry("a6", "delegate");
  }

  @Test
  void shouldShadowLowerLayersOnEqualKeys() {
    // given -- the same key with a distinct value on every layer
    state.store(STORE).put(bytes("key"), bytes("delegate"));
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("key"), bytes("segment"));
    store.promote();
    store.freeze(1L);
    store.put(bytes("key"), bytes("active"));
    store.promote();
    store.put(bytes("key"), bytes("staging"));

    // when
    final Map<String, String> scanned = scan(store, "");

    // then -- emitted exactly once, topmost version wins
    assertThat(scanned).hasSize(1).containsEntry("key", "staging");
  }

  @Test
  void shouldShadowOlderSegmentWithNewerSegment() {
    // given
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("key"), bytes("old"));
    store.promote();
    store.freeze(1L);
    store.put(bytes("key"), bytes("new"));
    store.promote();
    store.freeze(2L);

    // when
    final Map<String, String> scanned = scan(store, "");

    // then
    assertThat(scanned).hasSize(1).containsEntry("key", "new");
  }

  @Test
  void shouldSuppressTombstonedKeys() {
    // given -- tombstones at different heights over lower live versions
    state.store(STORE).put(bytes("delegateKilledByStaging"), bytes("v"));
    state.store(STORE).put(bytes("delegateKilledBySegment"), bytes("v"));
    final LayeredKeyValueStore store = newStore();
    store.delete(bytes("delegateKilledBySegment"));
    store.put(bytes("segmentKilledByActive"), bytes("v"));
    store.promote();
    store.freeze(1L);
    store.delete(bytes("segmentKilledByActive"));
    store.promote();
    store.delete(bytes("delegateKilledByStaging"));
    store.put(bytes("survivor"), bytes("v"));

    // when
    final Map<String, String> scanned = scan(store, "");

    // then
    assertThat(scanned.keySet()).containsExactly("survivor");
  }

  @Test
  void shouldHonorPrefixBounds() {
    // given -- keys inside and outside the prefix, spread over the layers
    state.store(STORE).put(bytes("a-delegate"), bytes("v"));
    state.store(STORE).put(bytes("b-delegate"), bytes("v"));
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("a-segment"), bytes("v"));
    store.put(bytes("b-segment"), bytes("v"));
    store.promote();
    store.freeze(1L);
    store.put(bytes("a-staging"), bytes("v"));
    store.put(bytes("b-staging"), bytes("v"));

    // when
    final Map<String, String> scanned = scan(store, "a-");

    // then
    assertThat(scanned.keySet()).containsExactly("a-delegate", "a-segment", "a-staging");
  }

  @Test
  void shouldScanWhilePersistRoundIsOutstanding() {
    // given -- one segment handed to an outstanding persist round, one frozen after
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("persisting"), bytes("v1"));
    store.promote();
    store.freeze(1L);
    store.beginPersist();
    store.put(bytes("fresh"), bytes("v2"));
    store.promote();
    store.freeze(2L);

    // when
    final Map<String, String> scanned = scan(store, "");

    // then -- persisting segments stay readable until the round completes
    assertThat(scanned).containsEntry("persisting", "v1").containsEntry("fresh", "v2").hasSize(2);
    assertThat(store.get(bytes("persisting"))).isEqualTo(bytes("v1"));
  }

  @Test
  void shouldVisitCleanCachedKeyExactlyOnce() {
    // given -- a read-through populated the clean cache with a delegate entry
    state.store(STORE).put(bytes("key"), bytes("committed"));
    final LayeredKeyValueStore store = newStore();
    store.get(bytes("key"));

    // when
    final List<String> visited = new ArrayList<>();
    store.forEach((key, value) -> visited.add(new String(key, UTF_8)));

    // then -- the clean cache is not merged, so the delegate stream is the only source
    assertThat(visited).containsExactly("key");
  }

  @Test
  void shouldScanEverythingWithForEach() {
    // given
    state.store(STORE).put(bytes("delegate"), bytes("v"));
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("staging"), bytes("v"));

    // when
    final Map<String, String> viaForEach = new LinkedHashMap<>();
    store.forEach((key, value) -> viaForEach.put(new String(key, UTF_8), new String(value, UTF_8)));

    // then
    assertThat(viaForEach).isEqualTo(scan(store, ""));
  }

  @Test
  void shouldScanEmptyStore() {
    // given
    final LayeredKeyValueStore store = newStore();

    // when / then
    assertThat(scan(store, "")).isEmpty();
  }

  private static Map<String, String> scan(final LayeredKeyValueStore store, final String prefix) {
    final Map<String, String> result = new LinkedHashMap<>();
    store.prefixScan(
        bytes(prefix),
        (key, value) -> result.put(new String(key, UTF_8), new String(value, UTF_8)));
    return result;
  }

  private static byte[] bytes(final String value) {
    return value.getBytes(UTF_8);
  }
}
