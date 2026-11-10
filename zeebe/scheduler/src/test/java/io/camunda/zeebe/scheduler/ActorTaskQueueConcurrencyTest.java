/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Stresses {@link ActorTaskQueue} with concurrent producers, stealing threads and optionally the
 * owner. A pool of tasks circulates: producers append ready tasks to the queue, whoever claims a
 * task hands it back. Each thief steals with its own {@link ActorTaskQueue.StealCursor}, like an
 * actor thread. Checks that no task is claimed twice for one append, and that no appended task
 * becomes unclaimable, i.e. a jump over a remembered run never hides a claimable node, also when
 * the owner pops remembered nodes meanwhile.
 */
final class ActorTaskQueueConcurrencyTest {

  private static final int PRODUCERS = 2;
  private static final int THIEVES = 6;
  private static final Duration ROUND = Duration.ofMillis(300);
  private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);
  private static final int[] TASK_COUNTS = {4, 64, 1024};

  @Test
  void shouldNotLoseOrDoubleClaimTasksWhileOwnerIsBlocked() throws Exception {
    for (final int tasks : TASK_COUNTS) {
      // when -- only thieves claim; nobody pops
      final var result = runRound(tasks, Owner.BLOCKED);

      // then -- every appended task was eventually stolen
      assertThat(result.error).describedAs("%d tasks", tasks).isNull();
      assertThat(result.returned).describedAs("tasks returned, %d tasks", tasks).isEqualTo(tasks);
      assertThat(result.claims).isPositive();
    }
  }

  @Test
  void shouldNotLoseOrDoubleClaimTasksWhileOwnerPops() throws Exception {
    for (final int tasks : TASK_COUNTS) {
      // when -- the owner pops while thieves steal
      final var result = runRound(tasks, Owner.POPS);

      // then
      assertThat(result.error).describedAs("%d tasks", tasks).isNull();
      assertThat(result.returned).describedAs("tasks returned, %d tasks", tasks).isEqualTo(tasks);
      assertThat(result.claims).isPositive();
      assertThat(result.drained).describedAs("owner drained the queue, %d tasks", tasks).isTrue();
    }
  }

  @Test
  void shouldNotLoseOrDoubleClaimTasksWhileOwnerPopsIntermittently() throws Exception {
    for (final int tasks : TASK_COUNTS) {
      // when -- the owner alternates between popping and being blocked, so thieves remember runs
      // that the owner pops meanwhile
      final var result = runRound(tasks, Owner.INTERMITTENT);

      // then
      assertThat(result.error).describedAs("%d tasks", tasks).isNull();
      assertThat(result.returned).describedAs("tasks returned, %d tasks", tasks).isEqualTo(tasks);
      assertThat(result.claims).isPositive();
      assertThat(result.drained).describedAs("owner drained the queue, %d tasks", tasks).isTrue();
    }
  }

  private static Result runRound(final int taskCount, final Owner ownerMode) throws Exception {
    final boolean ownerPops = ownerMode != Owner.BLOCKED;
    final var queue = new ActorTaskQueue();
    final var ready = new ConcurrentLinkedQueue<ActorTask>();
    // written before the threads start, only read afterwards
    final Map<ActorTask, AtomicBoolean> queued = new IdentityHashMap<>();
    for (int i = 0; i < taskCount; i++) {
      final var task = new ActorTask(new Actor() {});
      queued.put(task, new AtomicBoolean());
      ready.add(task);
    }

    final var producing = new AtomicBoolean(true);
    final var consuming = new AtomicBoolean(true);
    final var claims = new AtomicLong();
    final var error = new AtomicReference<String>();

    final List<Thread> producers = new ArrayList<>();
    final List<Thread> consumers = new ArrayList<>();
    try {
      for (int i = 0; i < PRODUCERS; i++) {
        producers.add(
            Thread.ofPlatform()
                .daemon(true)
                .name("producer-" + i)
                .start(
                    failOnException(
                        error,
                        () -> {
                          while (producing.get() && !Thread.currentThread().isInterrupted()) {
                            final var task = ready.poll();
                            if (task == null) {
                              Thread.onSpinWait();
                              continue;
                            }
                            if (!queued.get(task).compareAndSet(false, true)) {
                              error.compareAndSet(null, "task appended while already queued");
                            }
                            queue.append(task);
                          }
                        })));
      }

      final int consumerCount = THIEVES + (ownerPops ? 1 : 0);
      for (int i = 0; i < consumerCount; i++) {
        final boolean owner = ownerPops && i == 0;
        consumers.add(
            Thread.ofPlatform()
                .daemon(true)
                .name(owner ? "owner" : "thief-" + i)
                .start(
                    failOnException(
                        error,
                        () -> {
                          // like an actor thread: one cursor per thief for this queue
                          final var cursor = new ActorTaskQueue.StealCursor();
                          final var random = ThreadLocalRandom.current();
                          boolean popping = true;
                          long phaseEnd = System.nanoTime();
                          while (consuming.get() && !Thread.currentThread().isInterrupted()) {
                            if (owner && ownerMode == Owner.INTERMITTENT) {
                              if (System.nanoTime() > phaseEnd) {
                                popping = !popping;
                                phaseEnd = System.nanoTime() + random.nextLong(100_000, 3_000_000);
                              }
                              if (!popping) {
                                Thread.onSpinWait();
                                continue;
                              }
                            }
                            final var task = owner ? queue.pop() : queue.trySteal(cursor);
                            if (task == null) {
                              Thread.onSpinWait();
                              continue;
                            }
                            if (!queued.get(task).compareAndSet(true, false)) {
                              error.compareAndSet(null, "task claimed twice for one append");
                            }
                            claims.incrementAndGet();
                            ready.add(task);
                          }
                        })));
      }

      new CountDownLatch(1).await(ROUND.toMillis(), TimeUnit.MILLISECONDS);
      producing.set(false);
      await().atMost(DRAIN_TIMEOUT).until(() -> producers.stream().noneMatch(Thread::isAlive));

      // every appended task must come back; a hidden claimable node would leave it in the queue
      await().atMost(DRAIN_TIMEOUT).until(() -> ready.size() >= taskCount || error.get() != null);
      if (ownerPops) {
        await().atMost(DRAIN_TIMEOUT).until(() -> queue.isEmpty() || error.get() != null);
      }
      consuming.set(false);
      await().atMost(DRAIN_TIMEOUT).until(() -> consumers.stream().noneMatch(Thread::isAlive));

      return new Result(error.get(), ready.size(), claims.get(), queue.isEmpty());
    } finally {
      producing.set(false);
      consuming.set(false);
      final var workers = new ArrayList<>(producers);
      workers.addAll(consumers);
      workers.forEach(Thread::interrupt);
      joinWorkers(workers);
    }
  }

  private static void joinWorkers(final List<Thread> workers) {
    boolean interrupted = Thread.interrupted();
    final long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
    try {
      for (final var worker : workers) {
        while (worker.isAlive() && System.nanoTime() < deadline) {
          try {
            worker.join(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
          } catch (final InterruptedException e) {
            interrupted = true;
          }
        }
      }
      assertThat(workers)
          .allSatisfy(
              worker -> assertThat(worker.isAlive()).describedAs(worker.getName()).isFalse());
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** A failed queue operation only ends its own thread; record it so the round fails. */
  private static Runnable failOnException(
      final AtomicReference<String> error, final Runnable body) {
    return () -> {
      try {
        body.run();
      } catch (final Throwable t) {
        error.compareAndSet(null, Thread.currentThread().getName() + " failed: " + t);
      }
    };
  }

  private enum Owner {
    BLOCKED,
    POPS,
    INTERMITTENT
  }

  private record Result(String error, int returned, long claims, boolean drained) {}
}
