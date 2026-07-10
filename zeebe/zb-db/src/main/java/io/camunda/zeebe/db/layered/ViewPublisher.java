/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import java.util.Objects;

/**
 * The single distribution point handing {@link ReadOnlyView}s from the owner thread to concurrent
 * readers (query service, async checkers, position supplier). The coordinator's view listener feeds
 * {@link #publish(ReadOnlyView)}; each reader pairs {@link #acquireLatest()} with {@link
 * #release(ReadOnlyView)} around every read pass — request-driven readers grab-and-release per
 * request, actor readers may hold one acquired view and swap it when convenient.
 *
 * <p><b>Why the lock makes acquisition safe:</b> {@link #acquireLatest()} must retain the view
 * atomically with reading it — a plain {@code AtomicReference} would leave a window between the
 * read and the retain in which the snapshot could be released to zero. Both operations run under
 * this object's monitor, so while a reader is acquiring, {@code latest} is the newest published
 * view, on which the coordinator still holds its own reference (it releases the <em>previous</em>
 * view only after publishing the successor, and a publish blocks on the same monitor). The retain
 * therefore always operates on a count of at least one; {@link RefCountedSnapshot#retain()}
 * throwing on zero remains a pure safety net.
 *
 * <p><b>Threading:</b> {@link #publish(ReadOnlyView)} is called on the owner thread (the
 * coordinator's view listener); {@link #acquireLatest()} and {@link #release(ReadOnlyView)} are
 * safe from any thread.
 */
public final class ViewPublisher {

  private ReadOnlyView latest; // guarded by this

  /** Publishes a new view as the latest; wired as the coordinator's view listener. */
  public synchronized void publish(final ReadOnlyView view) {
    latest = Objects.requireNonNull(view, "view");
  }

  /**
   * The newest published view, already retained for the caller, who must {@link
   * #release(ReadOnlyView)} it after the read pass.
   *
   * @throws IllegalStateException if no view was published yet
   */
  public synchronized ReadOnlyView acquireLatest() {
    if (latest == null) {
      throw new IllegalStateException("expected a published view to acquire, but none exists yet");
    }
    latest.retain();
    return latest;
  }

  /** Releases an acquired view; the counterpart of {@link #acquireLatest()}. */
  public void release(final ReadOnlyView view) {
    view.release();
  }
}
