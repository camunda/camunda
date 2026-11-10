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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Idle actor threads must not burn CPU in {@link
 * ActorTaskQueue#trySteal(ActorTaskQueue.StealCursor)} while another thread of the same group is
 * blocked inside an actor job.
 *
 * <p>While the owner of a queue is blocked, external submissions keep appending to its queue and
 * other threads steal them. Stolen nodes stay in the queue until the owner pops them. A thief must
 * not walk the same already claimed nodes again on each steal attempt.
 *
 * <p>Several tests prove that a walk jumps over a remembered run by making a node inside that run
 * claimable again by hand, which cannot happen in a real queue: a walk that jumps does not see it,
 * a walk without the cursor does.
 */
final class WorkStealingStaleNodesTest {

  private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();

  @ParameterizedTest
  @ValueSource(ints = {100, 1_000, 10_000})
  void shouldDrainBurstsWithoutRescanningStolenTasksOrHistoricalRun(final int burstSize) {
    // given
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    final var history = new CountingTask();
    appendStale(queue, history, 10_000);
    assertThat(queue.trySteal(cursor)).isNull();
    final long historyClaims = history.claims;

    for (int burst = 0; burst < 3; burst++) {
      final var tasks = new ArrayList<CountingTask>();
      for (int i = 0; i < burstSize; i++) {
        final var task = new CountingTask();
        tasks.add(task);
        queue.append(task);
      }

      // when
      for (int i = burstSize - 1; i >= 0; i--) {
        assertThat(queue.trySteal(cursor)).isSameAs(tasks.get(i));
      }
      assertThat(queue.trySteal(cursor)).isNull();

      // then
      assertThat(tasks).allSatisfy(task -> assertThat(task.claims).isEqualTo(1));
      assertThat(history.claims).isEqualTo(historyClaims);
    }
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void shouldRememberSuccessfulWalkWithoutHistoricalRun() {
    // given
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    final var waiting = new CountingTask();
    final var stale = new CountingTask();
    queue.append(waiting);
    appendStale(queue, stale, 100);

    // when
    assertThat(queue.trySteal(cursor)).isSameAs(waiting);
    final long claims = stale.claims;
    assertThat(queue.trySteal(cursor)).isNull();

    // then
    assertThat(waiting.claims).isEqualTo(1);
    assertThat(stale.claims).isEqualTo(claims);
  }

  @Test
  void shouldMergePrefixAndHistoryWithoutSkippingLiveGap() {
    // given
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    final var history = new CountingTask();
    appendStale(queue, history, 100);
    assertThat(queue.trySteal(cursor)).isNull();
    final long historyClaims = history.claims;
    final var gap = new CountingTask();
    final var prefix = new CountingTask();
    queue.append(gap);
    queue.append(prefix);
    assertThat(queue.trySteal(cursor)).isSameAs(prefix);

    // when
    assertThat(queue.trySteal(cursor)).isSameAs(gap);
    for (int i = 0; i < 10; i++) {
      assertThat(queue.trySteal(cursor)).isNull();
    }

    // then
    assertThat(gap.claims).isEqualTo(1);
    assertThat(prefix.claims).isEqualTo(1);
    assertThat(history.claims).isEqualTo(historyClaims);
  }

  @Test
  void shouldFollowCompletedAppendBehindRememberedPrefix() {
    // given
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    appendStale(queue, new CountingTask(), 100);
    assertThat(queue.trySteal(cursor)).isNull();
    final var waiting = new CountingTask();
    queue.append(waiting);
    final var previousTail = queue.tail;
    final var task = new CountingTask();
    final var appending = new ActorTaskQueueNode();
    appending.setTask(task);
    appending.stateCount = task.getStateCount();
    queue.tail = appending;
    assertThat(queue.trySteal(cursor)).isSameAs(task);
    assertThat(queue.trySteal(cursor)).isNull();

    // when
    previousTail.nextOrdered(appending);
    appending.prevOrdered(previousTail);

    // then
    assertThat(queue.trySteal(cursor)).isSameAs(waiting);
    assertThat(queue.trySteal(cursor)).isNull();
    assertThat(task.claims).isEqualTo(1);
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void shouldPreserveLiveGapsAcrossNestedBurstsAndOwnerPops() {
    // given
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    appendStale(queue, new CountingTask(), 100);
    assertThat(queue.trySteal(cursor)).isNull();
    final var older = new CountingTask();
    final var first = new CountingTask();
    queue.append(older);
    queue.append(first);
    assertThat(queue.trySteal(cursor)).isSameAs(first);
    final var middle = new CountingTask();
    final var newest = new CountingTask();
    queue.append(middle);
    queue.append(newest);

    // when
    assertThat(queue.trySteal(cursor)).isSameAs(newest);
    assertThat(queue.pop()).isSameAs(older);
    assertThat(queue.trySteal(cursor)).isSameAs(middle);
    assertThat(queue.trySteal(cursor)).isNull();

    // then
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();
    assertThat(queue.trySteal(cursor)).isNull();
    queue.append(first);
    assertThat(queue.trySteal(cursor)).isSameAs(first);
  }

  @Test
  void shouldNotWalkStaleNodesAgainAfterFailedSteal() {
    // given -- a long run of nodes of a task that was claimed elsewhere
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    final var task = new ActorTask(new Actor() {});
    appendStale(queue, task, 100_000);

    // when -- a failed steal walks the whole run
    assertThat(queue.trySteal(cursor)).isNull();

    // then -- the cursor remembers the run from the tail down to its oldest node
    assertThat(cursor.newest).isSameAs(queue.tail);
    assertThat(cursor.oldest).isSameAs(nodeBehindTail(queue, 99_999));

    // and the next attempt jumps over it: a node in the middle is not looked at again
    makeClaimable(nodeBehindTail(queue, 50_000));
    assertThat(queue.trySteal(cursor)).isNull();
    assertThat(queue.trySteal()).describedAs("walk without cursor").isSameAs(task);

    // and the owner still drains the whole list
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void shouldStealTasksAppendedAboveAndBelowStaleRun() {
    // given -- a waiting task behind a long run of stale nodes
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    final var waiting = new ActorTask(new Actor() {});
    queue.append(waiting);
    appendStale(queue, new ActorTask(new Actor() {}), 100);

    // when / then -- the first walk finds the waiting task behind the run
    assertThat(queue.trySteal(cursor)).isSameAs(waiting);

    // and a task appended later is found on top of the remembered run
    final var later = new ActorTask(new Actor() {});
    queue.append(later);
    assertThat(queue.trySteal(cursor)).isSameAs(later);
    assertThat(queue.trySteal(cursor)).isNull();
  }

  @Test
  void shouldKeepRememberedRunWhenClaimingAboveIt() {
    // given -- a remembered run of stale nodes
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    final var staleTask = new ActorTask(new Actor() {});
    appendStale(queue, staleTask, 100);
    assertThat(queue.trySteal(cursor)).isNull();
    final var rememberedNewest = cursor.newest;
    final var rememberedOldest = cursor.oldest;

    // when -- a task appended on top is claimed before the walk reaches the run
    final var later = new ActorTask(new Actor() {});
    queue.append(later);
    assertThat(queue.trySteal(cursor)).isSameAs(later);

    // then -- the run is still remembered, and the next walk connects to it
    assertThat(cursor.newest).isSameAs(rememberedNewest);
    assertThat(cursor.oldest).isSameAs(rememberedOldest);
    makeClaimable(nodeBehindTail(queue, 50));
    assertThat(queue.trySteal(cursor)).isNull();
    assertThat(cursor.newest).isSameAs(queue.tail);
    assertThat(cursor.oldest).isSameAs(rememberedOldest);
  }

  @Test
  void shouldStealAfterOwnerPoppedRememberedRun() {
    // given -- a remembered run, then a waiting task and more stale nodes on top
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    final var staleTask = new ActorTask(new Actor() {});
    appendStale(queue, staleTask, 100);
    assertThat(queue.trySteal(cursor)).isNull();
    final var popped = new ActorTask(new Actor() {});
    queue.append(popped);

    // when -- the owner pops the whole remembered run and the task behind it
    assertThat(queue.pop()).isSameAs(popped);

    // then -- tasks appended afterwards are stolen, with or without a claim on the way
    final var first = new ActorTask(new Actor() {});
    final var second = new ActorTask(new Actor() {});
    queue.append(first);
    appendStale(queue, new ActorTask(new Actor() {}), 50);
    queue.append(second);
    assertThat(queue.trySteal(cursor)).isSameAs(second);
    assertThat(queue.trySteal(cursor)).isSameAs(first);
    assertThat(queue.trySteal(cursor)).isNull();

    // and the owner drains the rest
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();

    // and a drained queue clears the cursor
    assertThat(queue.trySteal(cursor)).isNull();
    assertThat(cursor.newest).isNull();
    assertThat(cursor.oldest).isNull();
  }

  @Test
  void shouldKeepOwnerOrder() {
    // given -- two older live tasks, followed by a long run of stale nodes
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    final var first = new ActorTask(new Actor() {});
    final var second = new ActorTask(new Actor() {});
    queue.append(first);
    queue.append(second);
    appendStale(queue, new ActorTask(new Actor() {}), 100);

    // when -- a thief takes the newest live task
    assertThat(queue.trySteal(cursor)).isSameAs(second);
    final var third = new ActorTask(new Actor() {});
    final var fourth = new ActorTask(new Actor() {});
    queue.append(third);
    queue.append(fourth);

    // then -- the owner pops the remaining live tasks in FIFO order and drains the queue
    assertThat(queue.pop()).isSameAs(first);
    assertThat(queue.pop()).isSameAs(third);
    assertThat(queue.pop()).isSameAs(fourth);
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();

    // and the drained queue is usable again, by the same thief and by the owner
    queue.append(first);
    assertThat(queue.trySteal(cursor)).isSameAs(first);
    queue.append(second);
    assertThat(queue.pop()).isSameAs(second);
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void shouldNotSkipPastNodeThatIsStillBeingAppended() {
    // given -- a waiting task behind a long run of stale nodes
    final var queue = new ActorTaskQueue();
    final var cursor = new ActorTaskQueue.StealCursor();
    final var waiting = new ActorTask(new Actor() {});
    final var staleTask = new ActorTask(new Actor() {});
    queue.append(waiting);
    appendStale(queue, staleTask, 100);

    // and an append that is in progress: the tail was swapped, but the links are not set yet
    final ActorTaskQueueNode previousTail = queue.tail;
    final var appending = new ActorTaskQueueNode();
    appending.setTask(staleTask);
    appending.stateCount = staleTask.getStateCount() - 1;
    queue.tail = appending;
    // and later appends that completed, so the unfinished link is in the middle of the chain
    appendStale(queue, staleTask, 20);

    // when
    final var stolen = queue.trySteal(cursor);

    // then -- the walk ends at the unfinished link; it remembers only what it walked
    assertThat(stolen).isNull();
    assertThat(appending.prev).isNull();
    assertThat(cursor.oldest).isSameAs(appending);

    // when -- the append completes
    previousTail.nextOrdered(appending);
    appending.prevOrdered(previousTail);

    // then -- the next walk jumps to the newly linked part and finds the waiting task
    assertThat(queue.trySteal(cursor)).isSameAs(waiting);
    assertThat(queue.pop()).isNull();
    assertThat(queue.isEmpty()).isTrue();
  }

  /**
   * End-to-end: block one IO thread with a long job while an external thread keeps waking up other
   * IO-bound actors (similar to exporter wakeups coming from raft / processing threads). Compares
   * CPU used by the IO thread group with and without the blocked thread.
   */
  @Test
  @Tag("performance")
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
    System.out.println("ioThreads | baseline cores | blocked cores | max nodes in a queue");
    for (final var r : results) {
      System.out.printf(
          "%9d | %14.2f | %13.2f | %,d%n",
          r.ioThreads, r.baselineCores, r.blockedCores, r.maxStaleNodes);
    }

    // stolen nodes stay in the queue until its owner pops them; only the CPU matters
    for (final var r : results) {
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
    final var targets = new ArrayList<PingActor>();
    final var blocker = new BlockingActor();

    // external producer: wakes up IO actors from a non-actor thread, so every wakeup is appended
    // to the queue of a random IO thread
    final var running = new AtomicBoolean(true);
    final var feederFailure = new AtomicReference<Throwable>();
    final var feeder =
        Thread.ofPlatform()
            .name("external-feeder")
            .daemon(true)
            .uncaughtExceptionHandler((thread, failure) -> feederFailure.set(failure))
            .unstarted(
                () -> {
                  while (running.get() && !Thread.currentThread().isInterrupted()) {
                    targets.forEach(PingActor::ping);
                    LockSupport.parkNanos(50_000);
                  }
                });

    final var release = new CountDownLatch(1);
    scheduler.start();
    try {
      for (int i = 0; i < 4; i++) {
        final var target = new PingActor();
        scheduler.submitActor(target, SchedulingHints.ioBound()).get(5, TimeUnit.SECONDS);
        targets.add(target);
      }
      scheduler.submitActor(blocker, SchedulingHints.ioBound()).get(5, TimeUnit.SECONDS);
      feeder.start();
      // only the IO threads of this scheduler; other test classes may run schedulers in parallel
      final var ioThreadIds = ioThreadIds(builder.getIoBoundActorThreads());
      final var queues = ioQueues(builder.getIoBoundActorThreads());

      // baseline: same external load, nothing blocked
      await()
          .atMost(Duration.ofSeconds(5))
          .until(() -> targets.stream().allMatch(t -> t.pings() > 100));
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
      assertThat(feederFailure.get()).describedAs("external feeder failure").isNull();

      return new Result(
          ioThreads,
          baselineCpu / (double) window.toNanos(),
          blockedCpu / (double) window.toNanos(),
          maxStale.get());
    } finally {
      running.set(false);
      release.countDown();
      feeder.interrupt();
      try {
        if (feeder.isAlive()) {
          assertThat(feeder.join(Duration.ofSeconds(5)))
              .describedAs("external feeder stopped")
              .isTrue();
        }
      } finally {
        scheduler.stop().get(30, TimeUnit.SECONDS);
      }
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

  /** Counts the nodes from tail to head. Racy, but fine for sampling. */
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

  /** Appends {@code count} nodes of the task, then claims it, so that all those nodes are stale. */
  private static void appendStale(
      final ActorTaskQueue queue, final ActorTask task, final int count) {
    for (int i = 0; i < count; i++) {
      queue.append(task);
    }
    assertThat(task.claim(task.getStateCount())).isTrue();
  }

  /** Returns the node {@code steps} links behind the tail, following prev. */
  private static ActorTaskQueueNode nodeBehindTail(final ActorTaskQueue queue, final int steps) {
    ActorTaskQueueNode node = queue.tail;
    for (int i = 0; i < steps; i++) {
      node = node.prev;
    }
    return node;
  }

  /** Makes a stale node claimable again; impossible in a real queue, used to detect jumps. */
  private static void makeClaimable(final ActorTaskQueueNode node) {
    node.stateCount = node.task.getStateCount();
  }

  private record Result(
      int ioThreads, double baselineCores, double blockedCores, long maxStaleNodes) {}

  private static final class CountingTask extends ActorTask {
    private long claims;

    private CountingTask() {
      super(new Actor() {});
    }

    @Override
    public boolean claim(final long stateCount) {
      claims++;
      return super.claim(stateCount);
    }
  }

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
  }
}
