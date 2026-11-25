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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A subscription with its listener and delivery queue. It is active until it terminates, either
 * because the store ends it or because its executor rejects a call; it takes no events after that.
 * Events and the end of the subscription reach the listener in the order they were queued; once the
 * consumer closes it, nothing more does.
 *
 * @param <E> the event type of the listener
 */
final class StoreSubscription<E> implements Subscription {

  private static final Logger LOG = LoggerFactory.getLogger(StoreSubscription.class);

  private final WatchListener<E> listener;
  private final SerialDelivery delivery;
  private final Consumer<StoreSubscription<E>> unregister;
  private final AtomicBoolean closedByConsumer = new AtomicBoolean();
  private final AtomicBoolean terminated = new AtomicBoolean();

  /**
   * @param unregister removes this subscription from wherever the registry keeps it
   */
  StoreSubscription(
      final WatchListener<E> listener,
      final Executor executor,
      final Consumer<StoreSubscription<E>> unregister) {
    this.listener = listener;
    delivery = new SerialDelivery(executor, this::onRejected);
    this.unregister = unregister;
  }

  @Override
  public void close() {
    if (closedByConsumer.compareAndSet(false, true)) {
      // the queued calls would be skipped anyway; dropping them frees them behind a slow listener
      delivery.clear();
      unregister.accept(this);
    }
  }

  /** Queues the event for the listener, unless the subscription terminated. */
  void notify(final E event) {
    if (!terminated.get()) {
      deliver(() -> listener.onEvent(event));
    }
  }

  /** Ends the subscription: the listener's {@code onClosed} follows the events queued before. */
  void end(final CloseReason reason) {
    if (terminated.compareAndSet(false, true)) {
      deliver(() -> listener.onClosed(reason));
    }
  }

  /**
   * Queues a call to the listener, unless the consumer closed the subscription. The call checks
   * again when it runs, since the consumer may close it while the call waits.
   */
  private void deliver(final Runnable call) {
    if (closedByConsumer.get()) {
      return;
    }
    delivery.enqueue(
        () -> {
          if (!closedByConsumer.get()) {
            call.run();
          }
        });
  }

  /** The listener cannot be told, since its executor is what rejected the call. */
  private void onRejected(final RejectedExecutionException e) {
    if (terminated.compareAndSet(false, true)) {
      LOG.warn(
          "The executor of a cluster metadata store listener rejected its events; ending the"
              + " subscription without notifying the listener",
          e);
      unregister.accept(this);
    }
  }
}
