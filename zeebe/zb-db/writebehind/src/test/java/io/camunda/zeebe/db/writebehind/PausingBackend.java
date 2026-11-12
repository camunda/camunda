/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.RawDbAccess;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * A {@link RawBackend} that can pause one thread when it weighs an entry, to force an interleaving
 * of threads in a test. The store weighs an entry while it publishes a commit, under its publish
 * lock, and the cache weighs one before it inserts it.
 */
final class PausingBackend implements Backend<RawKey, HeldValue, DbValue> {

  private final RawBackend delegate;
  private volatile @Nullable Pause pause;

  PausingBackend(final RawDbAccess storage) {
    delegate = new RawBackend(storage);
  }

  /**
   * Pauses {@code thread} the next time it weighs {@code key}, or any key if it is null, until the
   * returned pause is released.
   */
  Pause pauseOnWeigh(final Thread thread, final @Nullable RawKey key) {
    final var next = new Pause(thread, key);
    pause = next;
    return next;
  }

  @Override
  public int weigh(final RawKey key, final @Nullable HeldValue value) {
    final var current = pause;
    if (current != null && current.matches(key)) {
      pause = null;
      current.pauseHere();
    }
    return delegate.weigh(key, value);
  }

  @Override
  public long groupOf(final RawKey key) {
    return delegate.groupOf(key);
  }

  @Override
  public @Nullable HeldValue get(final RawKey key, final DbValue prototype) {
    return delegate.get(key, prototype);
  }

  @Override
  public boolean exists(final RawKey key) {
    return delegate.exists(key);
  }

  @Override
  public boolean mayExist(final RawKey key) {
    return delegate.mayExist(key);
  }

  @Override
  public Batch<RawKey, HeldValue> newBatch() {
    return delegate.newBatch();
  }

  @Override
  public Cursor<RawKey, DbValue> newCursor() {
    return delegate.newCursor();
  }

  @Override
  public void copy(final HeldValue source, final DbValue target) {
    delegate.copy(source, target);
  }

  static final class Pause {
    private final Thread thread;
    private final @Nullable RawKey key;
    private final CountDownLatch reached = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    private Pause(final Thread thread, final @Nullable RawKey key) {
      this.thread = thread;
      this.key = key;
    }

    private boolean matches(final RawKey weighed) {
      return Thread.currentThread() == thread && (key == null || key.equals(weighed));
    }

    private void pauseHere() {
      reached.countDown();
      try {
        if (!released.await(30, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Expected the test to release the pause in time");
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }

    /** Waits until the paused thread reached the pause. */
    void awaitReached() throws InterruptedException {
      if (!reached.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Expected the thread to reach the pause in time");
      }
    }

    void release() {
      released.countDown();
    }
  }
}
