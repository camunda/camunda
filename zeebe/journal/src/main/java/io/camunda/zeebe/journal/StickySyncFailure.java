/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal;

import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The first failed sync of a partition's data, after which none of it can be persisted anymore.
 *
 * <p>After a failed fsync or msync, the kernel marks the affected pages as clean and reports the
 * error only once, so a later sync succeeds without writing them, while reading them back still
 * returns what is in the page cache. Everything that writes this data checks for a failure here
 * first and fails instead, so that nothing written since is treated as persisted.
 *
 * <p>Only the first failure is kept. The listener learns of it exactly once: on the thread that
 * records it, or on the thread that sets the listener if the failure was recorded before.
 */
public final class StickySyncFailure {
  private volatile @Nullable Throwable failure;
  private Consumer<Throwable> listener = ignored -> {};

  /** Returns the first recorded failure, or null if no sync failed. */
  public @Nullable Throwable get() {
    return failure;
  }

  /** Records the failure, unless one was recorded before, and notifies the listener of it. */
  public void record(final Throwable cause) {
    final Consumer<Throwable> toNotify;
    synchronized (this) {
      if (failure != null) {
        return;
      }
      failure = cause;
      toNotify = listener;
    }
    toNotify.accept(cause);
  }

  /**
   * Sets the listener that learns of the first failure, at once if it was recorded already.
   *
   * @param listener called with the failure, possibly from another thread
   */
  public void setListener(final Consumer<Throwable> listener) {
    Objects.requireNonNull(listener, "must specify a listener");
    final Throwable recorded;
    synchronized (this) {
      this.listener = listener;
      recorded = failure;
    }
    if (recorded != null) {
      listener.accept(recorded);
    }
  }
}
