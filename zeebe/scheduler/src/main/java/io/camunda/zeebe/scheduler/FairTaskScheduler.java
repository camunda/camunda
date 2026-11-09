/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;
import org.agrona.concurrent.IdleStrategy;

/**
 * SPIKE: replaces {@link WorkStealingGroup} and {@link ActorTaskQueue}.
 *
 * <ul>
 *   <li>One shared FIFO queue per group. A task is in it at most once (the task's scheduling state
 *       guarantees one submit per wakeup), and taking it removes it: no stale nodes, no claims.
 *   <li>One "run next" slot per worker: an actor woken from a job on worker T runs next on T, with
 *       warm caches (like Go's runnext and Tokio's LIFO slot). A worker takes at most {@link
 *       #MAX_RUN_NEXT_STREAK} tasks in a row from its slot before it serves the shared queue, so
 *       chains of actor hops cannot starve other work. Other workers may steal a slot that has been
 *       occupied for longer than {@link #STEAL_DELAY_NS}.
 *   <li>CPU-quota friendly: at most {@code target} workers are awake (not parked) at a time, so the
 *       actor threads never try to use more CPU than the quota allows. Idle workers park right away
 *       (no spinning by default) on an idle stack; new work wakes one of them.
 *   <li>Blocking compensation (like Go's sysmon): a monitor samples the CPU time of workers that
 *       have been in one task for longer than {@link #BLOCK_THRESHOLD_NS}. A worker that barely
 *       used CPU meanwhile is blocked (I/O, JNI, a lock), so one more worker may be awake while it
 *       is. Long tasks that do burn CPU are not compensated.
 * </ul>
 */
final class FairTaskScheduler implements TaskScheduler {
  static final int MAX_RUN_NEXT_STREAK = Integer.getInteger("zeebe.actor.runNextStreak", 3);
  static final long STEAL_DELAY_NS = TimeUnit.MICROSECONDS.toNanos(10);
  static final long BLOCK_THRESHOLD_NS =
      TimeUnit.MICROSECONDS.toNanos(Long.getLong("zeebe.actor.blockThresholdMicros", 2000));
  // IO-bound actors are expected to block: compensate them sooner
  static final long IO_BLOCK_THRESHOLD_NS =
      TimeUnit.MICROSECONDS.toNanos(Long.getLong("zeebe.actor.ioBlockThresholdMicros", 500));
  private static final long MONITOR_PERIOD_NS = TimeUnit.MICROSECONDS.toNanos(1000);
  private static final int MAX_SPINS = Integer.getInteger("zeebe.actor.idleSpins", 0);
  private static final long MAX_PARK_NS = TimeUnit.MILLISECONDS.toNanos(100);
  private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();
  private static final boolean DEBUG = Boolean.getBoolean("zeebe.actor.debug");
  private static final java.nio.file.Path CGROUP_CPU_STAT =
      java.nio.file.Path.of("/sys/fs/cgroup/cpu.stat");

  private final ConcurrentLinkedQueue<ActorTask> queue = new ConcurrentLinkedQueue<>();
  private final AtomicReferenceArray<ActorTask> runNext;
  private final AtomicLongArray runNextSince;
  private final int[] runNextStreak;
  private final ConcurrentLinkedDeque<Thread> parked = new ConcurrentLinkedDeque<>();
  private final AtomicInteger spinning = new AtomicInteger();
  private final int workers;

  // awake workers are limited to `allowed`: the target plus one per blocked (compensated) worker
  private final AtomicInteger allowed;
  private final AtomicInteger awake;
  private final int maxAllowed;
  private final AtomicLongArray busySince;
  private final AtomicIntegerArray compensated;
  private final AtomicIntegerArray runningIoBound;
  private final boolean[] excess;
  private volatile Thread[] threads = new Thread[0];
  private volatile Thread monitor;
  private volatile boolean running;
  // debug counters
  private final java.util.concurrent.atomic.LongAdder compensations =
      new java.util.concurrent.atomic.LongAdder();
  private final java.util.concurrent.atomic.LongAdder excessParks =
      new java.util.concurrent.atomic.LongAdder();
  private final java.util.concurrent.atomic.LongAdder movedRunNext =
      new java.util.concurrent.atomic.LongAdder();

