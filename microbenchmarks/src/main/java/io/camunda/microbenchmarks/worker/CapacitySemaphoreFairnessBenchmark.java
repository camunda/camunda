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
 * Reproduction harness for <a href="https://github.com/camunda/camunda/issues/59734">#59734</a>:
 * the job worker's shared capacity semaphore is constructed non-fair ({@code new
 * Semaphore(maxActivate)} in {@code BlockingExecutor}).
 *
 * <p>The point of this benchmark is to answer, with numbers rather than by code read, whether the
 * two delivery paths that contend for that semaphore starve each other, and whether making the
 * semaphore fair ({@code new Semaphore(n, true)}) changes the split. It models the two paths <em>as
 * they dispatch after the #59632 refactor</em>, which is the crux — the original issue was written
 * against the pre-#59632 code where the arbitration was the other way around:
 *
 * <ul>
 *   <li><b>push</b> — {@code JobWorkerImpl#handleStreamedJob} hands a pushed job to {@code
 *       BlockingExecutor#execute}, a <em>timed blocking</em> {@code tryAcquire(timeout, unit)} that
 *       parks the caller until a permit frees. A parked waiter is what a fair semaphore orders.
 *   <li><b>poll</b> — {@code JobWorkerImpl#handleJob} hands a poll-delivered job to {@code
 *       BlockingExecutor#executeWithoutWaiting}, the <em>untimed</em> {@code tryAcquire()} that, by
 *       the {@link Semaphore} contract, <em>barges</em> past parked waiters "even when this
 *       semaphore has been set to use a fair ordering policy". The poll path therefore never parks:
 *       it tries once and, on a miss, the job is refused back to the broker.
 * </ul>
 *
 * <p>What to read from the output. The {@code push:pushAcquired} and {@code poll:pollAcquired}
 * auxiliary counters are each path's share of acquisitions at saturation. Because the poll path
 * barges, the expectation — and the finding this harness exists to establish — is that the poll
 * path keeps its share in <em>both</em> the {@code fair=false} and {@code fair=true} runs. If that
 * holds, then (a) the poll/backlog-drain path is not the one being starved on this branch, and (b)
 * a plain fair semaphore does not re-arbitrate the two paths, so the one-line {@code new
 * Semaphore(n, true)} fix does not do what the issue assumes. Both feed the explicit decision the
 * issue asks for before any fix is benchmarked.
 *
 * <p>The two paths are given the same number of threads on purpose: any asymmetry in the resulting
 * split then reflects the semaphore's arbitration policy and the acquire discipline, not an uneven
 * allocation of producer threads. The permit count is kept well below the total thread count so the
 * semaphore is genuinely contended; a permit is held for a short, fixed spin so it stays taken long
 * enough for the two paths to actually race for it.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms512M", "-Xmx512M"})
public class CapacitySemaphoreFairnessBenchmark {

  private static final int HOLD_SPIN_TOKENS = 500;
  private static final long PUSH_TIMEOUT_MILLIS = 50;

  public static void main(final String[] args) throws RunnerException {
    final Options options =
        new OptionsBuilder()
            .include(CapacitySemaphoreFairnessBenchmark.class.getSimpleName())
            .build();
    new Runner(options).run();
  }

  /**
   * Models the pushed-job path: a timed blocking acquire that parks until a permit frees. This is
   * the party a fair semaphore is meant to protect from barging.
   */
  @Benchmark
  @Group("contention")
  @GroupThreads(8)
  public void push(final Capacity capacity, final PushCounters counters)
      throws InterruptedException {
    if (capacity.semaphore.tryAcquire(PUSH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
      counters.pushAcquired++;
      hold();
      capacity.semaphore.release();
    } else {
      counters.pushTimedOut++;
    }
  }

  /**
   * Models the poll-delivered-job path: the untimed {@code tryAcquire()} that barges past parked
   * push waiters even on a fair semaphore, and never parks itself.
   */
  @Benchmark
  @Group("contention")
  @GroupThreads(8)
  public void poll(final Capacity capacity, final PollCounters counters) {
    if (capacity.semaphore.tryAcquire()) {
      counters.pollAcquired++;
      hold();
      capacity.semaphore.release();
    } else {
      counters.pollRefused++;
    }
  }

  private static void hold() {
    // A held permit stands in for a job handler running while its slot is taken. Without a hold the
    // semaphore would never actually be contended and neither path could starve the other.
    Blackhole.consumeCPU(HOLD_SPIN_TOKENS);
  }

  /** The shared capacity permits, mirroring {@code BlockingExecutor}'s single semaphore. */
  @State(Scope.Group)
  public static class Capacity {

    @Param({"false", "true"})
    public boolean fair;

    @Param({"8"})
    public int permits;

    Semaphore semaphore;

    @Setup(Level.Iteration)
    public void setUp() {
      semaphore = new Semaphore(permits, fair);
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
