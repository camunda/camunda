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
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.StoreClosedException;
import io.camunda.zeebe.metadata.store.api.KeyEvent;
import io.camunda.zeebe.metadata.store.api.KeyValue;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.NamespaceApi;
import io.camunda.zeebe.metadata.store.api.NamespaceChange;
import io.camunda.zeebe.metadata.store.api.NamespaceScope;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.Subscription;
import io.camunda.zeebe.metadata.store.api.Subscription.CloseReason;
import io.camunda.zeebe.metadata.store.api.WatchListener;
import io.camunda.zeebe.metadata.store.api.Write;
import io.camunda.zeebe.metadata.store.impl.state.Entry;
import io.camunda.zeebe.metadata.store.impl.state.Entry.NamespaceEntry;
import io.camunda.zeebe.metadata.store.impl.state.EntryApplier;
import io.camunda.zeebe.metadata.store.impl.state.StoreState;
import io.camunda.zeebe.metadata.store.impl.watch.WatchRegistry;
import io.camunda.zeebe.metadata.store.impl.write.WriteEvaluator;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A cluster metadata store without Raft and without durability, on one actor: a write is evaluated
 * and applied directly, with the same state, evaluation and watch registry as the Raft store. It
 * serves consumers and their tests until the Raft store exists.
 *
 * <p>The actor serializes writes, registrations and the capture of read views, so a watch
 * registered before a read never misses a change that the read does not show. The state is
 * published to a volatile field after each write; reads work on that immutable value.
 */
public final class InMemoryClusterMetadataStore extends Actor
    implements ClusterMetadataStore, NamespaceApi {

  private final String producerVersion;
  private final InstantSource clock;
  private volatile StoreState state = StoreState.empty();
  private final WatchRegistry registry = new WatchRegistry(actor::run, () -> state);
  private volatile boolean closed;

  /**
   * @param producerVersion the Camunda version that the store records as the version of each
   *     namespace it changes
   * @param clock the clock of the writer, read once per write
   */
  public InMemoryClusterMetadataStore(final String producerVersion, final InstantSource clock) {
    super("ClusterMetadataStore");
    this.producerVersion = producerVersion;
    this.clock = clock;
  }

  @Override
  protected void onActorClosing() {
    closed = true;
    registry.closeAll(CloseReason.STORE_CLOSED);
  }

  @Override
  public ActorFuture<Optional<KeyValue>> get(final StoreKey key) {
    return submit(() -> state.get(key));
  }

  @Override
  public ActorFuture<List<KeyValue>> list(final StoreKey prefix) {
    return submit(() -> state.list(prefix));
  }

  @Override
  public Subscription watch(
      final StoreKey key, final WatchListener<KeyEvent> listener, final Executor executor) {
    requireOpen();
    return registry.watch(key, listener, executor);
  }

  @Override
  public ActorFuture<Void> batch(final Batch batch) {
    return write(() -> WriteEvaluator.evaluate(state, batch));
  }

  @Override
  public ActorFuture<Void> createNamespace(final Namespace namespace) {
    return batch(Batch.of(new Write.CreateNamespace(namespace)));
  }

  @Override
  public ActorFuture<Boolean> exists(final Namespace namespace) {
    return submit(() -> state.exists(namespace));
  }

  @Override
  public ActorFuture<List<Namespace>> list(final NamespaceScope scope) {
    return submit(() -> state.namespaces(scope));
  }

  @Override
  public ActorFuture<Void> deleteNamespace(final Namespace namespace) {
    return write(() -> List.of(WriteEvaluator.evaluateDeleteNamespace(state, namespace)));
  }

  @Override
  public Subscription watchNamespace(
      final NamespaceScope scope,
      final WatchListener<NamespaceChange> listener,
      final Executor executor) {
    requireOpen();
    return registry.watchNamespace(scope, listener, executor);
  }

  private ActorFuture<Void> write(final Supplier<List<NamespaceEntry>> evaluation) {
    return submit(
        () -> {
          final var namespaces = evaluation.get();
          if (!namespaces.isEmpty()) {
            apply(new Entry(producerVersion, clock.millis(), namespaces));
          }
          return unit();
        });
  }

  private void apply(final Entry entry) {
    final var applied = EntryApplier.apply(state, state.appliedPosition() + 1, entry);
    state = applied.state();
    registry.dispatch(applied.changes());
  }

  /**
   * Runs the call on the actor, after every earlier registration and write, and only while the
   * store is open. A store error, such as a failed condition, completes the returned future
   * exceptionally instead of escaping the actor job, which the scheduler would log as an uncaught
   * error; any other exception is a bug and still escapes, so it is logged. A call that the actor
   * drops because it closes fails with {@link StoreClosedException}.
   */
  private <T> ActorFuture<T> submit(final Supplier<T> call) {
    if (closed) {
      return CompletableActorFuture.completedExceptionally(storeClosed());
    }
    final var result = new CompletableActorFuture<T>();
    actor
        .call(
            () -> {
              try {
                requireOpen();
                result.complete(call.get());
              } catch (final ClusterMetadataStoreException e) {
                result.completeExceptionally(e);
              }
              return unit();
            })
        .onComplete(
            (ignored, failure) -> {
              if (failure != null) {
                result.completeExceptionally(closed ? storeClosed() : failure);
              }
            },
            Runnable::run);
    return result;
  }

  private void requireOpen() {
    if (closed) {
      throw storeClosed();
    }
  }

  private static StoreClosedException storeClosed() {
    return new StoreClosedException(
        "Expected the cluster metadata store to be open, but it is closed");
  }
}