  /**
   * @param workers worker threads in the group
   * @param target how many of them may be awake at once, unless some are blocked
   */
  FairTaskScheduler(final int workers, final int target) {
    this.workers = workers;
    runNext = new AtomicReferenceArray<>(workers);
    runNextSince = new AtomicLongArray(workers);
    runNextStreak = new int[workers];
    allowed = new AtomicInteger(Math.min(target, workers));
    awake = new AtomicInteger(workers);
    maxAllowed = workers;
    busySince = new AtomicLongArray(workers);
    compensated = new AtomicIntegerArray(workers);
    runningIoBound = new AtomicIntegerArray(workers);
    excess = new boolean[workers];
  }

  /** Starts the blocking monitor, if there are spare workers to compensate with. */
  void start(final Thread[] workerThreads) {
    threads = workerThreads;
    if (maxAllowed > allowed.get() && THREAD_MX.isCurrentThreadCpuTimeSupported()) {
      running = true;
      monitor = Thread.ofPlatform().daemon().name("zb-actors-monitor").start(this::monitorLoop);
    }
  }

  void stop() {
    running = false;
    final Thread m = monitor;
    if (m != null) {
      LockSupport.unpark(m);
    }
  }

  /** Submit from outside the group, or a task re-submitting itself after its turn. */
  void enqueue(final ActorTask task) {
    queue.offer(task);
    if (spinning.get() == 0) {
      wakeOne();
    }
  }

  /** Submit from a job running on worker {@code workerId}: the task runs next on that worker. */
  void pushRunNext(final int workerId, final ActorTask task) {
    runNextSince.set(workerId, System.nanoTime());
    final ActorTask displaced = runNext.getAndSet(workerId, task);
    if (displaced != null) {
      enqueue(displaced);
    }
  }

  private void wakeOne() {
    if (awake.get() < allowed.get()) {
      final Thread worker = parked.pollFirst();
      if (worker != null) {
        LockSupport.unpark(worker);
      }
    }
  }

  @Override
  public ActorTask getNextTask() {
    final int id = ActorThread.current().getRunnerId();
    // the previous task, if any, is done
    busySince.set(id, 0);
    if (compensated.get(id) == 1 && compensated.compareAndSet(id, 1, 0)) {
      allowed.decrementAndGet();
    }
    if (awake.get() > allowed.get()) {
      // more workers awake than allowed (a blocked worker came back): this one goes to sleep,
      // and hands the task it would have run next to the others
      excess[id] = true;
      excessParks.increment();
      moveRunNextToQueue(id);
      return null;
    }

    final ActorTask task = selectTask(id);
    if (task != null) {
      runningIoBound.set(id, task.ioBound ? 1 : 0);
      busySince.set(id, System.nanoTime());
    }
    return task;
  }

  private ActorTask selectTask(final int id) {
    ActorTask task;
    if (runNextStreak[id] < MAX_RUN_NEXT_STREAK
        && runNext.get(id) != null
        && (task = runNext.getAndSet(id, null)) != null) {
      runNextStreak[id]++;
      return task;
    }

    runNextStreak[id] = 0;
    if ((task = queue.poll()) != null) {
      return task;
    }
    if (runNext.get(id) != null && (task = runNext.getAndSet(id, null)) != null) {
      return task;
    }
    return stealRunNext(id);
  }

  private ActorTask stealRunNext(final int self) {
    final long now = System.nanoTime();
    for (int i = 0; i < workers; i++) {
      if (i == self) {
        continue;
      }
      final ActorTask task = runNext.get(i);
      if (task != null
          && now - runNextSince.get(i) > STEAL_DELAY_NS
          && runNext.compareAndSet(i, task, null)) {
        return task;
      }
    }
    return null;
  }

  private long thresholdOf(final int worker) {
    return runningIoBound.get(worker) == 1 ? IO_BLOCK_THRESHOLD_NS : BLOCK_THRESHOLD_NS;
  }

  private void moveRunNextToQueue(final int id) {
    final ActorTask task = runNext.getAndSet(id, null);
    if (task != null) {
      movedRunNext.increment();
      queue.offer(task);
    }
  }

  private boolean hasPendingWork() {
    if (!queue.isEmpty()) {
      return true;
    }
    for (int i = 0; i < workers; i++) {
      if (runNext.get(i) != null) {
        return true;
      }
    }
    return false;
  }

