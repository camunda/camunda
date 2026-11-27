/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.cpu;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.sun.management.ThreadMXBean;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;
import org.agrona.collections.Long2LongHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Charges the CPU time of the threads of each registered {@link ThreadGroup}, and of its subgroups,
 * against a CPU budget. It only measures: it stops no work. The broker registers one group per
 * physical tenant; registered groups must not be nested.
 *
 * <p>The budget of a sample is the time since the last sample times the quota of the group. CPU
 * time above the budget is kept as debt, up to what the group repays at its quota within the
 * maximum repay time; unused budget is not saved. Only CPU time used after registration is charged.
 * A thread whose CPU time cannot be read is charged nothing until it can be read again, from that
 * read on.
 */
public final class ThreadGroupCpuSampler implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadGroupCpuSampler.class);
  private static final long NO_CPU_TIME = -1;
  // a failed read drops the baseline: the next read must not charge the thread from zero
  private static final long UNREAD_CPU_TIME = -2;
  private static final int INITIAL_THREAD_CAPACITY = 64;

  private final Duration period;
  private final long maxRepayNanos;
  private final UnaryOperator<long[]> threadCpuNanos;
  private final LongSupplier nanoClock;
  private final ThreadGroup rootGroup = rootGroup();
  private final Logger sampleFailureLog = new ThrottledLogger(LOG, Duration.ofMinutes(1));
  private final Logger readFailureLog = new ThrottledLogger(LOG, Duration.ofMinutes(1));
  // read without the lock by usage()
  private final Map<String, Account> accounts = new ConcurrentHashMap<>();

  @GuardedBy("this")
  private final Map<ThreadGroup, Account> accountsByGroup = new IdentityHashMap<>();

  @GuardedBy("this")
  private final List<Account> owners = new ArrayList<>();

  @GuardedBy("this")
  private Thread[] threads;

  @GuardedBy("this")
  private long[] ids = new long[0];

  @GuardedBy("this")
  private @Nullable ScheduledExecutorService executor;

  /**
   * @param maxRepayTime bounds the debt of a group, so that the group repays it within this time at
   *     its quota
   * @throws IllegalStateException if the JVM does not support or measure thread CPU time
   */
  public ThreadGroupCpuSampler(final Duration period, final Duration maxRepayTime) {
    this(
        period,
        maxRepayTime,
        cpuTimeSourceOf((ThreadMXBean) ManagementFactory.getThreadMXBean()),
        INITIAL_THREAD_CAPACITY,
        System::nanoTime);
  }

  /**
   * @param threadCpuNanos returns the CPU time of the threads with the given ids, in the same
   *     order, or a negative value for a thread whose CPU time cannot be read. It must not keep or
   *     modify the array.
   */
  @VisibleForTesting
  ThreadGroupCpuSampler(
      final Duration period,
      final Duration maxRepayTime,
      final UnaryOperator<long[]> threadCpuNanos,
      final int initialThreadCapacity,
      final LongSupplier nanoClock) {
    this.period = period;
    maxRepayNanos = maxRepayTime.toNanos();
    this.threadCpuNanos = threadCpuNanos;
    this.nanoClock = nanoClock;
    threads = new Thread[initialThreadCapacity];
  }

  /**
   * Registers the group under the id, or changes the quota of a registered id. A new quota applies
   * from the last sample on and keeps the debt.
   *
   * @param cpus the CPU quota of the group, for example 1.5
   * @throws IllegalArgumentException if the quota is not positive, if the id is registered with
   *     another group, or if the group is registered under another id
   */
  public synchronized void register(final String id, final ThreadGroup group, final double cpus) {
    if (!Double.isFinite(cpus) || cpus <= 0) {
      throw new IllegalArgumentException(
          "Cannot register thread group '%s' with a CPU quota of %s: the quota must be a positive number of CPUs"
              .formatted(group.getName(), cpus));
    }
    final var registered = accounts.get(id);
    if (registered != null && registered.group == group) {
      registered.updateCpus(cpus);
      return;
    }
    if (registered != null || accountsByGroup.containsKey(group)) {
      throw new IllegalArgumentException(
          "Cannot register thread group '%s' under id '%s': the id or the group is already registered"
              .formatted(group.getName(), id));
    }
    final var account = new Account(group, cpus);
    accounts.put(id, account);
    accountsByGroup.put(group, account);
    try {
      account.startFrom(readCpuNanos());
    } catch (final RuntimeException e) {
      // a retry must read a new baseline, not only change the quota
      accounts.remove(id);
      accountsByGroup.remove(group);
      throw e;
    }
  }

  public synchronized void unregister(final String id) {
    final var account = accounts.remove(id);
    if (account != null) {
      accountsByGroup.remove(account.group);
    }
  }

  /** Returns the usage of the last sample, or empty if the group was not sampled yet. */
  public Optional<CpuUsage> usage(final String id) {
    final var account = accounts.get(id);
    return account == null ? Optional.empty() : Optional.ofNullable(account.usage);
  }

  public synchronized void sample() {
    final var readNanos = readCpuNanos();
    for (final var account : accounts.values()) {
      account.sample(readNanos);
    }
  }

  /** Samples each period on a thread of the root group, so it is charged to no group. */
  public synchronized void start() {
    if (executor != null) {
      return;
    }
    executor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              final var thread = new Thread(rootGroup, r, "zb-cpu-sampler");
              thread.setDaemon(true);
              return thread;
            });
    executor.scheduleAtFixedRate(
        this::sampleAndLogFailure, period.toNanos(), period.toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  public synchronized void close() {
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  private void sampleAndLogFailure() {
    // a task that throws is never scheduled again
    try {
      sample();
    } catch (final RuntimeException e) {
      sampleFailureLog.warn("Failed to sample the CPU time of the thread groups", e);
    }
  }

  /** Reads the CPU time of the threads of all groups into their accounts; returns the read time. */
  @GuardedBy("this")
  private long readCpuNanos() {
    final var readNanos = nanoClock.getAsLong();
    for (final var account : accounts.values()) {
      account.startReading();
    }

    forgetLastRead();
    final int count = enumerateThreads();
    final int matched = matchThreadsToAccounts(count);
    if (matched == 0) {
      return readNanos;
    }
    final var cpuNanos = threadCpuNanos.apply(idsOfMatchedThreads(matched));
    final int failed = chargeOwners(cpuNanos);
    if (failed == matched) {
      readFailureLog.warn(
          "Failed to read the CPU time of all {} sampled threads; was thread CPU time measurement disabled?",
          matched);
    }
    return readNanos;
  }

  /** Clears the buffers of the last read, so that it keeps no ended thread or old account. */
  @GuardedBy("this")
  private void forgetLastRead() {
    Arrays.fill(threads, null);
    owners.clear();
  }

  /** Enumerates all live threads into {@code threads}; returns their number. */
  @GuardedBy("this")
  private int enumerateThreads() {
    // since JDK 21 a thread group does not track its threads, so enumerate all threads once
    int count;
    while ((count = rootGroup.enumerate(threads, true)) == threads.length) {
      threads = new Thread[threads.length * 2];
    }
    return count;
  }

  /**
   * Moves the threads of registered groups to the front of {@code threads} and adds their accounts
   * to {@code owners}, in the same order; returns their number.
   */
  @GuardedBy("this")
  private int matchThreadsToAccounts(final int count) {
    int matched = 0;
    for (int i = 0; i < count; i++) {
      final var account = accountOf(threads[i].getThreadGroup());
      if (account != null) {
        threads[matched++] = threads[i];
        owners.add(account);
      }
    }
    return matched;
  }

  @GuardedBy("this")
  private long[] idsOfMatchedThreads(final int matched) {
    // the source reads every id, so the array must have exactly the matched length
    if (ids.length != matched) {
      ids = new long[matched];
    }
    for (int i = 0; i < matched; i++) {
      ids[i] = threads[i].threadId();
    }
    return ids;
  }

  /** Charges each matched thread to its owner; returns the number of failed reads. */
  @GuardedBy("this")
  private int chargeOwners(final long[] cpuNanos) {
    int failed = 0;
    for (int i = 0; i < owners.size(); i++) {
      if (cpuNanos[i] >= 0) {
        owners.get(i).charge(ids[i], cpuNanos[i]);
      } else {
        owners.get(i).skipFailedRead(ids[i]);
        failed++;
      }
    }
    return failed;
  }

  @GuardedBy("this")
  private @Nullable Account accountOf(final @Nullable ThreadGroup group) {
    for (var g = group; g != null; g = g.getParent()) {
      final var account = accountsByGroup.get(g);
      if (account != null) {
        return account;
      }
    }
    return null;
  }

  private static ThreadGroup rootGroup() {
    var group = Thread.currentThread().getThreadGroup();
    while (group.getParent() != null) {
      group = group.getParent();
    }
    return group;
  }

  @VisibleForTesting
  static UnaryOperator<long[]> cpuTimeSourceOf(final ThreadMXBean bean) {
    if (!bean.isThreadCpuTimeSupported() || !bean.isThreadCpuTimeEnabled()) {
      throw new IllegalStateException(
          "Cannot sample the CPU time of thread groups: thread CPU time is not supported or disabled");
    }
    return bean::getThreadCpuTime;
  }

  /**
   * Error Prone cannot see that the callers hold the sampler monitor, so each method takes it
   * again; it is reentrant, so this is free.
   */
  private final class Account {
    private final ThreadGroup group;
    private volatile @Nullable CpuUsage usage;

    @GuardedBy("ThreadGroupCpuSampler.this")
    private double cpus;

    @GuardedBy("ThreadGroupCpuSampler.this")
    private Long2LongHashMap lastCpuNanos = new Long2LongHashMap(NO_CPU_TIME);

    @GuardedBy("ThreadGroupCpuSampler.this")
    private Long2LongHashMap currentCpuNanos = new Long2LongHashMap(NO_CPU_TIME);

    @GuardedBy("ThreadGroupCpuSampler.this")
    private long usedNanos;

    @GuardedBy("ThreadGroupCpuSampler.this")
    private long lastReadNanos;

    @GuardedBy("ThreadGroupCpuSampler.this")
    private long debtNanos;

    private Account(final ThreadGroup group, final double cpus) {
      this.group = group;
      this.cpus = cpus;
    }

    private void updateCpus(final double newCpus) {
      synchronized (ThreadGroupCpuSampler.this) {
        cpus = newCpus;
      }
    }

    private void startReading() {
      synchronized (ThreadGroupCpuSampler.this) {
        currentCpuNanos.clear();
        usedNanos = 0;
      }
    }

    private void charge(final long threadId, final long cpuNanos) {
      synchronized (ThreadGroupCpuSampler.this) {
        final var last = lastCpuNanos.get(threadId);
        currentCpuNanos.put(threadId, cpuNanos);
        if (last != UNREAD_CPU_TIME) {
          // a thread without a last read is new, so it is charged from zero
          usedNanos += cpuNanos - Math.max(0, last);
        }
      }
    }

    private void skipFailedRead(final long threadId) {
      synchronized (ThreadGroupCpuSampler.this) {
        currentCpuNanos.put(threadId, UNREAD_CPU_TIME);
      }
    }

    /** Makes the last read the baseline of the next sample. */
    private void startFrom(final long readNanos) {
      synchronized (ThreadGroupCpuSampler.this) {
        final var previous = lastCpuNanos;
        lastCpuNanos = currentCpuNanos;
        currentCpuNanos = previous;
        lastReadNanos = readNanos;
      }
    }

    private void sample(final long readNanos) {
      synchronized (ThreadGroupCpuSampler.this) {
        final var budget = (long) ((readNanos - lastReadNanos) * cpus);
        final var maxDebt = (long) (maxRepayNanos * cpus);
        debtNanos = Math.clamp(debtNanos + usedNanos - budget, 0, maxDebt);
        usage = new CpuUsage(usedNanos, budget, debtNanos);
        startFrom(readNanos);
      }
    }
  }
}
