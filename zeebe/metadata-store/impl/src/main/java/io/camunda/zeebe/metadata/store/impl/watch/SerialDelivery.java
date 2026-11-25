/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.watch;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A FIFO queue of calls on the executor of one subscription, with at most one drain running at a
 * time. The executor itself need not be single-threaded or FIFO: the calls still run one at a time
 * and in the order they were queued. A call that throws an exception is logged and does not stop
 * the ones after it; an error propagates to the executor, and the remaining calls run with the next
 * one queued. An executor that rejects a drain, for example because its consumer shut it down, gets
 * no further drains: the queued calls are dropped and the rejection is handed to the owner, so the
 * store never fails because of a consumer's executor.
 */
final class SerialDelivery {

  private static final Logger LOG = LoggerFactory.getLogger(SerialDelivery.class);

  private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean draining = new AtomicBoolean();
  private final Executor executor;
  private final Consumer<RejectedExecutionException> onRejected;

  SerialDelivery(final Executor executor, final Consumer<RejectedExecutionException> onRejected) {
    this.executor = executor;
    this.onRejected = onRejected;
  }

  void enqueue(final Runnable call) {
    queue.add(call);
    scheduleDrain();
  }

  private void scheduleDrain() {
    if (draining.compareAndSet(false, true)) {
      try {
        executor.execute(this::drain);
      } catch (final RejectedExecutionException e) {
        // draining stays set, so no later call schedules a drain on this executor again
        queue.clear();
        onRejected.accept(e);
      }
    }
  }

  private void drain() {
    try {
      Runnable call;
      while ((call = queue.poll()) != null) {
        try {
          call.run();
        } catch (final Exception e) {
          LOG.warn("A cluster metadata store listener failed; continuing with the next event", e);
        }
      }
    } finally {
      draining.set(false);
    }
    // a call queued after the last poll but before the flag was cleared found the drain running
    if (!queue.isEmpty()) {
      scheduleDrain();
    }
  }
}
