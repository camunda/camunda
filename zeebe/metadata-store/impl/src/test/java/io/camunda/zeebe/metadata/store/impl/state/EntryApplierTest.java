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

import io.camunda.zeebe.metadata.store.api.KeyEvent;
import io.camunda.zeebe.metadata.store.api.KeyEvent.ChangeType;
import io.camunda.zeebe.metadata.store.api.KeyValue;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.NamespaceChange;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.StoreValue;
import io.camunda.zeebe.metadata.store.impl.state.Entry.NamespaceEntry;
import io.camunda.zeebe.metadata.store.impl.state.Entry.Operation;
import io.camunda.zeebe.metadata.store.impl.state.StoreChange.KeyChanged;
import io.camunda.zeebe.metadata.store.impl.state.StoreChange.NamespaceChanged;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Applying an entry is a pure function: it derives previous values and namespace metadata, reports
 * each change once, and shares every namespace it does not change with the previous state.
 */
final class EntryApplierTest {

  private static final Namespace CONFIG = Namespace.cluster("config");
  private static final Namespace TENANT = Namespace.tenant("tenant-a");
  private static final StoreValue V1 = StoreValue.of(new byte[] {1});
  private static final StoreValue V2 = StoreValue.of(new byte[] {2});

  @Test
  void shouldCreateNamespaceWithItsMetadata() {
    // when
    final var applied = apply(StoreState.empty(), 5, "8.11.0", 100L, created(CONFIG, put("a", V1)));

    // then
    final var namespace = applied.state().requireNamespace(CONFIG);
    assertThat(namespace.version()).isEqualTo("8.11.0");
    assertThat(namespace.incarnation()).isEqualTo(5);
    assertThat(namespace.lastUpdatedPosition()).isEqualTo(5);
    assertThat(namespace.lastUpdatedTimestamp()).isEqualTo(100L);
    assertThat(applied.state().appliedPosition()).isEqualTo(5);
    assertThat(applied.changes())
        .containsExactly(
            new NamespaceChanged(new NamespaceChange(CONFIG, NamespaceChange.ChangeType.CREATED)),
            new KeyChanged(new KeyEvent(ChangeType.ADD, key(CONFIG, "a"), V1, null)));
  }

  @Test
  void shouldMoveReplacedValueToPrevious() {
    // given
    final var state = apply(StoreState.empty(), 1, created(CONFIG, put("a", V1))).state();

    // when
    final var applied = apply(state, 2, changed(CONFIG, put("a", V2)));

    // then
    assertThat(applied.state().requireNamespace(CONFIG).get("a"))
        .isEqualTo(new KeyValue(key(CONFIG, "a"), V2, V1));
    assertThat(applied.changes())
        .containsExactly(
            new KeyChanged(new KeyEvent(ChangeType.REPLACE, key(CONFIG, "a"), V2, V1)));
  }

  @Test
  void shouldKeepNoPreviousValueOfDeletedKey() {
    // given
    var state = apply(StoreState.empty(), 1, created(CONFIG, put("a", V1))).state();
    state = apply(state, 2, changed(CONFIG, put("a", V2))).state();

    // when
    final var deleted = apply(state, 3, changed(CONFIG, delete("a")));
    final var added = apply(deleted.state(), 4, changed(CONFIG, put("a", V1)));

    // then
    assertThat(deleted.changes())
        .containsExactly(
            new KeyChanged(new KeyEvent(ChangeType.REMOVE, key(CONFIG, "a"), null, V2)));
    assertThat(added.changes())
        .containsExactly(new KeyChanged(new KeyEvent(ChangeType.ADD, key(CONFIG, "a"), V1, null)));
  }

  @Test
  void shouldUpdateMetadataOfChangedNamespacesOnly() {
    // given
    var state = apply(StoreState.empty(), 1, "8.10.0", 10L, created(CONFIG, put("a", V1))).state();
    state = apply(state, 2, "8.10.0", 20L, created(TENANT, put("a", V1))).state();

    // when
    final var applied = apply(state, 3, "8.11.0", 30L, changed(TENANT, put("a", V2)));

    // then
    final var tenant = applied.state().requireNamespace(TENANT);
    assertThat(tenant.version()).isEqualTo("8.11.0");
    assertThat(tenant.incarnation()).isEqualTo(2);
    assertThat(tenant.lastUpdatedPosition()).isEqualTo(3);
    assertThat(tenant.lastUpdatedTimestamp()).isEqualTo(30L);
    assertThat(applied.state().requireNamespace(CONFIG)).isSameAs(state.requireNamespace(CONFIG));
  }

