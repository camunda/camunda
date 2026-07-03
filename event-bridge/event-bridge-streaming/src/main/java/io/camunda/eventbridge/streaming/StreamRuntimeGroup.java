/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs several supervised {@link StreamRuntime} members in parallel, one per thread — the runtime's
 * unit of horizontal scaling.
 *
 * <p>A single {@link StreamRuntime} is deliberately single-threaded: it owns one consumer and
 * processes one record at a time across the partitions that consumer is assigned. Parallelism does
 * not come from fanning those partitions out to worker threads (which would force every poll cycle
 * to wait for its slowest partition); it comes from running several members, each with its own
 * consumer and its own poll/process/commit loop. All members join the same consumer group under
 * distinct instance ids, so the group coordinator splits the source partitions across them. Each
 * member is then a single-writer over its own partitions — no locks, no shared state — and the
 * loops are fully independent: a slow partition delays only the member that owns it, never the
 * others. The effective parallelism is bounded by the source partition count (extra members get no
 * assignment).
 *
 * <p><b>Supervision.</b> Each member runs under a supervisor that distinguishes an intentional stop
 * from a crash: a member that returns cleanly (a graceful {@link StreamRuntime#stop() stop}, or its
 * own fail-fast on a fatal record) is left stopped, but one whose loop throws — e.g. a failure
 * while subscribing or restoring, or an {@link Error} — is logged and restarted after a backoff
 * with a fresh member (new consumer, so the coordinator reassigns its partitions). Without this a
 * member thread could die silently and its partition slice go dark. Threads are non-daemon and
 * carry an uncaught-exception handler as a last resort, so the JVM will not exit skipping a
 * member's final produce-before-commit; call {@link #close()} (e.g. from a shutdown hook) to stop
 * the group.
 *
 * <p>The caller supplies a factory that builds a fully-configured member for each index in {@code
 * [0, parallelism)}, and again for each restart. Each member must be given a distinct {@link
 * StreamRuntime.Builder#instanceId instance id} and its own state/offset backends; the framework
 * does not partition those for you (member-local state is what keeps the members lock-free).
 */
public final class StreamRuntimeGroup implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(StreamRuntimeGroup.class);
  private static final Duration DEFAULT_RESTART_BACKOFF = Duration.ofSeconds(1);
  private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
  private static final long INTERRUPT_GRACE_MS = 5_000;

  private final List<SupervisedMember> members;
  private final long shutdownTimeoutMs;
  private final List<Thread> threads = new ArrayList<>();
  private boolean started;

  private StreamRuntimeGroup(final List<SupervisedMember> members, final long shutdownTimeoutMs) {
    this.members = members;
    this.shutdownTimeoutMs = shutdownTimeoutMs;
  }

  /**
   * Builds {@code parallelism} members via {@code memberFactory} with default backoff/shutdown
   * timings. Nothing runs until {@link #start()}.
   */
  public static StreamRuntimeGroup of(
      final int parallelism, final IntFunction<StreamRuntime<?>> memberFactory) {
    return of(parallelism, memberFactory, DEFAULT_RESTART_BACKOFF, DEFAULT_SHUTDOWN_TIMEOUT);
  }

  /**
   * Builds {@code parallelism} members via {@code memberFactory} (called once per index in {@code
   * [0, parallelism)}, and again per restart), tuning how long a crashed member waits before
   * restarting and how long {@link #close()} waits for a member to stop before interrupting it.
   */
  public static StreamRuntimeGroup of(
      final int parallelism,
      final IntFunction<StreamRuntime<?>> memberFactory,
      final Duration restartBackoff,
      final Duration shutdownTimeout) {
    if (parallelism < 1) {
      throw new IllegalArgumentException("parallelism must be >= 1, was " + parallelism);
    }
    final long backoffMs = restartBackoff.toMillis();
    final List<SupervisedMember> members = new ArrayList<>(parallelism);
    for (int i = 0; i < parallelism; i++) {
      // Build the first generation eagerly so a misconfigured factory fails fast, before start().
      members.add(new SupervisedMember(i, memberFactory, memberFactory.apply(i), backoffMs));
    }
    return new StreamRuntimeGroup(members, shutdownTimeout.toMillis());
  }

  /** Starts every member on its own supervised thread. Returns once launched, not joined. */
  public void start() {
    if (started) {
      throw new IllegalStateException("already started");
    }
    started = true;
    for (final SupervisedMember member : members) {
      final Thread thread = newThread(member);
      threads.add(thread);
      thread.start();
    }
    LOG.info("Stream runtime group started with {} member(s)", members.size());
  }

  /**
   * Signals every member to stop and waits — up to the shutdown timeout per member — for its loop
   * to finish its final commit and exit, interrupting any member that overruns.
   */
  @Override
  public void close() {
    members.forEach(SupervisedMember::stop);
    for (int i = 0; i < threads.size(); i++) {
      joinMember(i, threads.get(i));
    }
    LOG.info("Stream runtime group stopped");
  }

  private void joinMember(final int index, final Thread thread) {
    try {
      thread.join(shutdownTimeoutMs);
      if (thread.isAlive()) {
        LOG.warn(
            "Stream runtime member {} did not stop within {}ms; interrupting",
            index,
            shutdownTimeoutMs);
        thread.interrupt();
        thread.join(INTERRUPT_GRACE_MS);
        if (thread.isAlive()) {
          LOG.error("Stream runtime member {} is still alive after interrupt", index);
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while awaiting stream runtime group shutdown");
    }
  }

  private static Thread newThread(final SupervisedMember member) {
    // One place for the thread policy: stable name, non-daemon (so a member drains before JVM
    // exit),
    // and an uncaught handler as a backstop should anything escape the supervisor itself.
    final Thread thread = new Thread(member, "eb-stream-" + member.index);
    thread.setDaemon(false);
    thread.setUncaughtExceptionHandler(
        (t, error) -> LOG.error("Uncaught error on stream runtime thread {}", t.getName(), error));
    return thread;
  }

  /** Supervises one member index: runs it, and restarts a crashed member with a fresh instance. */
  private static final class SupervisedMember implements Runnable {

    private final int index;
    private final IntFunction<StreamRuntime<?>> factory;
    private final long restartBackoffMs;
    private final CountDownLatch stopSignal = new CountDownLatch(1);

    private volatile StreamRuntime<?> current;
    private volatile boolean stopped;

    SupervisedMember(
        final int index,
        final IntFunction<StreamRuntime<?>> factory,
        final StreamRuntime<?> initial,
        final long restartBackoffMs) {
      this.index = index;
      this.factory = factory;
      current = initial;
      this.restartBackoffMs = restartBackoffMs;
    }

    @Override
    public void run() {
      while (!stopped) {
        if (current == null && !rebuild()) {
          break; // stop requested during backoff
        }
        final boolean threw = runCurrent();
        current = null;
        if (stopped) {
          break;
        }
        if (!threw) {
          // A clean exit without a stop request means the member stopped itself (a fatal record
          // under a fail-fast policy). Restarting would only replay into the same failure.
          LOG.error(
              "Stream runtime member {} exited on its own without a stop request; not restarting",
              index);
          break;
        }
        LOG.warn("Stream runtime member {} crashed; restarting in {}ms", index, restartBackoffMs);
        if (awaitStopOrBackoff()) {
          break; // stop requested during backoff
        }
      }
    }

    /** Runs the current member to completion; returns {@code true} if it exited by throwing. */
    private boolean runCurrent() {
      try {
        current.run();
        return false;
      } catch (final Throwable t) {
        LOG.error("Stream runtime member {} terminated abnormally", index, t);
        return true;
      }
    }

    /** Builds a fresh member for a restart; retries after a backoff if the factory itself fails. */
    private boolean rebuild() {
      while (!stopped) {
        try {
          current = factory.apply(index);
          return true;
        } catch (final RuntimeException e) {
          LOG.error("Stream runtime member {} could not be rebuilt; retrying", index, e);
          if (awaitStopOrBackoff()) {
            return false;
          }
        }
      }
      return false;
    }

    /** Waits out the restart backoff; returns {@code true} if a stop was requested meanwhile. */
    private boolean awaitStopOrBackoff() {
      try {
        return stopSignal.await(restartBackoffMs, TimeUnit.MILLISECONDS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return true;
      }
    }

    void stop() {
      stopped = true;
      stopSignal.countDown(); // wake a member waiting out its restart backoff
      final StreamRuntime<?> member = current;
      if (member != null) {
        member.stop();
      }
    }
  }
}
