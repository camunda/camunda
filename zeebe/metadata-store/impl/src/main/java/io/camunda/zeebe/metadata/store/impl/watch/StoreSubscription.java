/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.watch;

import io.camunda.zeebe.metadata.store.api.Subscription;
import io.camunda.zeebe.metadata.store.api.WatchListener;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A subscription with its listener and delivery queue. Events and the end of the subscription reach
 * the listener in the order they were queued; once the consumer closes it, nothing more does.
 *
 * @param <E> the event type of the listener
 */
final class StoreSubscription<E> implements Subscription {

  private final WatchListener<E> listener;
  private final SerialDelivery delivery;
  private final Consumer<StoreSubscription<E>> unregister;
  private final AtomicBoolean closedByConsumer = new AtomicBoolean();
  private final AtomicBoolean ended = new AtomicBoolean();

  /**
   * @param unregister removes this subscription from wherever the registry keeps it
   */
  StoreSubscription(
      final WatchListener<E> listener,
      final Executor executor,
      final Consumer<StoreSubscription<E>> unregister) {
    this.listener = listener;
    delivery = new SerialDelivery(executor);
    this.unregister = unregister;
  }

  @Override
  public void close() {
    if (closedByConsumer.compareAndSet(false, true)) {
      unregister.accept(this);
    }
  }

  /** Queues the event for the listener. */
  void notify(final E event) {
    deliver(() -> listener.onEvent(event));
  }

  /** Ends the subscription: the listener's {@code onClosed} follows the events queued before. */
  void end(final CloseReason reason) {
    if (ended.compareAndSet(false, true)) {
      deliver(() -> listener.onClosed(reason));
    }
  }

  /** Queues a call to the listener, which is skipped if the consumer closed the subscription. */
  private void deliver(final Runnable call) {
    delivery.enqueue(
        () -> {
          if (!closedByConsumer.get()) {
            call.run();
          }
        });
  }
}
