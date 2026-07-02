/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.producer;

import io.camunda.eventbridge.client.EventBridgeException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounds the total bytes of in-flight publish batches so a producer that outruns the network
 * applies backpressure instead of growing the heap without limit. A publish {@link #acquire(long)
 * acquires} its byte size before it is sent and {@link #release(long) releases} it when the send
 * completes; once the outstanding total reaches the cap, further acquisitions park (their future
 * stays incomplete) and are admitted, in FIFO order, as earlier sends release their bytes.
 *
 * <p>Admission is non-blocking: {@link #acquire(long)} returns a future the caller chains its send
 * onto, so no thread is held while parked. A single batch larger than the whole cap is admitted
 * once nothing else is outstanding, so it can never deadlock. FIFO order means a large batch at the
 * head holds back smaller ones behind it rather than being starved.
 *
 * <p>Thread-safe. Futures are always completed outside the lock so a caller's continuation (e.g.
 * the send it chains on) never runs while the lock is held.
 */
public final class PublishBudget {

  private record Waiter(long bytes, CompletableFuture<Void> future) {}

  private final long maxBytes;
  private final ReentrantLock lock = new ReentrantLock();
  private final Deque<Waiter> waiters = new ArrayDeque<>();
  private long available;
  private boolean closed;

  public PublishBudget(final long maxBytes) {
    this.maxBytes = Math.max(1L, maxBytes);
    available = this.maxBytes;
  }

  /**
   * Reserves {@code bytes} of publish budget. The returned future completes once the reservation is
   * granted — immediately if budget is available and no one is ahead in the queue, otherwise when
   * enough in-flight sends have released. If the budget is closed, the future fails.
   */
  public CompletableFuture<Void> acquire(final long bytes) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    final List<CompletableFuture<Void>> granted = new ArrayList<>();
    lock.lock();
    try {
      if (closed) {
        future.completeExceptionally(new EventBridgeException("Client is closed"));
        return future;
      }
      // Always enqueue then drain from the head, so a new request can't jump ahead of a parked one.
      waiters.add(new Waiter(bytes, future));
      drain(granted);
    } finally {
      lock.unlock();
    }
    granted.forEach(f -> f.complete(null));
    return future;
  }

  /** Returns {@code bytes} to the budget and admits any waiters that now fit. */
  public void release(final long bytes) {
    final List<CompletableFuture<Void>> granted = new ArrayList<>();
    lock.lock();
    try {
      available += bytes;
      drain(granted);
    } finally {
      lock.unlock();
    }
    granted.forEach(f -> f.complete(null));
  }

  /** Fails every parked acquisition; subsequent acquisitions fail immediately. */
  public void close() {
    final List<Waiter> pending = new ArrayList<>();
    lock.lock();
    try {
      closed = true;
      pending.addAll(waiters);
      waiters.clear();
    } finally {
      lock.unlock();
    }
    pending.forEach(
        w -> w.future.completeExceptionally(new EventBridgeException("Client is closed")));
  }

  /** Admits head-of-queue waiters while budget allows. Caller holds the lock. */
  private void drain(final List<CompletableFuture<Void>> granted) {
    while (!waiters.isEmpty()) {
      final Waiter head = waiters.peek();
      // Admit if it fits, or if the pool is fully free (lets an over-cap batch through alone).
      if (available >= head.bytes || available == maxBytes) {
        waiters.poll();
        available -= head.bytes;
        granted.add(head.future);
      } else {
        break;
      }
    }
  }
}
