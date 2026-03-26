/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish;

import io.camunda.eventbridge.broker.publish.flowcontrol.FlowControl;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded MPSC queue of {@link InflightBatchEntry} references. */
public final class InboundQueue {

  private final MpscArrayQueue<InflightBatchEntry> queue;
  private final FlowControl flowControl;
  private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

  public InboundQueue(final int capacity, final FlowControl flowControl) {
    queue = new MpscArrayQueue<>(capacity);
    this.flowControl = flowControl;
  }

  public boolean offer(final InflightBatchEntry entry) {
    if (!flowControl.tryAcquire(entry.entryCount(), entry.batchLength())) {
      return false;
    }

    if (!queue.offer(entry)) {
      flowControl.onCompleted(entry.entryCount(), entry.batchLength());
      return false;
    }

    return true;
  }

  public boolean scheduleDrain() {
    return drainScheduled.compareAndSet(false, true);
  }

  int drain(final List<InflightBatchEntry> target, final int limit) {
    drainScheduled.set(false);
    return queue.drain(target, limit);
  }

  boolean hasData() {
    return !queue.isEmpty();
  }
}
