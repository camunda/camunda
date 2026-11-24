/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.state;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Applies one entry to a state: a pure function from the state, the entry and its position to the
 * next state and the changes it made. A namespace that the entry does not change is shared with the
 * previous state; one that changes is copied once, however many of its keys change. One instance
 * applies one entry.
 */
public final class EntryApplier {

  private final Map<Namespace, NamespaceState> namespaces;
  private final List<StoreChange> changes = new ArrayList<>();
  private final Entry entry;
  private final long position;

  private EntryApplier(final StoreState state, final long position, final Entry entry) {
    namespaces = new HashMap<>(state.namespaces());
    this.entry = entry;
    this.position = position;
  }

  /**
   * Returns the state after the entry and the changes it made, in the order of the entry.
   *
   * @throws IllegalStateException if the entry does not fit the state, for example a key operation
   *     in a namespace that does not exist, or one that changes nothing; the single writer never
   *     writes such an entry
   */
  public static Applied apply(final StoreState state, final long position, final Entry entry) {
    final var applier = new EntryApplier(state, position, entry);
    entry.namespaces().forEach(applier::apply);
    return new Applied(new StoreState(position, applier.namespaces), applier.changes);
  }

  private void apply(final NamespaceEntry namespaceEntry) {
    switch (namespaceEntry) {
      case final NamespaceEntry.Created created -> {
        createNamespace(created.namespace());
        changeKeys(created.namespace(), created.operations());
      }
      case final NamespaceEntry.Deleted deleted -> deleteNamespace(deleted.namespace());
      case final NamespaceEntry.KeysChanged keysChanged ->
          changeKeys(keysChanged.namespace(), keysChanged.operations());
    }
  }

  private void createNamespace(final Namespace namespace) {
    if (namespaces.containsKey(namespace)) {
      throw new IllegalStateException(
          "Expected namespace '%s' not to exist to create it at position %d, but it does"
              .formatted(namespace, position));
    }
    namespaces.put(
        namespace,
        new NamespaceState(
            new TreeMap<>(), entry.producerVersion(), position, entry.timestamp(), position));
    changes.add(
        new NamespaceChanged(new NamespaceChange(namespace, NamespaceChange.ChangeType.CREATED)));
  }

  /** Removes the namespace: a removal of each of its keys, in key order, then the deletion. */
  private void deleteNamespace(final Namespace namespace) {
    final var removed = requireNamespace(namespace, "delete");
    namespaces.remove(namespace);
    removed
        .values()
        .values()
        .forEach(
            pair ->
                changes.add(
                    new KeyChanged(
                        new KeyEvent(ChangeType.REMOVE, pair.key(), null, pair.current()))));
    changes.add(
        new NamespaceChanged(new NamespaceChange(namespace, NamespaceChange.ChangeType.DELETED)));
  }

  /**
   * Applies the operations to the namespace. The writer only writes operations that change their
   * key, so each one gives exactly one event.
   */
  private void changeKeys(final Namespace namespace, final List<Operation> operations) {
    final var current = requireNamespace(namespace, "change");
    final var values = new TreeMap<>(current.values());
    for (final var operation : operations) {
      final var key = StoreKey.of(namespace, operation.key());
      final var event =
          switch (operation) {
            case final Operation.Put put -> putKey(values, key, put.value());
            case final Operation.Delete ignored -> deleteKey(values, key);
          };
      changes.add(new KeyChanged(event));
    }
    namespaces.put(
        namespace,
        new NamespaceState(
            values, entry.producerVersion(), current.incarnation(), entry.timestamp(), position));
  }

  private KeyEvent putKey(
      final TreeMap<String, KeyValue> values, final StoreKey key, final StoreValue value) {
    final var existing = values.get(key.key());
    if (existing == null) {
      values.put(key.key(), new KeyValue(key, value, null));
      return new KeyEvent(ChangeType.ADD, key, value, null);
    }
    if (existing.current().equals(value)) {
      throw new IllegalStateException(
          "Expected the put of '%s' at position %d to change its value, but it holds these bytes"
              .formatted(key, position));
    }
    values.put(key.key(), new KeyValue(key, value, existing.current()));
    return new KeyEvent(ChangeType.REPLACE, key, value, existing.current());
  }

  private KeyEvent deleteKey(final TreeMap<String, KeyValue> values, final StoreKey key) {
    final var existing = values.remove(key.key());
    if (existing == null) {
      throw new IllegalStateException(
          "Expected '%s' to exist to delete it at position %d, but it does not"
              .formatted(key, position));
    }
    return new KeyEvent(ChangeType.REMOVE, key, null, existing.current());
  }

  private NamespaceState requireNamespace(final Namespace namespace, final String action) {
    final var state = namespaces.get(namespace);
    if (state == null) {
      throw new IllegalStateException(
          "Expected namespace '%s' to exist to %s it at position %d, but it does not"
              .formatted(namespace, action, position));
    }
    return state;
  }

  /** The state after an entry and the changes it made. */
  public record Applied(StoreState state, List<StoreChange> changes) {}
}
