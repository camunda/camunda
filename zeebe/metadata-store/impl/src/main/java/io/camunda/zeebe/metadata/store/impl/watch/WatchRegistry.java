/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.watch;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceNotFoundException;
import io.camunda.zeebe.metadata.store.api.KeyEvent;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.NamespaceChange;
import io.camunda.zeebe.metadata.store.api.NamespaceScope;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.Subscription;
import io.camunda.zeebe.metadata.store.api.Subscription.CloseReason;
import io.camunda.zeebe.metadata.store.api.WatchListener;
import io.camunda.zeebe.metadata.store.impl.state.KeyPath;
import io.camunda.zeebe.metadata.store.impl.state.StoreChange;
import io.camunda.zeebe.metadata.store.impl.state.StoreChange.KeyChanged;
import io.camunda.zeebe.metadata.store.impl.state.StoreChange.NamespaceChanged;
import io.camunda.zeebe.metadata.store.impl.state.StoreState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The registered watches, by namespace and key path, and the dispatch of changes to them. Confined
 * to the actor that applies entries: registration, dispatch and removal run on it, so a
 * subscription receives every change applied after its registration and none before. A change
 * reaches the watches on the changed key and on each of its ancestor paths, so the cost of a change
 * depends on the depth of its key, not on the number of watches.
 */
public final class WatchRegistry {

  private final Map<Namespace, Map<String, Set<StoreSubscription<KeyEvent>>>> keyWatches =
      new HashMap<>();
  private final Map<NamespaceScope, Set<StoreSubscription<NamespaceChange>>> namespaceWatches =
      new EnumMap<>(NamespaceScope.class);
  private final Consumer<Runnable> onActor;
  private final Supplier<StoreState> state;

  /**
   * @param onActor runs a task on the actor that owns the registry
   * @param state the store's current published state, read on that actor
   */
  public WatchRegistry(final Consumer<Runnable> onActor, final Supplier<StoreState> state) {
    this.onActor = onActor;
    this.state = state;
  }

  /**
   * Watches the key and every key underneath it. The registration runs on the actor, so it is
   * ordered before any read that the caller starts after this returns. If the namespace is deleted
   * before the registration runs, the subscription ends with {@link CloseReason#NAMESPACE_DELETED}.
   *
   * @throws InvalidArgumentException if the key is empty
   * @throws NamespaceNotFoundException if the namespace does not exist
   */
  public Subscription watch(
      final StoreKey key, final WatchListener<KeyEvent> listener, final Executor executor) {
    if (key.isEmpty()) {
      throw new InvalidArgumentException(
          "Expected a non-empty key to watch, but got the empty key of namespace '%s'"
              .formatted(key.namespace()));
    }
    state.get().requireNamespace(key.namespace());
    final var subscription =
        new StoreSubscription<>(
            listener, executor, s -> onActor.accept(() -> removeKeyWatch(key, s)));
    onActor.accept(() -> registerKeyWatch(key, subscription));
    return subscription;
  }

  /** Watches the namespaces of the scope being created or deleted. */
  public Subscription watchNamespace(
      final NamespaceScope scope,
      final WatchListener<NamespaceChange> listener,
      final Executor executor) {
    final var subscription =
        new StoreSubscription<>(
            listener, executor, s -> onActor.accept(() -> namespaceWatchesOf(scope).remove(s)));
    onActor.accept(() -> namespaceWatchesOf(scope).add(subscription));
    return subscription;
  }

  /** Queues the events of the changes of one applied entry, in their order. */
  public void dispatch(final List<StoreChange> changes) {
    for (final var change : changes) {
      switch (change) {
        case final KeyChanged keyChanged -> dispatch(keyChanged.event());
        case final NamespaceChanged namespaceChanged -> dispatch(namespaceChanged.change());
      }
    }
  }

  /** Ends every subscription with the reason and removes it. */
  public void closeAll(final CloseReason reason) {
    final var subscriptions = new ArrayList<StoreSubscription<?>>();
    keyWatches.values().forEach(paths -> paths.values().forEach(subscriptions::addAll));
    namespaceWatches.values().forEach(subscriptions::addAll);
    keyWatches.clear();
    namespaceWatches.clear();
    subscriptions.forEach(subscription -> subscription.end(reason));
  }

  private void dispatch(final KeyEvent event) {
    final var paths = keyWatches.get(event.key().namespace());
    if (paths == null) {
      return;
    }
    for (final var path : KeyPath.pathsTo(event.key().key())) {
      final var subscriptions = paths.get(path);
      if (subscriptions != null) {
        subscriptions.forEach(subscription -> subscription.notify(event));
      }
    }
  }

  /**
   * A deleted namespace ends its key watches; their removals were dispatched just before, as
   * ordinary key events.
   */
  private void dispatch(final NamespaceChange change) {
    if (change.type() == NamespaceChange.ChangeType.DELETED) {
      final var paths = keyWatches.remove(change.namespace());
      if (paths != null) {
        paths.values().forEach(subscriptions -> subscriptions.forEach(this::endDeleted));
      }
    }
    namespaceWatchesOf(change.namespace().scope())
        .forEach(subscription -> subscription.notify(change));
  }

  private void endDeleted(final StoreSubscription<KeyEvent> subscription) {
    subscription.end(CloseReason.NAMESPACE_DELETED);
  }

  /** The namespace may have been deleted after the caller's check; then the watch ends at once. */
  private void registerKeyWatch(
      final StoreKey key, final StoreSubscription<KeyEvent> subscription) {
    if (!state.get().exists(key.namespace())) {
      endDeleted(subscription);
      return;
    }
    keyWatches
        .computeIfAbsent(key.namespace(), ignored -> new HashMap<>())
        .computeIfAbsent(key.key(), ignored -> new LinkedHashSet<>())
        .add(subscription);
  }

  private void removeKeyWatch(final StoreKey key, final StoreSubscription<KeyEvent> subscription) {
    final var paths = keyWatches.get(key.namespace());
    if (paths == null) {
      return;
    }
    final var subscriptions = paths.get(key.key());
    if (subscriptions != null && subscriptions.remove(subscription) && subscriptions.isEmpty()) {
      paths.remove(key.key());
      if (paths.isEmpty()) {
        keyWatches.remove(key.namespace());
      }
    }
  }

  private Set<StoreSubscription<NamespaceChange>> namespaceWatchesOf(final NamespaceScope scope) {
    return namespaceWatches.computeIfAbsent(scope, ignored -> new LinkedHashSet<>());
  }
}
