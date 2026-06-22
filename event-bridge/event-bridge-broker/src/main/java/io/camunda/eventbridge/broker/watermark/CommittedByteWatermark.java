/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.watermark;

/**
 * Tracks the exact number of durably committed bytes for a <b>single partition</b>.
 *
 * <p><b>Architecture & Performance Design:</b> This class is heavily optimized for a read-biased
 * workload where hundreds of concurrent Virtual Threads (fetchers) read the watermark, but only a
 * single Actor thread writes to it.
 *
 * <ul>
 *   <li><b>Zero Read Allocations:</b> Readers fetch a reference to an immutable snapshot. This
 *       allows fetchers to safely "pin" a specific watermark state for calculations or delayed
 *       parking without generating heap garbage on the read path.
 *   <li><b>Wait-Free Publication:</b> By relying on the Single Writer Principle, we avoid the
 *       overhead of {@link java.util.concurrent.atomic.AtomicReference} (which requires CAS retry
 *       loops and lambda allocations). A simple {@code volatile} reference guarantees atomic
 *       visibility to all readers.
 * </ul>
 */
public final class CommittedByteWatermark implements HighWatermark {

  private final int partitionId;
  private volatile Runnable onWatermarkAdvancedNotifier;

  // The single source of truth.
  // The 'volatile' ensures that when the writer thread updates this reference,
  // the new immutable snapshot is immediately and atomically visible to all reading Virtual
  // Threads. It also acts as a StoreLoad memory barrier, preventing instruction reordering.
  private volatile PartitionWatermark currentWatermark = new PartitionWatermark(-1, 0);

  /**
   * @param partitionId the ID of the partition this tracker belongs to
   */
  public CommittedByteWatermark(final int partitionId) {
    this.partitionId = partitionId;
  }

  public int partitionId() {
    return partitionId;
  }

  /**
   * Retrieves the current watermark snapshot.
   *
   * <p><b>Performance:</b> Wait-free, lock-free, and strictly zero-allocation.
   *
   * @return an immutable {@link PartitionWatermark} representing the exact state at the moment of
   *     the read. The caller can safely hold ("pin") this instance for delayed evaluations without
   *     fearing concurrent mutation or blocking the writer.
   */
  @Override
  public PartitionWatermark get() {
    return currentWatermark;
  }

  /**
   * Advances the high watermark.
   *
   * <p><b>Contract:</b> This method MUST be called by a single, dedicated writer thread (e.g., the
   * partition's Raft/Log Actor). Because of this single-writer guarantee, no locks or
   * Compare-And-Swap (CAS) operations are needed.
   *
   * @param commitPosition the new highest committed record position
   * @param committedBytes the size of the newly committed batch to add to the total
   */
  @Override
  public void onCommitted(final long commitPosition, final int committedBytes) {
    // 1. Read the current volatile state.
    // This is safe without locks because we are the only thread that ever writes to it.
    final PartitionWatermark previous = currentWatermark;

    if (commitPosition <= previous.commitPosition()) {
      return; // no-op: ignore out-of-order or duplicate commits
    }

    // 2. Allocate exactly ONE immutable snapshot per commit.
    // Trade-off: We accept one small, short-lived allocation per write in order to
    // guarantee absolutely zero allocations across thousands of concurrent reads.
    // Because there is no CAS loop, there is no risk of allocating multiple discarded
    // objects due to thread contention.
    currentWatermark =
        new PartitionWatermark(commitPosition, previous.committedBytes() + committedBytes);

    // 3. Notify downstream components (e.g., wake up parked fetchers)
    if (onWatermarkAdvancedNotifier != null) {
      onWatermarkAdvancedNotifier.run();
    }
  }

  @Override
  public void seed(final long commitPosition) {
    final PartitionWatermark previous = currentWatermark;
    if (commitPosition <= previous.commitPosition()) {
      return; // already at or beyond the recovered position
    }
    // Preserve the running byte total (normally 0 on a fresh leader) and only move the position
    // forward to the recovered tip. Parked fetchers snapshot the byte counter at park time, so a
    // zero baseline here is correct.
    currentWatermark = new PartitionWatermark(commitPosition, previous.committedBytes());
  }

  public void setOnWatermarkAdvancedNotifier(final Runnable onWatermarkAdvancedNotifier) {
    this.onWatermarkAdvancedNotifier = onWatermarkAdvancedNotifier;
  }
}
