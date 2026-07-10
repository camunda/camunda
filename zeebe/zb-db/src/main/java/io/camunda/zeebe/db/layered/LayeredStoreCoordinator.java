/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Orchestrates the layered stores of one partition as a single durability unit: freezes them
 * together, drains them into one atomic {@link PersistBatch} together with the recovery anchor, and
 * publishes consistent {@link ReadOnlyView}s to asynchronous readers.
 *
 * <p>The persist protocol splits into three steps so only the cheap one runs on the owner thread:
 *
 * <ol>
 *   <li>{@link #prepareRound()} — owner thread. Marks every store's pipeline segments as persisting
 *       (they stay readable) and captures them into a {@link PersistRound}.
 *   <li>{@link PersistRound#persist()} — may run on an IO thread. Drains all captured segments,
 *       oldest first, newest version per key winning, into one {@link PersistBatch}; writes the
 *       recovery anchor (the newest drained watermark) into the <em>same</em> batch; commits.
 *       Touches only the immutable segments and the sink — never a store's mutable layers.
 *   <li>{@link #completeRound(PersistRound, boolean)} — owner thread. On success retires the
 *       drained segments in every store, rotates the snapshot (take new, close old) and publishes a
 *       fresh view. On failure the segments stay in their pipelines and the next round retries
 *       them; nothing needs merging back.
 * </ol>
 *
 * <p>Rounds are single-flight: {@link #prepareRound()} throws while a round is outstanding. The
 * atomic batch is what upholds the anchor invariant — recovery either sees the full cut (state@P,
 * anchor=P) or none of it (state@P₀, anchor=P₀, replay rebuilds the difference); the torn states
 * (double application, holes) are unrepresentable.
 *
 * <p>{@link #freezeAll(long)} freezes every store at a common watermark and publishes a refreshed
 * view — segments change, the snapshot stays, which is consistent because the durable state has not
 * moved (persist rounds are its only writer).
 *
 * <p><b>Threading:</b> owner thread only, except {@link PersistRound#persist()}. Hand-offs (round
 * to IO thread, views to readers) must happen through a safe publication mechanism (actor message,
 * executor submission) — the coordinator contains no internal locking.
 */
public final class LayeredStoreCoordinator {

  /**
   * @param stores the stores forming the durability unit; names must be unique
   * @param sink creates the atomic persist batches and reads the anchor at recovery
   * @param snapshots pins durable-state snapshots for views
   * @param viewListener receives every newly published view; the previous view's snapshot is closed
   *     by the coordinator once the new one is published
   */
  public LayeredStoreCoordinator(
      final Collection<LayeredKeyValueStore> stores,
      final PersistSink sink,
      final SnapshotSource snapshots,
      final Consumer<ReadOnlyView> viewListener) {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /**
   * Freezes every store's active overlay into a segment stamped {@code watermark} and publishes a
   * refreshed view. Cheap — pointer swaps and flattens, no durable IO.
   */
  public void freezeAll(final long watermark) {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /**
   * Starts a persist round over everything currently frozen, implicitly freezing first if the
   * active overlays hold anything at {@code watermark}. Owner thread.
   *
   * @throws IllegalStateException if a round is already outstanding
   */
  public PersistRound prepareRound(final long watermark) {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /**
   * Finishes an outstanding round on the owner thread after {@link PersistRound#persist()} returned
   * (successfully or not). See the class javadoc for success/failure semantics.
   */
  public void completeRound(final PersistRound round, final boolean success) {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /** The most recently published view, or null before the first freeze. */
  public ReadOnlyView currentView() {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /** Whether a persist round is outstanding (prepared but not completed). */
  public boolean roundOutstanding() {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /** The recovery anchor as last committed by a round, or -1; delegates to the sink. */
  public long persistedAnchor() {
    throw new UnsupportedOperationException("implemented in task #6");
  }

  /**
   * One prepared persist round: the immutable segments captured from every store, drained on an IO
   * thread by {@link #persist()}. Never touches a store's mutable layers.
   */
  public static final class PersistRound {

    private PersistRound() {}

    /**
     * Drains the captured segments into one atomic batch — entries plus anchor — and commits. The
     * only coordinator operation allowed off the owner thread. Throws on failure; the caller
     * reports the outcome via {@link #completeRound(PersistRound, boolean)} either way.
     */
    public void persist() throws Exception {
      throw new UnsupportedOperationException("implemented in task #6");
    }

    /** The anchor position this round will commit (newest captured watermark), or -1. */
    public long anchor() {
      throw new UnsupportedOperationException("implemented in task #6");
    }
  }
}
