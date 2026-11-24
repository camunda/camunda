/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Reads, writes and watches keys of the cluster metadata store. Namespaces are managed through
 * {@link NamespaceApi}.
 *
 * <p>Every write the store accepts takes a place in one commit order, the same on every broker;
 * reads see writes in that order and events are delivered in it. Every call returns a future that
 * the store may complete on any thread: a caller that is an actor continues on its own actor (for
 * example with {@code future.onComplete(consumer, actor)}) and never calls {@code join()}. Failures
 * are {@link ClusterMetadataStoreException}s.
 */
public interface ClusterMetadataStore {

  /**
   * Reads the pair stored under exactly this key, from this broker's view of the store. Empty if
   * the key holds no value, also when keys exist underneath it.
   */
  ActorFuture<Optional<KeyValue>> get(StoreKey key);

  /**
   * Reads every pair stored at or under the prefix, at any depth, from this broker's view of the
   * store, sorted by key. The prefix matches on whole segments: {@code a.b} matches {@code a.b} and
   * {@code a.b.c}, not {@code a.bc}. An empty key lists the whole namespace.
   */
  ActorFuture<List<KeyValue>> list(StoreKey prefix);

  /**
   * Applies the batch as one write, all or nothing, checked in the order of its writes, so a {@link
   * Write.CreateNamespace} can be followed by writes into that namespace. A {@link
   * ClusterMetadataStoreException.ConditionFailedException} means nothing was applied; a {@link
   * ClusterMetadataStoreException.WriteTimeoutException} does not establish that. Every other write
   * method of this interface is a batch of one write.
   */
  ActorFuture<Void> batch(Batch batch);

  /**
   * Writes the value of one key. The old current value becomes the previous value; a key that
   * already holds the same bytes is left as it is. Completes once the write is committed; this
   * broker's view can apply it shortly after.
   */
  default ActorFuture<Void> put(final StoreKey key, final Value value) {
    return put(key, value, Condition.always());
  }

  /**
   * Like {@link #put(StoreKey, Value)}, but only if the condition holds. Otherwise fails with
   * {@link ClusterMetadataStoreException.ConditionFailedException}.
   */
  default ActorFuture<Void> put(final StoreKey key, final Value value, final Condition condition) {
    return batchOfOne(() -> new Write.Put(key, value, condition));
  }

  /**
   * Deletes one key, with its current and previous value. Keys underneath it are not touched. A key
   * that holds no value is not an error.
   */
  default ActorFuture<Void> delete(final StoreKey key) {
    return delete(key, Condition.always());
  }

  /**
   * Like {@link #delete(StoreKey)}, but only if the condition holds. Otherwise fails with {@link
   * ClusterMetadataStoreException.ConditionFailedException}.
   */
  default ActorFuture<Void> delete(final StoreKey key, final Condition condition) {
    return batchOfOne(() -> new Write.Delete(key, condition));
  }

  /**
   * Deletes every key at or under the prefix, at any depth, as one write. The prefix matches on
   * whole segments, as in {@link #list(StoreKey)}. An empty key fails the future with {@link
   * ClusterMetadataStoreException.InvalidArgumentException}. A prefix that matches no key is not an
   * error.
   */
  default ActorFuture<Void> deletePrefix(final StoreKey prefix) {
    return batchOfOne(() -> new Write.DeletePrefix(prefix));
  }

  /**
   * Registers a listener for every change of the key or of a key underneath it, on this broker's
   * view. The key does not have to exist. Registration delivers nothing for the current state: once
   * the future completes, use {@link #get} or {@link #list} for the starting state.
   *
   * <p>A listener that throws is not called again for that event; delivery goes on with the next
   * one. When the namespace is deleted, the listener receives {@link KeyEvent.ChangeType#REMOVE}
   * for each matching key, then {@link Subscription.Listener#onClosed} with {@link
   * Subscription.CloseReason#NAMESPACE_DELETED}.
   *
   * <p>Fails, with nothing registered, with {@link
   * ClusterMetadataStoreException.NamespaceNotFoundException} if the namespace of the key does not
   * exist, {@link ClusterMetadataStoreException.InvalidArgumentException} if the key is empty,
   * {@link ClusterMetadataStoreException.ViewStalledException} if this broker's view can no longer
   * follow the store, or {@link ClusterMetadataStoreException.StoreClosedException} if the store is
   * closed.
   */
  ActorFuture<Subscription> watch(
      StoreKey key, Subscription.Listener<KeyEvent> listener, Executor executor);

  /** Fails the future, rather than throwing, if the write breaks a rule, as every call does. */
  private ActorFuture<Void> batchOfOne(final Supplier<Write> write) {
    final Batch batch;
    try {
      batch = Batch.of(write.get());
    } catch (final ClusterMetadataStoreException e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
    return batch(batch);
  }
}
