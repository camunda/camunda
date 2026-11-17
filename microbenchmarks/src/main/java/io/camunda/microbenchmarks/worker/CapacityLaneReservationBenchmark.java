/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.worker;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Reproduces the poll starvation reported in <a
 * href="https://github.com/camunda/camunda/issues/59734">#59734</a> by modelling the one thing
 * {@link CapacitySemaphoreFairnessBenchmark} leaves out: the poll path pays a network round-trip
 * that the push path does not.
 *
 * <p>With an in-process, zero-latency {@code tryAcquire()} the poll path wins, which only shows
 * that a fair semaphore is the wrong fix. In a cluster the two paths reach a freed permit at very
 * different speeds:
 *
 * <ul>
 *   <li><b>push</b> is a thread already parked in {@code BlockingExecutor#execute}'s blocking
 *       acquire. When a permit is released the JVM unparks it in microseconds, so it claims the
 *       slot almost immediately.
 *   <li><b>poll</b> only issues an {@code ActivateJobs} request when a slot frees ({@code
 *       onCapacityAvailable}); that request takes a network round-trip to come back before any
 *       {@code tryAcquire()} happens. By then a parked push has usually taken the slot, and the
 *       poll's jobs are refused back to the broker.
 * </ul>
 *
 * <p>The poll path {@link LockSupport#parkNanos parks} for a configured round-trip before its
 * untimed acquire, while the push path keeps its timed blocking acquire. Two capacity structures
 * are compared:
 *
 * <ul>
 *   <li><b>shared</b> — one {@link Semaphore} both paths draw from, as without a reserved lane.
 *       Once the round-trip is modelled, push takes nearly every freed slot, poll's share collapses
 *       and its refusals climb.
 *   <li><b>reserved</b> — total in-flight is still bounded by one semaphore of {@code N} permits
 *       (so {@code freeCapacity()} keeps its single-count meaning from #59632), but the push path
 *       must also hold a permit from a smaller {@code N - reserved} budget, leaving {@code
 *       reserved} slots only the poll path can take. Total in-flight never exceeds {@code N}.
 * </ul>
 *
 * <p>The reserved mode models the lane in its reserved state. In the client the lane is reserved
 * only while the poll is starved (a poll's jobs were refused) and released after an empty poll, a
 * few polls without a refusal, or a failed poll, so this harness measures the contended case the
 * lane exists for, not a worker that keeps up with push. Throughput cost is measured in the cluster
 * A/B recorded in {@code docs/adr/clients/0002-reserved-poll-capacity-for-job-worker.md}, not here.
 *
 * <p>{@code BlockingExecutor} is package-private in the Java client, which this module does not
 * depend on, so the acquire logic is copied here: the push takes its budget and capacity under one
 * shared deadline, the poll takes capacity only, without waiting. Keep it in step with {@code
 * BlockingExecutor#execute} and {@code BlockingExecutor#executeWithoutWaiting}.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms512M", "-Xmx512M"})
public class CapacityLaneReservationBenchmark {

  private static final int HOLD_SPIN_TOKENS = 500;
  private static final long PUSH_TIMEOUT_MILLIS = 50;

  public static void main(final String[] args) throws RunnerException {
    final Options options =
        new OptionsBuilder()
            .include(CapacityLaneReservationBenchmark.class.getSimpleName())
            .build();
    new Runner(options).run();
  }

  @Benchmark
  @Group("contention")
  @GroupThreads(8)
  public void push(final Capacity capacity, final PushCounters counters)
      throws InterruptedException {
    // Mirror the production push path: the budget and the capacity acquisition share one deadline,
    // so a contended push waits for its budget rather than being counted as an instant timeout.
    final long deadlineNanos =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PUSH_TIMEOUT_MILLIS);
    if (capacity.pushBudget != null
        && !capacity.pushBudget.tryAcquire(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS)) {
      // Push could not fit within its budget before the deadline; the remaining slots are the
      // poll's reserved lane.
      counters.pushTimedOut++;
      return;
    }
    try {
      if (capacity.total.tryAcquire(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS)) {
        counters.pushAcquired++;
        hold();
        capacity.total.release();
      } else {
        counters.pushTimedOut++;
      }
    } finally {
      if (capacity.pushBudget != null) {
        capacity.pushBudget.release();
      }
    }
  }

  private static long remainingNanos(final long deadlineNanos) {
    return Math.max(0L, deadlineNanos - System.nanoTime());
  }

  @Benchmark
  @Group("contention")
  @GroupThreads(8)
  public void poll(final Capacity capacity, final PollCounters counters) {
    // The poll only learns of a freed slot a round-trip late; by then a parked push usually took
    // it.
    if (capacity.pollRoundTripNanos > 0) {
      LockSupport.parkNanos(capacity.pollRoundTripNanos);
    }
    if (capacity.total.tryAcquire()) {
      counters.pollAcquired++;
      hold();
      capacity.total.release();
    } else {
      counters.pollRefused++;
    }
  }

  private static void hold() {
    Blackhole.consumeCPU(HOLD_SPIN_TOKENS);
  }

  /** The shared capacity, in either the single-pool or reserved-lane structure. */
  @State(Scope.Group)
  public static class Capacity {

    @Param({"shared", "reserved"})
    public String mode;

    @Param({"8"})
    public int permits;

    @Param({"2"})
    public int reserved;

    /**
     * Modelled poll round-trip. 0 matches {@link CapacitySemaphoreFairnessBenchmark}, where the
     * poll wins; a realistic value shifts freed slots to the locally parked push.
     */
    @Param({"0", "200"})
    public long pollRoundTripMicros;

    /** Bounds total in-flight in both modes: the single post-#59632 capacity authority. */
    Semaphore total;

    /** Only present in reserved mode: the smaller budget the push path must also hold. */
    Semaphore pushBudget;

    long pollRoundTripNanos;

    @Setup(Level.Iteration)
    public void setUp() {
      total = new Semaphore(permits);
      pushBudget = "reserved".equals(mode) ? new Semaphore(permits - reserved) : null;
      pollRoundTripNanos = TimeUnit.MICROSECONDS.toNanos(pollRoundTripMicros);
    }
  }

  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.EVENTS)
  public static class PushCounters {
    public long pushAcquired;
    public long pushTimedOut;

    @Setup(Level.Iteration)
    public void reset() {
      pushAcquired = 0;
      pushTimedOut = 0;
    }
  }

  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.EVENTS)
  public static class PollCounters {
    public long pollAcquired;
    public long pollRefused;

    @Setup(Level.Iteration)
    public void reset() {
      pollAcquired = 0;
      pollRefused = 0;
    }
  }
}
