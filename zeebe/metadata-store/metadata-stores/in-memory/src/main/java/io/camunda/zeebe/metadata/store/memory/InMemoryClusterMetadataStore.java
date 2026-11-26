/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.memory;

import static io.camunda.zeebe.util.Unit.unit;

import io.camunda.zeebe.metadata.store.api.Batch;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStore;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.ConditionFailedException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.ConditionFailedException.FailedCondition;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceAlreadyExistsException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceNotFoundException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.StoreClosedException;
import io.camunda.zeebe.metadata.store.api.Condition;
import io.camunda.zeebe.metadata.store.api.KeyEvent;
import io.camunda.zeebe.metadata.store.api.KeyEvent.ChangeType;
import io.camunda.zeebe.metadata.store.api.KeyValue;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.NamespaceApi;
import io.camunda.zeebe.metadata.store.api.NamespaceChange;
import io.camunda.zeebe.metadata.store.api.NamespaceScope;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.StoreValue;
import io.camunda.zeebe.metadata.store.api.Subscription;
import io.camunda.zeebe.metadata.store.api.Subscription.CloseReason;
import io.camunda.zeebe.metadata.store.api.WatchListener;
import io.camunda.zeebe.metadata.store.api.Write;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * A cluster metadata store for consumers' tests: no Raft, no durability, no scheduler, and none of
 * the components of the Raft store. It is the simplest implementation of the API, and the reference
 * that the contract tests and, later, a property test of the Raft store compare against.
 *
 * <p>Every call runs under one lock, so any number of threads can use the store, and each call is
 * complete when it returns its future. Events reach a listener on its executor, one at a time and
 * in commit order, after the change that caused them is visible to reads.
 */
