/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.sink;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The detachable outbox of a frozen commit cut (streaming ADR 0005): items staged on the owner
 * thread are {@link #freeze() detached} at the commit barrier into a frozen pile, drained to their
 * destination on the IO thread, and {@link #completeFrozen(boolean) completed} back on the owner
 * thread — retired when the cut landed, retained for the next cut when it failed. A retained pile
 * is re-emitted <em>ahead</em> of newer items at the next freeze (or synchronous drain), preserving
 * staging order across a failed cut: for idempotent full-value writes that keeps last-write-wins
 * per key, and for monotonically sequenced frames it keeps the sequence the receiver's dedup relies
 * on.
 *
 * <p><b>Threading.</b> Single writer, no locks. The owner thread stages, freezes and completes; the
 * IO thread exclusively owns the frozen pile between freeze and completion and never touches the
 * active pile the owner keeps filling. The happens-before edges come from the runtime's executor
 * handoff (freeze &rarr; drain &rarr; complete), exactly as for the rest of the frozen cut. At most
 * one frozen pile is outstanding at a time.
 *
 * @param <T> the staged item type — must be immutable (or no longer mutated) by the time it is
 *     staged, since the IO thread reads it with no synchronization beyond the handoff
 */
public final class FrozenOutbox<T> {

  /** Items staged since the last freeze or drain; owner thread only. */
  private List<T> staged = new ArrayList<>();

  /** A failed cut's items, re-emitted ahead of newer items at the next freeze or drain. */
  private final List<T> retained = new ArrayList<>();

  /** The outstanding frozen pile (null when none); owned by the IO thread until completed. */
  private List<T> frozen;

  /** Owner thread: appends {@code item} to the active pile, staging it for the next cut. */
  public void stage(final T item) {
    staged.add(item);
  }

  /**
   * Owner thread: true if anything is staged for the next freeze or synchronous drain — a failed
   * cut's retained items count, since they re-emit first.
   */
  public boolean hasStaged() {
    return !staged.isEmpty() || !retained.isEmpty();
  }

  /** True if a frozen pile is outstanding — frozen but not yet completed. */
  public boolean hasFrozen() {
    return frozen != null;
  }

  /**
   * Owner thread, at the commit barrier: detaches the active pile into the frozen slot — any
   * retained items of a failed cut first, then the newly staged items, preserving order — and
   * installs a fresh active pile. Items staged afterwards belong to the next cut.
   *
   * @throws IllegalStateException if a frozen pile is already outstanding
   */
  public void freeze() {
    if (frozen != null) {
      throw new IllegalStateException(
          "expected no outstanding frozen pile, but freeze() was called again before"
              + " completeFrozen()");
    }
    final List<T> pile = new ArrayList<>(retained.size() + staged.size());
    pile.addAll(retained);
    retained.clear();
    pile.addAll(staged);
    staged = new ArrayList<>();
    frozen = pile;
  }

  /**
   * IO thread: feeds the frozen pile to {@code sink} in staging order. Touches only the frozen pile
   * — never the active pile the owner thread keeps filling.
   *
   * @return the number of items drained, so a caller can skip its durable flush for an empty cut
   * @throws IllegalStateException if nothing is frozen
   */
  public int drainFrozen(final Consumer<T> sink) {
    if (frozen == null) {
      throw new IllegalStateException("expected a frozen pile to drain, but none");
    }
    for (final T item : frozen) {
      sink.accept(item);
    }
    return frozen.size();
  }

  /**
   * Owner thread, once the cut's outcome is known. Success: the items are durable at their
   * destination — discard the pile. Failure: retain it so the next freeze (or synchronous drain)
   * re-emits it ahead of newer items.
   *
   * @throws IllegalStateException if nothing is frozen
   */
  public void completeFrozen(final boolean success) {
    if (frozen == null) {
      throw new IllegalStateException("expected a frozen pile to complete, but none");
    }
    if (!success) {
      retained.addAll(frozen);
    }
    frozen = null;
  }

  /**
   * Owner thread, a synchronous boundary outside the cut protocol (e.g. a close-time or freshness
   * flush): feeds any retained items of a failed cut, then the staged items, to {@code sink} in
   * order, clearing both. Only legal while no frozen pile is outstanding — with a cut in flight the
   * IO thread owns the destination.
   *
   * @throws IllegalStateException if a frozen pile is outstanding
   */
  public void drainPending(final Consumer<T> sink) {
    if (frozen != null) {
      throw new IllegalStateException("cannot drain synchronously: a frozen pile is outstanding");
    }
    for (final T item : retained) {
      sink.accept(item);
    }
    retained.clear();
    for (final T item : staged) {
      sink.accept(item);
    }
    staged.clear();
  }
}
