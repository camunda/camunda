/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.write;

import io.camunda.zeebe.metadata.store.api.Batch;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.ConditionFailedException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.ConditionFailedException.FailedCondition;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceAlreadyExistsException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceNotFoundException;
import io.camunda.zeebe.metadata.store.api.Condition;
import io.camunda.zeebe.metadata.store.api.KeyValue;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.StoreValue;
import io.camunda.zeebe.metadata.store.api.Write;
import io.camunda.zeebe.metadata.store.impl.state.Entry.NamespaceEntry;
import io.camunda.zeebe.metadata.store.impl.state.Entry.Operation;
import io.camunda.zeebe.metadata.store.impl.state.NamespaceState;
import io.camunda.zeebe.metadata.store.impl.state.StoreState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Decides what a write does, on the single writer: the namespace operations of an entry, or none if
 * nothing changes, or an error. A batch is evaluated in order against a candidate on top of the
 * state, so each check sees the writes before it in the same batch; the state itself is never
 * changed. Only changes reach the result: a put of the bytes a key holds, a delete that finds
 * nothing and a {@link Write.Check} add nothing to it.
 */
public final class WriteEvaluator {

  private static final Comparator<Namespace> NAMESPACE_ORDER =
      Comparator.comparing(Namespace::scope).thenComparing(Namespace::name);

  private WriteEvaluator() {}

  /**
   * Evaluates a batch against the state.
   *
   * @return the changes per namespace, sorted by scope and name; empty if the batch changes nothing
   * @throws NamespaceNotFoundException if a write addresses a namespace that does not exist
   * @throws NamespaceAlreadyExistsException if the batch creates a namespace that exists
   * @throws ConditionFailedException if a condition does not hold; nothing is applied
   */
  public static List<NamespaceEntry> evaluate(final StoreState state, final Batch batch) {
    final var candidate = new Candidate(state);
    final var failed = new ArrayList<FailedCondition>();

    for (final var write : batch.writes()) {
      switch (write) {
        case final Write.CreateNamespace create -> candidate.create(create.namespace());
        case final Write.Put put -> {
          if (holds(candidate, put.key(), put.condition(), failed)) {
            candidate.put(put.key(), put.value());
          }
        }
        case final Write.Delete delete -> {
          if (holds(candidate, delete.key(), delete.condition(), failed)) {
            candidate.delete(delete.key());
          }
        }
        case final Write.DeletePrefix deletePrefix -> candidate.deletePrefix(deletePrefix.prefix());
        case final Write.Check check -> holds(candidate, check.key(), check.condition(), failed);
      }
    }

    if (!failed.isEmpty()) {
      throw new ConditionFailedException(
          "Expected every condition of the batch to hold, but it failed for %s"
              .formatted(failed.stream().map(FailedCondition::key).toList()),
          failed);
    }
    return candidate.changes();
  }

  /**
   * Evaluates the deletion of a namespace with all its keys.
   *
   * @throws NamespaceNotFoundException if the namespace does not exist
   */
  public static NamespaceEntry evaluateDeleteNamespace(
      final StoreState state, final Namespace namespace) {
    state.requireNamespace(namespace);
    return new NamespaceEntry.Deleted(namespace);
  }

  private static boolean holds(
      final Candidate candidate,
      final StoreKey key,
      final Condition condition,
      final List<FailedCondition> failed) {
    if (matches(condition, candidate.current(key))) {
      return true;
    }
    failed.add(new FailedCondition(key, candidate.published(key)));
    return false;
  }

  private static boolean matches(final Condition condition, final @Nullable StoreValue current) {
    return switch (condition) {
      case final Condition.None ignored -> true;
      case final Condition.Absent ignored -> current == null;
      case final Condition.Exists ignored -> current != null;
      case final Condition.ValueEquals equals ->
          current != null && equals.expected().equals(current);
    };
  }

  /** The state with the writes of the batch so far on top, per namespace. */
  private static final class Candidate {

    private final StoreState state;
    private final Map<Namespace, PendingNamespace> pending = new TreeMap<>(NAMESPACE_ORDER);

    private Candidate(final StoreState state) {
      this.state = state;
    }

    void create(final Namespace namespace) {
      if (state.exists(namespace) || pending.containsKey(namespace)) {
        throw new NamespaceAlreadyExistsException(
            "Expected namespace '%s' not to exist, but it does".formatted(namespace));
      }
      pending.put(namespace, new PendingNamespace(null));
    }

    @Nullable StoreValue current(final StoreKey key) {
      return namespace(key.namespace()).current(key.key());
    }

    /** The key as the published state holds it, as get returns it now; null if it holds none. */
    @Nullable KeyValue published(final StoreKey key) {
      return state.exists(key.namespace()) ? state.get(key).orElse(null) : null;
    }

    void put(final StoreKey key, final StoreValue value) {
      namespace(key.namespace()).writes.put(key.key(), value);
    }

    void delete(final StoreKey key) {
      namespace(key.namespace()).writes.put(key.key(), null);
    }

    void deletePrefix(final StoreKey prefix) {
      final var namespace = namespace(prefix.namespace());
      namespace.currentKeys(prefix.key()).forEach(key -> namespace.writes.put(key, null));
    }

    private PendingNamespace namespace(final Namespace namespace) {
      final var existing = pending.get(namespace);
      if (existing != null) {
        return existing;
      }
      final var namespaceState = state.requireNamespace(namespace);
      final var created = new PendingNamespace(namespaceState);
      pending.put(namespace, created);
      return created;
    }

    List<NamespaceEntry> changes() {
      final var changes = new ArrayList<NamespaceEntry>();
      pending.forEach(
          (namespace, writes) -> {
            final var operations = writes.operations();
            if (writes.base == null) {
              changes.add(new NamespaceEntry.Created(namespace, operations));
            } else if (!operations.isEmpty()) {
              changes.add(new NamespaceEntry.KeysChanged(namespace, operations));
            }
          });
      return changes;
    }
  }

  /**
   * The writes of the batch to one namespace, by key: a value for a put, null for a delete. The
   * base is null for a namespace that the batch creates.
   */
  private static final class PendingNamespace {

    private final @Nullable NamespaceState base;
    private final TreeMap<String, @Nullable StoreValue> writes = new TreeMap<>();

    private PendingNamespace(final @Nullable NamespaceState base) {
      this.base = base;
    }

    @Nullable StoreValue current(final String key) {
      if (writes.containsKey(key)) {
        return writes.get(key);
      }
      final var value = base == null ? null : base.get(key);
      return value == null ? null : value.current();
    }

    /**
     * The keys that hold a value at or under the prefix. The batch rules keep every other write out
     * of the range of a {@link Write.DeletePrefix}, so these are the base's keys.
     */
    Set<String> currentKeys(final String prefix) {
      return base == null ? Set.of() : base.atOrUnder(prefix).keySet();
    }

    /** The writes that change the base, in key order. */
    List<Operation> operations() {
      final var operations = new ArrayList<Operation>();
      writes.forEach(
          (key, value) -> {
            final var existing = base == null ? null : base.get(key);
            if (value == null) {
              if (existing != null) {
                operations.add(new Operation.Delete(key));
              }
            } else if (existing == null || !existing.current().equals(value)) {
              operations.add(new Operation.Put(key, value));
            }
          });
      return operations;
    }
  }
}
