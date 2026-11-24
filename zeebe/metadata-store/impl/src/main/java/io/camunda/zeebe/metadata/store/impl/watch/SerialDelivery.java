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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A FIFO queue of calls on the executor of one subscription, with at most one drain running at a
 * time. The executor itself need not be single-threaded or FIFO: the calls still run one at a time
 * and in the order they were queued. A call that throws is logged and does not stop the ones after
 * it. An executor that rejects a drain, for example because its consumer shut it down, gets no
 * further calls: they are dropped, so the store never fails because of a consumer's executor.
 */
final class SerialDelivery {

  private static final Logger LOG = LoggerFactory.getLogger(SerialDelivery.class);

  private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean draining = new AtomicBoolean();
  private final AtomicBoolean rejected = new AtomicBoolean();
  private final Executor executor;

  SerialDelivery(final Executor executor) {
    this.executor = executor;
  }

  void enqueue(final Runnable call) {
    if (rejected.get()) {
      return;
    }
    queue.add(call);
    scheduleDrain();
  }

  private void scheduleDrain() {
    if (draining.compareAndSet(false, true)) {
      try {
        executor.execute(this::drain);
      } catch (final RejectedExecutionException e) {
        rejected.set(true);
        queue.clear();
        LOG.warn(
            "The executor of a cluster metadata store listener rejected its events; dropping them"
                + " and every later one",
            e);
      }
    }
  }

  private void drain() {
    Runnable call;
    while ((call = queue.poll()) != null) {
      try {
        call.run();
      } catch (final Exception e) {
        LOG.warn("A cluster metadata store listener failed; continuing with the next event", e);
      }
    }
    draining.set(false);
    // a call queued after the last poll but before the flag was cleared found the drain running
    if (!queue.isEmpty()) {
      scheduleDrain();
    }
  }
}