public final class InMemoryClusterMetadataStore
    implements ClusterMetadataStore, NamespaceApi, AutoCloseable {

  private static final Comparator<Namespace> NAMESPACE_ORDER =
      Comparator.comparing(Namespace::scope).thenComparing(Namespace::name);

  private final Map<Namespace, TreeMap<String, KeyValue>> namespaces = new HashMap<>();
  private final List<KeyWatch> keyWatches = new ArrayList<>();
  private final List<NamespaceWatch> namespaceWatches = new ArrayList<>();
  private boolean closed;

  @Override
  public synchronized ActorFuture<Optional<KeyValue>> get(final StoreKey key) {
    return call(
        () -> {
          if (key.isEmpty()) {
            throw new InvalidArgumentException(
                "Expected a non-empty key to get, but got the empty key of namespace '%s'"
                    .formatted(key.namespace()));
          }
          return Optional.ofNullable(values(key.namespace()).get(key.key()));
        });
  }

  @Override
  public synchronized ActorFuture<List<KeyValue>> list(final StoreKey prefix) {
    return call(
        () ->
            values(prefix.namespace()).values().stream()
                .filter(pair -> pair.key().isAtOrUnder(prefix))
                .toList());
  }

  @Override
  public synchronized Subscription watch(
      final StoreKey key, final WatchListener<KeyEvent> listener, final Executor executor) {
    requireOpen();
    if (key.isEmpty()) {
      throw new InvalidArgumentException(
          "Expected a non-empty key to watch, but got the empty key of namespace '%s'"
              .formatted(key.namespace()));
    }
    values(key.namespace());
    final var watch = new KeyWatch(key, new Delivery<>(listener, executor));
    keyWatches.add(watch);
    return watch;
  }

  @Override
  public synchronized ActorFuture<Void> batch(final Batch batch) {
    return call(
        () -> {
          dispatch(commit(evaluate(batch)));
          return unit();
        });
  }

  @Override
  public ActorFuture<Void> createNamespace(final Namespace namespace) {
    return batch(Batch.of(new Write.CreateNamespace(namespace)));
  }

  @Override
  public synchronized ActorFuture<Boolean> exists(final Namespace namespace) {
    return call(() -> namespaces.containsKey(namespace));
  }

  @Override
  public synchronized ActorFuture<List<Namespace>> list(final NamespaceScope scope) {
    return call(
        () ->
            namespaces.keySet().stream()
                .filter(namespace -> namespace.scope() == scope)
                .sorted(Comparator.comparing(Namespace::name))
                .toList());
  }

  @Override
  public synchronized ActorFuture<Void> deleteNamespace(final Namespace namespace) {
    return call(
        () -> {
          final var removed = values(namespace);
          namespaces.remove(namespace);
          final var events = new ArrayList<Event>();
          events.addAll(removed.values().stream().map(Event::removed).toList());
          events.add(
              new NamespaceChanged(
                  new NamespaceChange(namespace, NamespaceChange.ChangeType.DELETED)));
          dispatch(events);
          keyWatches.stream()
              .filter(watch -> watch.path.namespace().equals(namespace))
              .toList()
              .forEach(watch -> watch.end(CloseReason.NAMESPACE_DELETED));
          return unit();
        });
  }

  @Override
  public synchronized Subscription watchNamespace(
      final NamespaceScope scope,
      final WatchListener<NamespaceChange> listener,
      final Executor executor) {
    requireOpen();
    final var watch = new NamespaceWatch(scope, new Delivery<>(listener, executor));
    namespaceWatches.add(watch);
    return watch;
  }

  /** Fails later calls and ends every subscription with {@link CloseReason#STORE_CLOSED}. */
  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    keyWatches.forEach(watch -> watch.delivery.end(CloseReason.STORE_CLOSED));
    namespaceWatches.forEach(watch -> watch.delivery.end(CloseReason.STORE_CLOSED));
    keyWatches.clear();
    namespaceWatches.clear();
  }

  /**
   * Checks every write of the batch against the state before it, in order. A namespace error ends
   * the evaluation at once; failed conditions are collected over the whole batch. {@link Batch}
   * addresses each key once, so a write never depends on an earlier write to its key.
   */
  private Pending evaluate(final Batch batch) {
    final var pending = new Pending();
    final var failed = new ArrayList<FailedCondition>();

    for (final var write : batch.writes()) {
      switch (write) {
        case final Write.CreateNamespace create -> {
          if (namespaces.containsKey(create.namespace())
              || !pending.created.add(create.namespace())) {
            throw new NamespaceAlreadyExistsException(
                "Expected namespace '%s' not to exist, but it does".formatted(create.namespace()));
          }
        }
        case final Write.Put put -> {
          if (holds(put.key(), put.condition(), pending, failed)) {
            pending.writes.put(put.key(), Optional.of(put.value()));
          }
        }
        case final Write.Delete delete -> {
          if (holds(delete.key(), delete.condition(), pending, failed)) {
            pending.writes.put(delete.key(), Optional.empty());
          }
        }
        case final Write.DeletePrefix deletePrefix -> {
          final var prefix = deletePrefix.prefix();
          // a namespace that the batch creates holds no keys yet
          if (!pending.created.contains(prefix.namespace())) {
            values(prefix.namespace()).values().stream()
                .map(KeyValue::key)
                .filter(key -> key.isAtOrUnder(prefix))
                .forEach(key -> pending.writes.put(key, Optional.empty()));
          }
        }
        case final Write.Check check -> holds(check.key(), check.condition(), pending, failed);
      }
    }

    if (!failed.isEmpty()) {
      throw new ConditionFailedException(
          "Expected every condition of the batch to hold, but it failed for %s"
              .formatted(failed.stream().map(FailedCondition::key).toList()),
          failed);
    }
    return pending;
  }

  /** Checks a condition against the key as the state holds it; records the key if it fails. */
  private boolean holds(
      final StoreKey key,
      final Condition condition,
      final Pending pending,
      final List<FailedCondition> failed) {
    final var pair =
        pending.created.contains(key.namespace()) ? null : values(key.namespace()).get(key.key());
    final var current = pair == null ? null : pair.current();
    final var holds =
        switch (condition) {
          case final Condition.None ignored -> true;
          case final Condition.Absent ignored -> current == null;
          case final Condition.Exists ignored -> current != null;
          case final Condition.ValueEquals equals ->
              current != null && equals.expected().equals(current);
        };
    if (!holds) {
      failed.add(new FailedCondition(key, pair));
    }
    return holds;
  }

  /**
   * Applies the evaluated writes and returns the events they cause, namespace by namespace, each
   * with its creation first and then its keys in key order. A put of the bytes a key holds and a
   * delete of a missing key cause none.
   */
  private List<Event> commit(final Pending pending) {
    final var touched = new TreeSet<>(NAMESPACE_ORDER);
    touched.addAll(pending.created);
    pending.writes.keySet().forEach(key -> touched.add(key.namespace()));

    final var events = new ArrayList<Event>();
    for (final var namespace : touched) {
      if (pending.created.contains(namespace)) {
        namespaces.put(namespace, new TreeMap<>());
        events.add(
            new NamespaceChanged(
                new NamespaceChange(namespace, NamespaceChange.ChangeType.CREATED)));
      }
      final var values = values(namespace);
      pending.writes.entrySet().stream()
          .filter(write -> write.getKey().namespace().equals(namespace))
          .sorted(Map.Entry.comparingByKey(Comparator.comparing(StoreKey::key)))
          .forEach(write -> apply(values, write.getKey(), write.getValue(), events));
    }
    return events;
  }

  private static void apply(
      final TreeMap<String, KeyValue> values,
      final StoreKey key,
      final Optional<StoreValue> write,
      final List<Event> events) {
    final var existing = values.get(key.key());
    if (write.isPresent()) {
      final var value = write.get();
      if (existing == null) {
        values.put(key.key(), new KeyValue(key, value, null));
        events.add(new KeyChanged(new KeyEvent(ChangeType.ADD, key, value, null)));
      } else if (!existing.current().equals(value)) {
        values.put(key.key(), new KeyValue(key, value, existing.current()));
        events.add(
            new KeyChanged(new KeyEvent(ChangeType.REPLACE, key, value, existing.current())));
      }
    } else if (existing != null) {
      values.remove(key.key());
      events.add(Event.removed(existing));
    }
  }

  /** Hands the events to the subscriptions after the whole change is visible to reads. */
  private void dispatch(final List<Event> events) {
    for (final var event : events) {
      switch (event) {
        case final KeyChanged changed ->
            keyWatches.stream()
                .filter(watch -> changed.event().key().isAtOrUnder(watch.path))
                .forEach(watch -> watch.delivery.event(changed.event()));
        case final NamespaceChanged changed ->
            namespaceWatches.stream()
                .filter(watch -> watch.scope == changed.change().namespace().scope())
                .forEach(watch -> watch.delivery.event(changed.change()));
      }
    }
  }

  private TreeMap<String, KeyValue> values(final Namespace namespace) {
    final var values = namespaces.get(namespace);
    if (values == null) {
      throw new NamespaceNotFoundException(
          "Expected namespace '%s' to exist, but it does not".formatted(namespace));
    }
    return values;
  }

  private <T> ActorFuture<T> call(final Supplier<T> call) {
    try {
      requireOpen();
      return CompletableActorFuture.completed(call.get());
    } catch (final ClusterMetadataStoreException e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
  }

  private void requireOpen() {
    if (closed) {
      throw new StoreClosedException(
          "Expected the cluster metadata store to be open, but it is closed");
    }
  }

  /** The writes of a batch that have passed their conditions, not yet applied. */
  private static final class Pending {
    private final TreeSet<Namespace> created = new TreeSet<>(NAMESPACE_ORDER);

    /** A value for a put, empty for a delete. */
    private final Map<StoreKey, Optional<StoreValue>> writes = new LinkedHashMap<>();
  }

  private record KeyChanged(KeyEvent event) implements Event {}

  private record NamespaceChanged(NamespaceChange change) implements Event {}

  /**
   * The calls of one listener: one queue, drained by at most one task of the listener's executor,
   * so calls arrive one at a time and in order, also on an executor with many threads. An executor
   * that rejects the task drops the events of this listener only.
   */
  private static final class Delivery<E> {

    private final WatchListener<E> listener;
    private final Executor executor;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean draining;
    private boolean stopped;

    private Delivery(final WatchListener<E> listener, final Executor executor) {
      this.listener = listener;
      this.executor = executor;
    }

    void event(final E event) {
      enqueue(() -> listener.onEvent(event));
    }

    /** Delivers the end of the watch after the queued events; nothing is delivered after it. */
    void end(final CloseReason reason) {
      enqueue(() -> listener.onClosed(reason));
      synchronized (this) {
        stopped = true;
      }
    }

    /** The consumer closed its subscription: queued events are dropped. */
    synchronized void stop() {
      stopped = true;
      queue.clear();
    }

    private synchronized void enqueue(final Runnable call) {
      if (stopped) {
        return;
      }
      queue.add(call);
      if (draining) {
        return;
      }
      draining = true;
      try {
        executor.execute(this::drain);
      } catch (final RejectedExecutionException e) {
        stopped = true;
        draining = false;
        queue.clear();
      }
    }

    private void drain() {
      while (true) {
        final Runnable call;
        synchronized (this) {
          call = queue.poll();
          if (call == null) {
            draining = false;
            return;
          }
        }
        try {
          call.run();
        } catch (final RuntimeException e) {
          // a failing listener keeps its later events, and the error reaches the thread's handler
          final var thread = Thread.currentThread();
          thread.getUncaughtExceptionHandler().uncaughtException(thread, e);
        }
      }
    }
  }

  private sealed interface Event {

    static Event removed(final KeyValue pair) {
      return new KeyChanged(new KeyEvent(ChangeType.REMOVE, pair.key(), null, pair.current()));
    }
  }

  private final class KeyWatch implements Subscription {

    private final StoreKey path;
    private final Delivery<KeyEvent> delivery;

    private KeyWatch(final StoreKey path, final Delivery<KeyEvent> delivery) {
      this.path = path;
      this.delivery = delivery;
    }

    /** The store ends the watch: it delivers the reason after the events before it. */
    private void end(final CloseReason reason) {
      keyWatches.remove(this);
      delivery.end(reason);
    }

    @Override
    public void close() {
      synchronized (InMemoryClusterMetadataStore.this) {
        keyWatches.remove(this);
      }
      delivery.stop();
    }
  }

  private final class NamespaceWatch implements Subscription {

    private final NamespaceScope scope;
    private final Delivery<NamespaceChange> delivery;

    private NamespaceWatch(final NamespaceScope scope, final Delivery<NamespaceChange> delivery) {
      this.scope = scope;
      this.delivery = delivery;
    }

    @Override
    public void close() {
      synchronized (InMemoryClusterMetadataStore.this) {
        namespaceWatches.remove(this);
      }
      delivery.stop();
    }
  }
}