  @Test
  void shouldRejectOperationThatChangesNothing() {
    // given
    final var state = apply(StoreState.empty(), 1, created(CONFIG, put("a", V1))).state();

    // when - then: the writer only writes operations that change their key
    assertThatThrownBy(() -> apply(state, 2, changed(CONFIG, put("a", V1))))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> apply(state, 2, changed(CONFIG, delete("missing"))))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> apply(state, 2, changed(CONFIG)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldCreateEmptyNamespaceWithOnlyItsCreation() {
    // when
    final var applied = apply(StoreState.empty(), 3, created(CONFIG));

    // then
    final var namespace = applied.state().requireNamespace(CONFIG);
    assertThat(namespace.values()).isEmpty();
    assertThat(namespace.incarnation()).isEqualTo(3);
    assertThat(namespace.lastUpdatedPosition()).isEqualTo(3);
    assertThat(applied.changes())
        .containsExactly(
            new NamespaceChanged(new NamespaceChange(CONFIG, NamespaceChange.ChangeType.CREATED)));
  }

  @Test
  void shouldDeleteNamespaceWithItsKeys() {
    // given
    final var state =
        apply(StoreState.empty(), 1, created(CONFIG, put("b", V2), put("a", V1))).state();

    // when
    final var applied = apply(state, 2, new NamespaceEntry.Deleted(CONFIG));

    // then
    assertThat(applied.state().exists(CONFIG)).isFalse();
    assertThat(applied.changes())
        .containsExactly(
            new KeyChanged(new KeyEvent(ChangeType.REMOVE, key(CONFIG, "a"), null, V1)),
            new KeyChanged(new KeyEvent(ChangeType.REMOVE, key(CONFIG, "b"), null, V2)),
            new NamespaceChanged(new NamespaceChange(CONFIG, NamespaceChange.ChangeType.DELETED)));
  }

  @Test
  void shouldGiveRecreatedNamespaceNewIncarnation() {
    // given
    var state = apply(StoreState.empty(), 1, created(CONFIG)).state();
    state = apply(state, 2, new NamespaceEntry.Deleted(CONFIG)).state();

    // when
    final var applied = apply(state, 3, created(CONFIG));

    // then
    assertThat(applied.state().requireNamespace(CONFIG).incarnation()).isEqualTo(3);
  }

  @Test
  void shouldLeavePreviousStateUnchanged() {
    // given
    final var state = apply(StoreState.empty(), 1, created(CONFIG, put("a", V1))).state();

    // when
    apply(state, 2, changed(CONFIG, put("a", V2), put("b", V2)));

    // then
    assertThat(state.appliedPosition()).isEqualTo(1);
    assertThat(state.requireNamespace(CONFIG).values()).containsOnlyKeys("a");
    assertThat(state.requireNamespace(CONFIG).get("a"))
        .isEqualTo(new KeyValue(key(CONFIG, "a"), V1, null));
  }

  @Test
  void shouldRejectKeyOperationInUnknownNamespace() {
    // when - then
    assertThatThrownBy(() -> apply(StoreState.empty(), 1, changed(CONFIG, put("a", V1))))
        .isInstanceOf(IllegalStateException.class);
  }

  private static EntryApplier.Applied apply(
      final StoreState state, final long position, final NamespaceEntry... namespaces) {
    return apply(state, position, "8.11.0", position, namespaces);
  }

  private static EntryApplier.Applied apply(
      final StoreState state,
      final long position,
      final String version,
      final long timestamp,
      final NamespaceEntry... namespaces) {
    return EntryApplier.apply(state, position, new Entry(version, timestamp, List.of(namespaces)));
  }

  private static NamespaceEntry created(final Namespace namespace, final Operation... operations) {
    return new NamespaceEntry.Created(namespace, List.of(operations));
  }

  private static NamespaceEntry changed(final Namespace namespace, final Operation... operations) {
    return new NamespaceEntry.KeysChanged(namespace, List.of(operations));
  }

  private static Operation put(final String key, final StoreValue value) {
    return new Operation.Put(key, value);
  }

  private static Operation delete(final String key) {
    return new Operation.Delete(key);
  }

  private static StoreKey key(final Namespace namespace, final String key) {
    return StoreKey.of(namespace, key);
  }
}
