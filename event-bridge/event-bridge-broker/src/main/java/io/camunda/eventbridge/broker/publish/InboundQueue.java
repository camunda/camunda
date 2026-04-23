/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.publish;

import io.camunda.eventbridge.broker.flowcontrol.FlowControl;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Predicate;

/**
 * Bounded MPSC queue of {@link InflightBatchEntry} references with integrated flow control.
 *
 * <h3>Producer API</h3>
 *
 * (called from external threads, e.g. Netty event loops)
 *
 * <ul>
 *   <li>{@link #offer} -- enqueue an entry, acquiring flow control capacity
 *   <li>{@link #scheduleDrain} -- signal that data is available (coalescing dirty flag)
 * </ul>
 *
 * <h3>Consumer API</h3>
 *
 * (called from the single-threaded actor only)
 *
 * <ul>
 *   <li>{@link #drainAndCheckRemaining} -- drain entries and report if more remain
 *   <li>{@link #hasData} -- check if the queue has data (e.g. after processing)
 * </ul>
 *
 * <h3>Flow control contract</h3>
 *
 * <p>On {@link #offer}, flow control capacity is acquired via {@link FlowControl#tryAcquire}. If
 * the queue rejects the entry (full), capacity is immediately released. On success, the
 * <b>consumer</b> (the {@code EventStreamAppender}) is responsible for releasing capacity via
 * {@link FlowControl#release(int)} after the entry has been committed, failed, or discarded.
 *
 * <h3>Drain scheduling protocol</h3>
 *
 * <p>{@link #scheduleDrain()} returns {@code true} at most once per drain cycle. The consumer must
 * call {@link #drainAndCheckRemaining} which resets the flag, allowing the next producer to signal
 * again. This coalesces multiple rapid offers into a single actor wake-up.
 *
 * <p>The consumer must always check the return value of {@link #drainAndCheckRemaining}: if it
 * returns {@code true}, entries arrived during or after the drain and the consumer must re-schedule
 * itself. Ignoring this return value can strand entries indefinitely.
 */
public final class InboundQueue {

  private static final VarHandle DRAIN_SCHEDULED;

  static {
    try {
      DRAIN_SCHEDULED =
          MethodHandles.lookup().findVarHandle(InboundQueue.class, "drainScheduled", boolean.class);
    } catch (final ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private final MpscArrayQueue<InflightBatchEntry> queue;
  private final FlowControl flowControl;

  /**
   * Dirty flag for drain scheduling. Inlined as a plain volatile boolean with VarHandle CAS to
   * avoid the extra object indirection of AtomicBoolean.
   */
  @SuppressWarnings("FieldMayBeFinal")
  private volatile boolean drainScheduled = false;

  public InboundQueue(final int capacity, final FlowControl flowControl) {
    queue = new MpscArrayQueue<>(capacity);
    this.flowControl = flowControl;
  }

  /**
   * Enqueues an entry, acquiring flow control capacity first.
   *
   * <p>Returns {@code false} (backpressure) in two cases:
   *
   * <ol>
   *   <li>Flow control rejects the entry (rate/memory limit exceeded)
   *   <li>The underlying queue is full
   * </ol>
   *
   * <p>On success, flow control capacity is reserved. The caller of {@link #drainAndCheckRemaining}
   * is responsible for releasing capacity via {@link FlowControl#release(int)} after processing.
   *
   * @param entry the batch entry to enqueue (must not be null)
   * @return {@code true} if accepted, {@code false} if rejected (backpressure)
   */
  public boolean offer(final InflightBatchEntry entry) {
    if (!flowControl.tryAcquire(entry.entryCount())) {
      return false;
    }

    if (!queue.offer(entry)) {
      flowControl.release(entry.entryCount());
      return false;
    }

    return true;
  }

  /**
   * Attempts to mark that a drain should be scheduled. Returns {@code true} if this call
   * transitioned the flag from {@code false} to {@code true}, meaning the caller should wake the
   * consumer actor. Returns {@code false} if a drain is already scheduled.
   *
   * <p>This coalesces multiple rapid offers into a single actor wake-up. Only the first producer
   * after a drain resets wins the CAS and signals the actor.
   *
   * @return {@code true} if the caller should signal the consumer actor
   */
  public boolean scheduleDrain() {
    return DRAIN_SCHEDULED.compareAndSet(this, false, true);
  }

  /**
   * Drains entries until the queue is empty or the visitor returns false.
   *
   * <p>The flag is reset <b>before</b> draining so that producers arriving during the drain can
   * re-signal the actor. The caller must check the return value: if {@code true}, entries may have
   * arrived during or after the drain and the caller must re-schedule itself.
   *
   * <p>Called by the single consumer thread only.
   *
   * @param visitor evaluates each entry. Returns true to continue draining, false to stop.
   * @return {@code true} if the queue still has data after draining
   */
  boolean drainAndCheckRemaining(final Predicate<InflightBatchEntry> visitor) {
    // Reset BEFORE drain: opens the window for producers to re-signal.
    // Using release semantics -- the actual drain reads are the important part,
    // and producers will see the reset on their next scheduleDrain() CAS.
    DRAIN_SCHEDULED.setRelease(this, false);

    while (true) {
      final InflightBatchEntry entry = queue.poll();
      if (entry == null) {
        break; // Empty or mid-write race
      }

      // Pass to the publisher. If it returns false, it hit a limit.
      if (!visitor.test(entry)) {
        break;
      }
    }

    // Re-check: entries may have arrived during drain, or the visitor stopped us early.
    return !queue.isEmpty();
  }

  /**
   * Returns {@code true} if the queue appears to have data. May briefly return {@code false} while
   * a producer is mid-write (slot claimed but element not yet stored).
   *
   * <p>Called by the single consumer thread only.
   */
  boolean hasData() {
    return !queue.isEmpty();
  }
}
