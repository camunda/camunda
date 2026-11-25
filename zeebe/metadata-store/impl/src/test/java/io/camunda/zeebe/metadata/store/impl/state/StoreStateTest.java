/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceNotFoundException;
import io.camunda.zeebe.metadata.store.api.KeyValue;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.StoreValue;
import io.camunda.zeebe.metadata.store.impl.state.Entry.NamespaceEntry;
import io.camunda.zeebe.metadata.store.impl.state.Entry.Operation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Reads of a state: get is exact, list is one range on whole segments, arrays are copies. */
final class StoreStateTest {

  private static final Namespace NAMESPACE = Namespace.cluster("config");
  private static final List<String> KEYS = List.of("a", "a.b", "a.b.c", "a.bc", "a-b", "b");
  private static final StoreState STATE = stateWith(KEYS);

  @ParameterizedTest(name = "list({0})")
  @MethodSource("ranges")
  void shouldListKeysAtOrUnderPrefixOnWholeSegments(
      final String prefix, final List<String> expected) {
    // when
    final var listed = STATE.list(StoreKey.of(NAMESPACE, prefix));

    // then
    assertThat(listed).extracting(pair -> pair.key().key()).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> ranges() {
    return Stream.of(
        Arguments.of("", List.of("a", "a-b", "a.b", "a.b.c", "a.bc", "b")),
        Arguments.of("a", List.of("a", "a.b", "a.b.c", "a.bc")),
        Arguments.of("a.b", List.of("a.b", "a.b.c")),
        Arguments.of("a.b.c", List.of("a.b.c")),
        Arguments.of("a.b.c.d", List.of()),
        Arguments.of("c", List.of()));
  }

  @Test
  void shouldGetExactKeyOnly() {
    // when - then
    assertThat(STATE.get(StoreKey.of(NAMESPACE, "a.b")))
        .map(KeyValue::current)
        .contains(bytes("a.b"));
    assertThat(STATE.get(StoreKey.of(NAMESPACE, "a.b.c.d"))).isEmpty();
  }

  @Test
  void shouldRejectReadOfUnknownNamespace() {
    // given
    final var key = StoreKey.of(Namespace.cluster("unknown"), "a");

    // when - then
    assertThatThrownBy(() -> STATE.get(key)).isInstanceOf(NamespaceNotFoundException.class);
    assertThatThrownBy(() -> STATE.list(key)).isInstanceOf(NamespaceNotFoundException.class);
  }

  @Test
  void shouldNotLetCallerChangePublishedState() {
    // when - then: values are immutable, so only the maps could be changed
    assertThatThrownBy(() -> STATE.requireNamespace(NAMESPACE).values().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldKeepPublishedStateWhenCallerChangesItsMap() {
    // given
    final var values = new TreeMap<String, KeyValue>();
    final var key = StoreKey.of(NAMESPACE, "a");
    values.put("a", new KeyValue(key, bytes("a"), null));
    final var state =
        new StoreState(1, Map.of(NAMESPACE, new NamespaceState(values, "8.11.0", 1, 1L, 1)));

    // when
    values.clear();

    // then
    assertThat(state.get(key)).hasValue(new KeyValue(key, bytes("a"), null));
  }

  private static StoreState stateWith(final List<String> keys) {
    final var operations =
        keys.stream().<Operation>map(key -> new Operation.Put(key, bytes(key))).toList();
    final var entry =
        new Entry("8.11.0", 1L, List.of(new NamespaceEntry.Created(NAMESPACE, operations)));
    return EntryApplier.apply(StoreState.empty(), 1, entry).state();
  }

  private static StoreValue bytes(final String value) {
    return StoreValue.of(value.getBytes(StandardCharsets.UTF_8));
  }
}
