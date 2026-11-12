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
 * A {@link RawBackend} that can pause one thread when it weighs an entry or asks for the group of a
 * key, to force an interleaving of threads in a test. The store weighs an entry while it publishes
 * a commit, under its publish lock, and the cache weighs one before it inserts it. A commit asks
 * for the group of each key once while it publishes the versions, and then again while it updates
 * the key indexes.
 */
final class PausingBackend implements Backend<RawKey, HeldValue, DbValue> {

  private final RawBackend delegate;
  private volatile @Nullable Pause pause;
  private volatile @Nullable Pause groupPause;

  PausingBackend(final RawDbAccess storage) {
    delegate = new RawBackend(storage);
  }

  /**
   * Pauses {@code thread} the next time it weighs {@code key}, or any key if it is null, until the
   * returned pause is released.
   */
  Pause pauseOnWeigh(final Thread thread, final @Nullable RawKey key) {
    final var next = new Pause(thread, key, 0);
    pause = next;
    return next;
  }

  /**
   * Pauses {@code thread} when it asks for the group of {@code key} for the {@code skip + 1}th
   * time, until the returned pause is released.
   */
  Pause pauseOnGroupOf(final Thread thread, final RawKey key, final int skip) {
    final var next = new Pause(thread, key, skip);
    groupPause = next;
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
    final var current = groupPause;
    if (current != null && current.matches(key)) {
      groupPause = null;
      current.pauseHere();
    }
    return delegate.groupOf(key);
  }

  @Override
  public KeyRange<RawKey> groupRange(final long group) {
    return delegate.groupRange(group);
  }

  @Override
  public RawKey groupLastSeekKey(final long group) {
    return delegate.groupLastSeekKey(group);
  }

  @Override
  public @Nullable HeldValue get(final RawKey key) {
    return delegate.get(key);
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
    // only the paused thread changes it
    private int skip;
    private final CountDownLatch reached = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    private Pause(final Thread thread, final @Nullable RawKey key, final int skip) {
      this.thread = thread;
      this.key = key;
      this.skip = skip;
    }

    private boolean matches(final RawKey seen) {
      if (Thread.currentThread() != thread || (key != null && !key.equals(seen))) {
        return false;
      }
      return skip-- == 0;
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
