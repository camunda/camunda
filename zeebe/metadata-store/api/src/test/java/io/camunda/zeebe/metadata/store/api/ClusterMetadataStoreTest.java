/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

/**
 * The single writes are batches of one, so a store only implements {@link
 * ClusterMetadataStore#batch}, and a write that cannot be built fails its future like any other
 * write error.
 */
final class ClusterMetadataStoreTest {

  private static final Namespace NAMESPACE = Namespace.cluster("config");
  private static final StoreKey KEY = StoreKey.of(NAMESPACE, "a");
  private static final StoreValue VALUE = StoreValue.of(new byte[] {1});

  private final BatchRecordingStore store = new BatchRecordingStore();

  @Test
  void shouldWriteSingleOperationsAsBatchesOfOne() {
    // when
    store.put(KEY, VALUE);
    store.put(KEY, VALUE, Condition.absent());
    store.delete(KEY);
    store.delete(KEY, Condition.exists());
    store.deletePrefix(KEY);

    // then
    assertThat(store.batches)
        .containsExactly(
            Batch.of(new Write.Put(KEY, VALUE)),
            Batch.of(new Write.Put(KEY, VALUE, Condition.absent())),
            Batch.of(new Write.Delete(KEY)),
            Batch.of(new Write.Delete(KEY, Condition.exists())),
            Batch.of(new Write.DeletePrefix(KEY)));
  }

  @Test
  void shouldFailFutureOfWriteThatCannotBeBuilt() {
    // when
    final var future = store.deletePrefix(StoreKey.of(NAMESPACE, ""));

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThat(future.getException()).isInstanceOf(InvalidArgumentException.class);
    assertThat(store.batches).isEmpty();
  }

  private static final class BatchRecordingStore implements ClusterMetadataStore {

    private final List<Batch> batches = new ArrayList<>();

    @Override
    public ActorFuture<Void> batch(final Batch batch) {
      batches.add(batch);
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Optional<KeyValue>> get(final StoreKey key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ActorFuture<List<KeyValue>> list(final StoreKey prefix) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Subscription watch(
        final StoreKey key, final WatchListener<KeyEvent> listener, final Executor executor) {
      throw new UnsupportedOperationException();
    }
  }
}
