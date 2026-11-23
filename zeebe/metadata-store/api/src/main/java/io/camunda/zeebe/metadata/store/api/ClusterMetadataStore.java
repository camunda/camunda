/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

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
   * Writes the value of one key. The old current value becomes the previous value; a key that
   * already holds the same bytes is left as it is. Completes once the write is committed; this
   * broker's view can apply it shortly after.
   */
  ActorFuture<Void> put(StoreKey key, byte[] value);

  /**
   * Like {@link #put(StoreKey, byte[])}, but only if the condition holds. Otherwise fails with
   * {@link ClusterMetadataStoreException.ConditionFailedException}.
   */
  ActorFuture<Void> put(StoreKey key, byte[] value, Condition condition);

  /**
   * Deletes one key, with its current and previous value. Keys underneath it are not touched. A key
   * that holds no value is not an error.
   */
  ActorFuture<Void> delete(StoreKey key);

  /**
   * Like {@link #delete(StoreKey)}, but only if the condition holds. Otherwise fails with {@link
   * ClusterMetadataStoreException.ConditionFailedException}.
   */
  ActorFuture<Void> delete(StoreKey key, Condition condition);

  /**
   * Deletes every key at or under the prefix, at any depth, as one write. The prefix matches on
   * whole segments, as in {@link #list(StoreKey)}. An empty key fails with {@link
   * ClusterMetadataStoreException.InvalidArgumentException}. A prefix that matches no key is not an
   * error.
   */
  ActorFuture<Void> deletePrefix(StoreKey prefix);

  /**
   * Registers a listener for every change of the key or of a key underneath it, on this broker's
   * view. The key does not have to exist. Registration delivers nothing for the current state:
   * register first, then use {@link #get} or {@link #list} for the starting state.
   *
   * <p>The listener is called on the given executor, one event at a time and in commit order. A
   * listener that throws is not called again for that event; delivery goes on with the next one.
   * When the namespace is deleted, the listener receives {@link KeyEvent.ChangeType#REMOVE} for
   * each matching key, then the subscription closes with {@link
   * Subscription.CloseReason#NAMESPACE_DELETED}.
   *
   * @throws ClusterMetadataStoreException.NamespaceNotFoundException if the namespace of the key
   *     does not exist; nothing is registered
   * @throws ClusterMetadataStoreException.ViewStalledException if this broker's view can no longer
   *     follow the store; nothing is registered
   * @throws ClusterMetadataStoreException.InvalidArgumentException if the key is empty or not in
   *     canonical form; nothing is registered
   * @throws ClusterMetadataStoreException.StoreClosedException if the store is closed; nothing is
   *     registered
   */
  Subscription watch(StoreKey key, Consumer<KeyEvent> listener, Executor executor);

  /**
   * Applies the writes as one write, all or nothing, checked in the order of the list, so a {@link
   * Write.CreateNamespace} can be followed by writes into that namespace. A {@link
   * ClusterMetadataStoreException.ConditionFailedException} means nothing was applied; a {@link
   * ClusterMetadataStoreException.WriteTimeoutException} does not establish that. Two writes must
   * not address the same key, and no write may address a key at or under a {@link
   * Write.DeletePrefix} of the same batch; otherwise the batch fails with {@link
   * ClusterMetadataStoreException.InvalidArgumentException}.
   */
  ActorFuture<Void> batch(List<Write> writes);
}
