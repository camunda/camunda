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
import io.camunda.zeebe.metadata.store.api.Write;
import io.camunda.zeebe.metadata.store.impl.state.Entry.NamespaceEntry;
import io.camunda.zeebe.metadata.store.impl.state.Entry.Operation;
import io.camunda.zeebe.metadata.store.impl.state.StoreState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Decides what a write does, on the single writer: the namespace operations of an entry, or none if
 * nothing changes, or an error. {@link Batch} addresses each key at most once and keeps every other
 * write out of the range of a {@link Write.DeletePrefix}, so every write is checked against the
 * published value; only a namespace created earlier in the batch is visible to the writes after it.
 * The state itself is never changed. Only changes reach the result: a put of the bytes a key holds,
 * a delete that finds nothing and a {@link Write.Check} add nothing to it.
 *
 * <p>Namespace errors and failed conditions are reported differently: a namespace that does not
 * exist, or exists when the batch creates it, ends the evaluation at the write that addresses it,
 * since retrying cannot fix it. Failed conditions are collected over the whole batch, so that a
 * {@link ConditionFailedException} lists the current value of every failed key for a retry. A
 * namespace error therefore wins over failed conditions earlier in the batch.
 *
 * <p>Deleting a namespace is not a {@link Write}, so {@link #evaluateDeleteNamespace} decides it
 * here too: every store, in memory or on Raft, takes all its write decisions from this class.
 */
public final class WriteEvaluator {

  private static final Comparator<Namespace> NAMESPACE_ORDER =
      Comparator.comparing(Namespace::scope).thenComparing(Namespace::name);

  private final StoreState state;
  private final Set<Namespace> created = new HashSet<>();
  private final TreeMap<Namespace, TreeMap<String, Operation>> operations =
      new TreeMap<>(NAMESPACE_ORDER);
  private final List<FailedCondition> failed = new ArrayList<>();

  private WriteEvaluator(final StoreState state) {
    this.state = state;
  }

  /**
   * Evaluates a batch against the state.
   *
   * @return the changes per namespace, sorted by scope and name, each with its operations in key
   *     order; empty if the batch changes nothing
   * @throws NamespaceNotFoundException if a write addresses a namespace that does not exist
   * @throws NamespaceAlreadyExistsException if the batch creates a namespace that exists
   * @throws ConditionFailedException if a condition does not hold; nothing is applied
   */
  public static List<NamespaceEntry> evaluate(final StoreState state, final Batch batch) {
    final var evaluator = new WriteEvaluator(state);
    batch.writes().forEach(evaluator::evaluate);
    return evaluator.result();
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

  private void evaluate(final Write write) {
    switch (write) {
      case final Write.CreateNamespace create -> create(create.namespace());
      case final Write.Put put -> {
        final var current = current(put.key());
        if (holds(put.key(), put.condition(), current)
            && (current == null || !current.current().equals(put.value()))) {
          add(put.key().namespace(), new Operation.Put(put.key().key(), put.value()));
        }
      }
      case final Write.Delete delete -> {
        final var current = current(delete.key());
        if (holds(delete.key(), delete.condition(), current) && current != null) {
          add(delete.key().namespace(), new Operation.Delete(delete.key().key()));
        }
      }
      case final Write.DeletePrefix deletePrefix -> {
        final var prefix = deletePrefix.prefix();
        if (!created.contains(prefix.namespace())) {
          state
              .list(prefix)
              .forEach(pair -> add(prefix.namespace(), new Operation.Delete(pair.key().key())));
        }
      }
      case final Write.Check check -> holds(check.key(), check.condition(), current(check.key()));
    }
  }

  private void create(final Namespace namespace) {
    if (state.exists(namespace)) {
      throw new NamespaceAlreadyExistsException(
          "Expected namespace '%s' not to exist, but it does".formatted(namespace));
    }
    created.add(namespace);
    operations.put(namespace, new TreeMap<>());
  }

  /**
   * The pair the key holds when the batch starts; null in a namespace the batch created.
   *
   * @throws NamespaceNotFoundException if the namespace neither exists nor was created earlier
   */
  private @Nullable KeyValue current(final StoreKey key) {
    return created.contains(key.namespace()) ? null : state.get(key).orElse(null);
  }

  private boolean holds(
      final StoreKey key, final Condition condition, final @Nullable KeyValue current) {
    final var value = current == null ? null : current.current();
    final var holds =
        switch (condition) {
          case final Condition.None ignored -> true;
          case final Condition.Absent ignored -> value == null;
          case final Condition.Exists ignored -> value != null;
          case final Condition.ValueEquals equals ->
              value != null && equals.expected().equals(value);
        };
    if (!holds) {
      failed.add(new FailedCondition(key, current));
    }
    return holds;
  }

  private void add(final Namespace namespace, final Operation operation) {
    operations
        .computeIfAbsent(namespace, ignored -> new TreeMap<>())
        .put(operation.key(), operation);
  }

  private List<NamespaceEntry> result() {
    if (!failed.isEmpty()) {
      throw new ConditionFailedException(
          "Expected every condition of the batch to hold, but it failed for %s"
              .formatted(failed.stream().map(FailedCondition::key).toList()),
          failed);
    }
    return operations.entrySet().stream()
        .map(
            entry -> {
              final var namespaceOperations = List.copyOf(entry.getValue().values());
              return created.contains(entry.getKey())
                  ? (NamespaceEntry) new NamespaceEntry.Created(entry.getKey(), namespaceOperations)
                  : new NamespaceEntry.KeysChanged(entry.getKey(), namespaceOperations);
            })
        .toList();
  }
}
