/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ReadSnapshot} guarded by an atomic reference count, so several {@link ReadOnlyView}s and
 * their readers can share one pinned snapshot without coordinating who closes it: the underlying
 * snapshot is closed exactly once, by whichever holder releases last.
 *
 * <p>The count starts at 1 — the creator owns the initial reference and hands it to the first view.
 * Once the count has dropped to zero the snapshot is closed and can never be resurrected: {@link
 * #retain()} throws instead of re-incrementing from zero, and {@link #release()} throws on a double
 * release (releasing more than was retained is always a caller bug worth surfacing).
 *
 * <p><b>Threading:</b> {@link #retain()} and {@link #release()} are safe from any thread; the
 * transitions are lock-free CAS loops. Note that retaining is only race-free while the caller
 * already holds a reference (directly or via a publication lock such as {@link ViewPublisher}) —
 * retaining a snapshot that a concurrent release may drop to zero can throw.
 */
final class RefCountedSnapshot {

  private final ReadSnapshot snapshot;
  private final AtomicInteger references = new AtomicInteger(1);

  RefCountedSnapshot(final ReadSnapshot snapshot) {
    this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
  }

  ReadSnapshot snapshot() {
    return snapshot;
  }

  /**
   * Adds one reference.
   *
   * @throws IllegalStateException if the count already dropped to zero (the snapshot is closed)
   */
  void retain() {
    while (true) {
      final int current = references.get();
      if (current == 0) {
        throw new IllegalStateException(
            "expected a live snapshot to retain, but it was already released to zero (closed)");
      }
      if (references.compareAndSet(current, current + 1)) {
        return;
      }
    }
  }

  /**
   * Drops one reference; closes the underlying snapshot when the count reaches zero.
   *
   * @throws IllegalStateException on a double release (count already zero)
   */
  void release() {
    while (true) {
      final int current = references.get();
      if (current == 0) {
        throw new IllegalStateException(
            "expected a live snapshot to release, but it was already released to zero (closed)");
      }
      if (references.compareAndSet(current, current - 1)) {
        if (current == 1) {
          snapshot.close();
        }
        return;
      }
    }
  }
}
