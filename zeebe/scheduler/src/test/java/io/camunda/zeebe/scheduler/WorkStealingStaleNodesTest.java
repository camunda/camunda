/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

/**
 * Idle actor threads must not burn CPU in {@link ActorTaskQueue#trySteal()} while another thread of
 * the same group is blocked inside an actor job.
 *
 * <p>While the owner of a queue is blocked, external submissions keep appending to its queue and
 * other threads steal them. Stolen nodes must be removed from the queue; otherwise every thief
 * walks the whole list of already claimed nodes on each steal attempt.
 */
final class WorkStealingStaleNodesTest {

  private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();

  @Test
  void shouldNotWalkStolenNodesAgainAfterFailedSteal() {
    // given -- the task is repeatedly woken up into this queue and stolen by another thread
    final var queue = new ActorTaskQueue();
    final var task = new ActorTask(new Actor() {});
    for (int i = 0; i < 100_000; i++) {
      queue.append(task);
      assertThat(queue.trySteal()).isSameAs(task);
    }

    // when
    final var stolen = queue.trySteal();

    // then -- only the tail and the node after it are left to walk; the owner still drains the
    // whole list
    assertThat(stolen).isNull();
    assertThat(countNodes(queue)).isEqualTo(2);
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void shouldStealOlderTaskBehindStaleNodes() {
    // given -- a waiting task, followed by many nodes of a task that was claimed elsewhere
    final var victim = new ActorTaskQueue();
    final var other = new ActorTaskQueue();
    final var waiting = new ActorTask(new Actor() {});
    final var claimedElsewhere = new ActorTask(new Actor() {});
    victim.append(waiting);
    for (int i = 0; i < 100; i++) {
      victim.append(claimedElsewhere);
    }
    other.append(claimedElsewhere);
    assertThat(other.pop()).isSameAs(claimedElsewhere);

    // when
    final var stolen = victim.trySteal();

    // then -- the stale nodes between the tail and the stolen node are skipped from now on
    assertThat(stolen).isSameAs(waiting);
    assertThat(countNodes(victim)).isEqualTo(3);
    assertThat(victim.trySteal()).isNull();
  }

  @Test
  void shouldNotUnlinkShortRunsOfStaleNodes() {
    // given -- a waiting task behind a few stale nodes
    final var victim = new ActorTaskQueue();
    final var other = new ActorTaskQueue();
    final var waiting = new ActorTask(new Actor() {});
    final var claimedElsewhere = new ActorTask(new Actor() {});
    victim.append(waiting);
    for (int i = 0; i < 3; i++) {
      victim.append(claimedElsewhere);
    }
    other.append(claimedElsewhere);
    assertThat(other.pop()).isSameAs(claimedElsewhere);

    // when
    final var stolen = victim.trySteal();

    // then -- the stale nodes are left in place
    assertThat(stolen).isSameAs(waiting);
    assertThat(countNodes(victim)).isEqualTo(4);
  }

  @Test
  void shouldPopTasksInOrderAfterStaleNodesWereSkipped() {
    // given
    final var queue = new ActorTaskQueue();
    final var other = new ActorTaskQueue();
    final var first = new ActorTask(new Actor() {});
    final var claimedElsewhere = new ActorTask(new Actor() {});
    final var last = new ActorTask(new Actor() {});
    queue.append(first);
    queue.append(claimedElsewhere);
    queue.append(last);
    other.append(claimedElsewhere);
    assertThat(other.pop()).isSameAs(claimedElsewhere);
    assertThat(queue.trySteal()).isSameAs(last);
    assertThat(queue.trySteal()).isSameAs(first);
    queue.append(last);

    // when + then -- the owner's view (next chain) is unaffected
    assertThat(queue.pop()).isSameAs(last);
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();
  }

  /**
   * End-to-end: block one IO thread with a long job while an external thread keeps waking up other
   * IO-bound actors (similar to exporter wakeups coming from raft / processing threads). Compares
   * CPU used by the IO thread group with and without the blocked thread.
   */
  @Test
  void shouldNotBurnCpuOnIdleIoThreadsWhileOneIoThreadIsBlocked() throws Exception {
    final var results = new ArrayList<Result>();
    for (final int ioThreads : new int[] {2, 4, 8}) {
      results.add(runScenario(ioThreads, Duration.ofSeconds(5)));
    }

    System.out.println();
    System.out.println("ioThreads | baseline cores | blocked cores | max stale nodes in a queue");
    for (final var r : results) {
      System.out.printf(
          "%9d | %14.2f | %13.2f | %,d%n",
          r.ioThreads, r.baselineCores, r.blockedCores, r.maxStaleNodes);
    }

    for (final var r : results) {
      assertThat(r.maxStaleNodes)
          .describedAs("stale nodes, %d IO threads", r.ioThreads)
          .isLessThan(1_000);
      // without the fix, the other ioThreads - 1 threads each spin at ~1 core
      assertThat(r.blockedCores)
          .describedAs("cores used while one of %d IO threads is blocked", r.ioThreads)
          .isLessThan(r.baselineCores + 0.5 * (r.ioThreads - 1));
    }
  }

  private Result runScenario(final int ioThreads, final Duration window) throws Exception {
    final var builder =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("ws-" + ioThreads)
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(ioThreads);
    final var scheduler = builder.build();
    scheduler.start();

    final var targets = new ArrayList<PingActor>();
    for (int i = 0; i < 4; i++) {
      final var target = new PingActor();
      scheduler.submitActor(target, SchedulingHints.ioBound()).join();
      targets.add(target);
    }
    final var blocker = new BlockingActor();
    scheduler.submitActor(blocker, SchedulingHints.ioBound()).join();

    // external producer: wakes up IO actors from a non-actor thread, so every wakeup is appended
    // to the queue of a random IO thread
    final var running = new AtomicBoolean(true);
    final var feeder =
        Thread.ofPlatform()
            .name("external-feeder")
            .start(
                () -> {
                  while (running.get()) {
                    targets.forEach(PingActor::ping);
                    LockSupport.parkNanos(50_000);
                  }
                });

    try {
      final var ioThreadIds = ioThreadIds("zb-fs-workers");
      final var queues = ioQueues(builder.getIoBoundActorThreads());

      // baseline: same external load, nothing blocked
      Thread.sleep(500);
      final long baselineCpu = measureCpu(ioThreadIds, window, () -> {});

      // blocked: one IO thread runs a job that blocks for longer than the measuring window
      final long blockMs = window.toMillis() + 1_000;
      final var blockStarted = new CountDownLatch(1);
      blocker.block(blockMs, blockStarted);
      blockStarted.await(5, TimeUnit.SECONDS);
      final long blockerTid = blocker.blockedThreadId.get();
      final var maxStale = new AtomicLong();
      final long blockedCpu =
          measureCpu(
              ioThreadIds.stream().filter(id -> id != blockerTid).toList(),
              window,
              () -> {
                for (final var q : queues) {
                  maxStale.accumulateAndGet(countNodes(q), Math::max);
                }
              });

      return new Result(
          ioThreads,
          baselineCpu / (double) window.toNanos(),
          blockedCpu / (double) window.toNanos(),
          maxStale.get());
    } finally {
      running.set(false);
      feeder.join();
      // let the blocked job finish so the scheduler can stop
      blocker.awaitUnblocked();
      scheduler.stop().get(30, TimeUnit.SECONDS);
    }
  }

  /** Sums the CPU time of the given threads over the window, sampling while waiting. */
  private static long measureCpu(
      final List<Long> threadIds, final Duration window, final Runnable sampler) {
    final long before = cpuOf(threadIds);
    final long end = System.nanoTime() + window.toNanos();
    while (System.nanoTime() < end) {
      sampler.run();
      LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
    }
    return cpuOf(threadIds) - before;
  }

  private static long cpuOf(final List<Long> threadIds) {
    long sum = 0;
    for (final long id : threadIds) {
      final long cpu = THREAD_MX.getThreadCpuTime(id);
      if (cpu > 0) {
        sum += cpu;
      }
    }
    return sum;
  }

  private static List<Long> ioThreadIds(final String prefix) {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.getName().startsWith(prefix))
        .map(Thread::threadId)
        .toList();
  }