  /** Detects blocked workers by their CPU time and lets a spare worker run meanwhile. */
  private void monitorLoop() {
    final long[] lastCpu = new long[workers];
    final long[] lastSample = new long[workers];
    final long[] sampledBusySince = new long[workers];
    final int[] lowCpuSamples = new int[workers];
    long lastThrottled = readThrottledPeriods();
    long nextDebug = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (running) {
      LockSupport.parkNanos(this, MONITOR_PERIOD_NS);
      final Thread[] ts = threads;
      final long now = System.nanoTime();
      // a throttled cgroup stops every thread: such an interval says nothing about blocking.
      // Only read the cgroup when some worker is a candidate (reading it costs a few syscalls).
      boolean candidates = false;
      for (int i = 0; i < ts.length && !candidates; i++) {
        final long since = busySince.get(i);
        candidates = since != 0 && now - since >= thresholdOf(i);
      }
      boolean wasThrottled = false;
      if (candidates) {
        final long throttled = readThrottledPeriods();
        wasThrottled = throttled != lastThrottled;
        lastThrottled = throttled;
      }
      for (int i = 0; i < ts.length; i++) {
        final long since = busySince.get(i);
        if (since == 0 || now - since < thresholdOf(i)) {
          sampledBusySince[i] = 0;
          lowCpuSamples[i] = 0;
          continue;
        }
        final long cpu = THREAD_MX.getThreadCpuTime(ts[i].threadId());
        if (sampledBusySince[i] == since && cpu >= 0 && !wasThrottled) {
          final long wall = now - lastSample[i];
          // blocked: used less than a tenth of the elapsed time on CPU, twice in a row (once for
          // IO-bound actors, which are expected to block)
          lowCpuSamples[i] = cpu - lastCpu[i] < wall / 10 ? lowCpuSamples[i] + 1 : 0;
          final int needed = runningIoBound.get(i) == 1 ? 1 : 2;
          if (lowCpuSamples[i] >= needed && compensated.compareAndSet(i, 0, 1)) {
            if (allowed.incrementAndGet() > maxAllowed) {
              allowed.decrementAndGet();
              compensated.set(i, 0);
            } else {
              compensations.increment();
              moveRunNextToQueue(i);
              if (hasPendingWork()) {
                wakeOne();
              }
            }
          }
        } else {
          lowCpuSamples[i] = 0;
        }
        sampledBusySince[i] = since;
        lastCpu[i] = cpu;
        lastSample[i] = now;
      }
      if (DEBUG && now >= nextDebug) {
        nextDebug = now + TimeUnit.SECONDS.toNanos(5);
        System.out.printf(
            "[fair-scheduler] allowed=%d awake=%d queue=%d compensations=%d excessParks=%d"
                + " movedRunNext=%d%n",
            allowed.get(),
            awake.get(),
            queue.size(),
            compensations.sum(),
            excessParks.sum(),
            movedRunNext.sum());
      }
    }
  }

  private static long readThrottledPeriods() {
    try {
      for (final String line : java.nio.file.Files.readAllLines(CGROUP_CPU_STAT)) {
        if (line.startsWith("nr_throttled ")) {
          return Long.parseLong(line.substring("nr_throttled ".length()).trim());
        }
      }
    } catch (final Exception e) {
      // not in a cgroup v2 container
    }
    return 0;
  }

  IdleStrategy newIdleStrategy() {
    return new FairIdleStrategy();
  }

  /** Optionally spin briefly, then park on the idle stack until new work arrives. */
  private final class FairIdleStrategy implements IdleStrategy {
    private int spins;
    private boolean countedAsSpinning;

    @Override
    public void idle(final int workCount) {
      if (workCount > 0) {
        reset();
      } else {
        idle();
      }
    }

    @Override
    public void idle() {
      final int id = ActorThread.current().getRunnerId();
      if (!excess[id] && spins < MAX_SPINS) {
        if (!countedAsSpinning) {
          spinning.incrementAndGet();
          countedAsSpinning = true;
        }
        spins++;
        Thread.onSpinWait();
        return;
      }

      stopSpinning();
      final Thread self = Thread.currentThread();
      final boolean mustSleep = excess[id];
      excess[id] = false;
      awake.decrementAndGet();
      parked.push(self);
      // re-check after registering, so a task enqueued meanwhile is not missed
      if (mustSleep || !hasPendingWork()) {
        LockSupport.parkNanos(this, MAX_PARK_NS);
      }
      parked.remove(self);
      awake.incrementAndGet();
      spins = 0;
    }

    @Override
    public void reset() {
      // a spinning worker that found work hands the spinning role on, so tasks enqueued while it
      // spun (which woke nobody) get a worker too
      final boolean wasSpinning = countedAsSpinning;
      stopSpinning();
      spins = 0;
      if (wasSpinning && !queue.isEmpty() && spinning.get() == 0) {
        wakeOne();
      }
    }

    private void stopSpinning() {
      if (countedAsSpinning) {
        spinning.decrementAndGet();
        countedAsSpinning = false;
      }
    }

    @Override
    public String alias() {
      return "fair";
    }
  }
}
