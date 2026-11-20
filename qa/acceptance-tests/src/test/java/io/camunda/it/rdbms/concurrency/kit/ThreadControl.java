/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A thread doing database work that the test drives step by step. The thread stops at every sync
 * point: before each statement, before each entry of a JDBC batch, and before commit (see {@link
 * SyncPoint}).
 *
 * <p>Started by {@link RdbmsConcurrencyKit#startFlush} and friends, the thread stops at its first
 * sync point. Then:
 *
 * <ul>
 *   <li>{@link #stepTo} runs it until it stops before a matching sync point; it fails if the thread
 *       ends, or waits for a lock, before getting there (setup: the scenario is not as intended);
 *   <li>{@link #runTo} does the same, but returns how far the thread got ({@link Progress}) for the
 *       test to assert on, e.g. that it does not wait for a lock;
 *   <li>{@link #step} runs its next statement; if that statement waits for a lock, the test just
 *       goes on with the other threads;
 *   <li>{@link #release} lets it run to the end; {@link #waitUntilDone} waits for that.
 * </ul>
 *
 * <p>All public methods are called from the test thread only.
 */
public final class ThreadControl {

  /** How long a statement may take before the thread counts as waiting for a lock. */
  private static final Duration STEP_TIMEOUT = Duration.ofSeconds(3);

  /** Long enough for every database's deadlock detection (SQL Server checks every 5 s). */
  private static final Duration DONE_TIMEOUT = Duration.ofSeconds(60);

  private final String name;
  private final Supplier<String> lockWaits;
  private final Semaphore reached = new Semaphore(0);
  private final Semaphore proceed = new Semaphore(0);
  private final List<SyncPoint> history = new CopyOnWriteArrayList<>();
  private volatile Thread thread;
  private volatile boolean syncing = true;
  private volatile boolean done;
  private volatile Throwable workError;
  private volatile Throwable statementError;
  private volatile SyncPoint syncPoint;

  /** Whether the thread is stopped at {@link #syncPoint}; only used by the test thread. */
  private boolean stopped;

  private ThreadControl(final String name, final Supplier<String> lockWaits) {
    this.name = name;
    this.lockWaits = lockWaits;
  }

  static ThreadControl start(final String name, final Supplier<String> lockWaits, final Work work) {
    final ThreadControl control = new ThreadControl(name, lockWaits);
    control.thread =
        new Thread(
            () -> {
              ControlledStatements.CONTROL.set(control);
              try {
                work.run();
              } catch (final Throwable t) {
                control.workError = t;
              } finally {
                ControlledStatements.CONTROL.remove();
                control.done = true;
                control.reached.release();
              }
            },
            name);
    control.thread.start();
    return control;
  }

  // ------------------------------------------------------------- called by the controlled thread

  void sync(final SyncPoint point) {
    history.add(point);
    if (!syncing) {
      return;
    }
    syncPoint = point;
    reached.release();
    proceed.acquireUninterruptibly();
  }

  void statementFailed(final Throwable failure) {
    if (statementError == null) {
      statementError = failure;
    }
  }

  // ------------------------------------------------------------- called by the test

  /**
   * Runs the thread's next statement. Returns when it stopped before the following one, ended, or
   * waits for a lock held by another thread (then go on with that thread).
   */
  public void step() throws InterruptedException {
    if (awaitStop()) {
      stopped = false;
      proceed.release();
      awaitStop();
    }
  }

  /**
   * Setup: runs the thread until it stops before a matching sync point (see {@link SyncPoint} for
   * the matchers); returns at once if it already stopped there.
   *
   * @throws IllegalStateException if the thread ends or waits for a lock before getting there: the
   *     scenario does not run as expected; the message lists the statements it ran and the lock
   *     waits. When getting there (or not) is what the test checks, use {@link #runTo} and assert.
   */
  public void stepTo(final SyncPoint.Match match) throws InterruptedException {
    final Progress progress = runTo(match);
    if (!progress.reached()) {
      throw new IllegalStateException(progress.toString());
    }
  }

  /**
   * Runs the thread until it stops before a matching sync point, like {@link #stepTo}, and reports
   * how far it got instead of failing: reached, waits for a lock, or ended. For the expectation of
   * a test, e.g. {@code assertThat(flush.runTo(commit()).waitsForLock()).isFalse()}.
   */
  public Progress runTo(final SyncPoint.Match match) throws InterruptedException {
    while (awaitStop() && !match.test(syncPoint)) {
      step();
    }
    if (stopped) {
      return new Progress(name, match, Progress.Outcome.REACHED, "", report());
    }
    return done
        ? new Progress(name, match, Progress.Outcome.ENDED, "", report())
        : new Progress(name, match, Progress.Outcome.WAITS_FOR_LOCK, lockWaits.get(), report());
  }

  /**
   * Lets the thread run to the end without stopping again, and returns at once: release every
   * thread first, then {@link #waitUntilDone} each, so two threads waiting for each other's locks
   * can run into the database's deadlock detection.
   */
  public void release() {
    syncing = false;
    stopped = false;
    proceed.release();
  }

  /** Waits up to 60 s for the end of the work; true if it ended. */
  public boolean waitUntilDone() throws InterruptedException {
    thread.join(DONE_TIMEOUT.toMillis());
    return done;
  }

  public String name() {
    return name;
  }

  /** The sync point the thread stopped at last; null before the first one. */
  public SyncPoint syncPoint() {
    return syncPoint;
  }

  /** Every sync point the thread passed, also after release. */
  public List<SyncPoint> history() {
    return history;
  }

  /**
   * The first failed statement, or else the exception the work ended with. The exporter catches a
   * failed flush and retries later, so for a flush only the failed statement tells.
   */
  public Throwable error() {
    return statementError != null ? statementError : workError;
  }

  /** Name, error and the sync points passed, for assertion messages. */
  public String report() {
    final StringBuilder text =
        new StringBuilder(name)
            .append(error() == null ? " (no error)" : " failed: " + error())
            .append("\n  steps:");
    for (final SyncPoint point : history) {
      text.append("\n    ").append(point);
    }
    return text.toString();
  }

  /**
   * Waits for the thread to stop at a sync point; true if it did, false if it ended or its
   * statement still runs after the step timeout, i.e. it waits for a lock.
   */
  private boolean awaitStop() throws InterruptedException {
    if (!stopped && !done && reached.tryAcquire(STEP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
      stopped = !done;
    }
    return stopped;
  }

  @FunctionalInterface
  public interface Work {
    void run() throws Exception;
  }
}