  private static ActorTaskQueue[] ioQueues(final ActorThreadGroup group) throws Exception {
    final Field queuesField = WorkStealingGroup.class.getDeclaredField("taskQueues");
    queuesField.setAccessible(true);
    return (ActorTaskQueue[]) queuesField.get(group.tasks);
  }

  /** Counts the nodes trySteal() walks from tail to head. Racy, but fine for sampling. */
  private static long countNodes(final ActorTaskQueue queue) {
    long count = 0;
    ActorTaskQueueNode node = queue.tail;
    final ActorTaskQueueNode head = queue.head;
    while (node != null && node != head) {
      count++;
      node = node.prev;
    }
    return count;
  }

  private record Result(
      int ioThreads, double baselineCores, double blockedCores, long maxStaleNodes) {}

  private static final class PingActor extends Actor {
    private long pings;

    void ping() {
      actor.run(() -> pings++);
    }
  }

  private static final class BlockingActor extends Actor {
    private final AtomicLong blockedThreadId = new AtomicLong(-1);
    private final CountDownLatch unblocked = new CountDownLatch(1);

    void block(final long millis, final CountDownLatch started) {
      actor.run(
          () -> {
            blockedThreadId.set(Thread.currentThread().threadId());
            started.countDown();
            try {
              // stands in for slow blocking file I/O (e.g. snapshot checkpoint or deletion)
              Thread.sleep(millis);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              unblocked.countDown();
            }
          });
    }

    void awaitUnblocked() throws InterruptedException {
      unblocked.await(30, TimeUnit.SECONDS);
    }
  }
}
