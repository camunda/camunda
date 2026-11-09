/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
  void shouldKeepOwnerOrderAfterStaleRunWasUnlinked() {
    // given -- two older live tasks, followed by a long run of nodes of a task claimed elsewhere
    final var queue = new ActorTaskQueue();
    final var other = new ActorTaskQueue();
    final var first = new ActorTask(new Actor() {});
    final var second = new ActorTask(new Actor() {});
    final var claimedElsewhere = new ActorTask(new Actor() {});
    queue.append(first);
    queue.append(second);
    for (int i = 0; i < 100; i++) {
      queue.append(claimedElsewhere);
    }
    other.append(claimedElsewhere);
    assertThat(other.pop()).isSameAs(claimedElsewhere);

    // when -- a steal walks the stale run, unlinks it and takes the newest live task
    assertThat(queue.trySteal()).isSameAs(second);

    // then -- the run was unlinked: only the tail, the node after it, second and first are walked
    assertThat(countNodes(queue)).isEqualTo(4);

    // when -- more tasks are appended after the compaction
    final var third = new ActorTask(new Actor() {});
    final var fourth = new ActorTask(new Actor() {});
    queue.append(third);
    queue.append(fourth);

    // then -- the owner still pops the remaining live tasks in FIFO order and drains the queue
    assertThat(queue.pop()).isSameAs(first);
    assertThat(queue.pop()).isSameAs(third);
    assertThat(queue.pop()).isSameAs(fourth);
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();

    // and the drained queue is usable again, by thieves and by the owner
    queue.append(first);
    assertThat(queue.trySteal()).isSameAs(first);
    queue.append(second);
    assertThat(queue.pop()).isSameAs(second);
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void shouldNotSkipPastNodeThatIsStillBeingAppended() {
    // given -- a waiting task behind a long run of stale nodes
    final var queue = new ActorTaskQueue();
    final var other = new ActorTaskQueue();
    final var waiting = new ActorTask(new Actor() {});
    final var claimedElsewhere = new ActorTask(new Actor() {});
    queue.append(waiting);
    for (int i = 0; i < 100; i++) {
      queue.append(claimedElsewhere);
    }
    final long staleCount = claimedElsewhere.getStateCount();
    other.append(claimedElsewhere);
    assertThat(other.pop()).isSameAs(claimedElsewhere);

    // and an append that is in progress: the tail was swapped, but the links are not set yet
    final ActorTaskQueueNode previousTail = queue.tail;
    final var appending = new ActorTaskQueueNode();
    appending.setTask(claimedElsewhere);
    appending.stateCount = staleCount;
    queue.tail = appending;
    // and a later append that completed, so the unfinished link is in the middle of the chain; its
    // task is claimed elsewhere as well
    queue.append(claimedElsewhere);
    assertThat(claimedElsewhere.claim(claimedElsewhere.getStateCount())).isTrue();

    // when
    final var stolen = queue.trySteal();

    // then -- the walk stops at the unfinished link without writing past it
    assertThat(stolen).isNull();
    assertThat(appending.prev).isNull();
    assertThat(countNodes(queue)).isEqualTo(2);

    // when -- the append completes
    previousTail.nextOrdered(appending);
    appending.prevOrdered(previousTail);

    // then -- the waiting task is reachable again, and the owner drains the whole list
    assertThat(queue.trySteal()).isSameAs(waiting);
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
    assumeTrue(THREAD_MX.isThreadCpuTimeSupported(), "thread CPU time is not supported");
    if (!THREAD_MX.isThreadCpuTimeEnabled()) {
      THREAD_MX.setThreadCpuTimeEnabled(true);
    }

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

    final var release = new CountDownLatch(1);
    try {
      // only the IO threads of this scheduler; other test classes may run schedulers in parallel
      final var ioThreadIds = ioThreadIds(builder.getIoBoundActorThreads());
      final var queues = ioQueues(builder.getIoBoundActorThreads());

      // baseline: same external load, nothing blocked
      Thread.sleep(500);
      final long baselineCpu = measureCpu(ioThreadIds, window, () -> {});

      // blocked: one IO thread runs a job that stays blocked until released
      final var blockStarted = new CountDownLatch(1);
      blocker.block(blockStarted, release);
      assertThat(blockStarted.await(5, TimeUnit.SECONDS))
          .describedAs("blocking job started")
          .isTrue();
      final long blockerTid = blocker.blockedThreadId.get();
      final long[] pingsBefore = targets.stream().mapToLong(PingActor::pings).toArray();
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

      // the owner stayed blocked for the whole window, and stealing kept every actor running: a
      // wakeup that lands in the blocked thread's queue only runs if it is stolen
      assertThat(blocker.isBlocked()).describedAs("blocking job still running").isTrue();
      for (int i = 0; i < targets.size(); i++) {
        assertThat(targets.get(i).pings())
            .describedAs("jobs of actor %d run while an IO thread is blocked", i)
            .isGreaterThan(pingsBefore[i] + 100);
      }

      return new Result(
          ioThreads,
          baselineCpu / (double) window.toNanos(),
          blockedCpu / (double) window.toNanos(),
          maxStale.get());
    } finally {
      running.set(false);
      feeder.join();
      // let the blocked job finish so the scheduler can stop
      release.countDown();
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
      assertThat(cpu).describedAs("CPU time of thread %d", id).isNotNegative();
      sum += cpu;
    }
    return sum;
  }

  private static List<Long> ioThreadIds(final ActorThreadGroup group) {
    return Arrays.stream(group.threads).map(Thread::threadId).toList();
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
    private final AtomicLong pings = new AtomicLong();

    void ping() {
      actor.run(pings::incrementAndGet);
    }

    long pings() {
      return pings.get();
    }
  }

  private static final class BlockingActor extends Actor {
    private final AtomicLong blockedThreadId = new AtomicLong(-1);
    private final CountDownLatch unblocked = new CountDownLatch(1);

    void block(final CountDownLatch started, final CountDownLatch release) {
      actor.run(
          () -> {
            blockedThreadId.set(Thread.currentThread().threadId());
            started.countDown();
            try {
              // stands in for slow blocking file I/O (e.g. snapshot checkpoint or deletion)
              release.await(60, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              unblocked.countDown();
            }
          });
    }

    boolean isBlocked() {
      return unblocked.getCount() > 0;
    }

    void awaitUnblocked() throws InterruptedException {
      unblocked.await(30, TimeUnit.SECONDS);
    }
  }
}
