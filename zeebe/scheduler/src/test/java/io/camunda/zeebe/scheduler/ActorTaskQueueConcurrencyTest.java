/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Stresses {@link ActorTaskQueue} with concurrent producers, stealing threads and optionally the
 * owner. A pool of tasks circulates: producers append ready tasks to the queue, whoever claims a
 * task hands it back. Checks that no task is claimed twice for one append, and that no appended
 * task becomes unclaimable, i.e. stealing never hides a claimable node.
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
      final var result = runRound(tasks, false);

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
      final var result = runRound(tasks, true);

      // then
      assertThat(result.error).describedAs("%d tasks", tasks).isNull();
      assertThat(result.returned).describedAs("tasks returned, %d tasks", tasks).isEqualTo(tasks);
      assertThat(result.claims).isPositive();
      assertThat(result.drained).describedAs("owner drained the queue, %d tasks", tasks).isTrue();
    }
  }

  private static Result runRound(final int taskCount, final boolean ownerPops) throws Exception {
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
    for (int i = 0; i < PRODUCERS; i++) {
      producers.add(
          Thread.ofPlatform()
              .name("producer-" + i)
              .start(
                  failOnException(
                      error,
                      () -> {
                        while (producing.get()) {
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

    final List<Thread> consumers = new ArrayList<>();
    final int consumerCount = THIEVES + (ownerPops ? 1 : 0);
    for (int i = 0; i < consumerCount; i++) {
      final boolean owner = ownerPops && i == 0;
      consumers.add(
          Thread.ofPlatform()
              .name(owner ? "owner" : "thief-" + i)
              .start(
                  failOnException(
                      error,
                      () -> {
                        while (consuming.get()) {
                          final var task = owner ? queue.pop() : queue.trySteal();
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

    Thread.sleep(ROUND.toMillis());
    producing.set(false);
    for (final var producer : producers) {
      producer.join();
    }

    // every appended task must come back; a hidden claimable node would leave it in the queue
    final long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
    while (ready.size() < taskCount && error.get() == null && System.nanoTime() < deadline) {
      Thread.sleep(1);
    }
    consuming.set(false);
    for (final var consumer : consumers) {
      consumer.join();
    }

    boolean drained = false;
    if (ownerPops) {
      // only stale nodes are left; the owner drains them
      while (queue.pop() != null) {
        error.compareAndSet(null, "claimed a task after all tasks were returned");
      }
      drained = queue.isEmpty();
    }

    return new Result(error.get(), ready.size(), claims.get(), drained);
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

  private record Result(String error, int returned, long claims, boolean drained) {}
}
