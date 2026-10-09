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

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies that actors on independent schedulers derived from one another interact correctly:
 * timers fire on the thread of the actor's own scheduler, can be cancelled on request of actors of
 * other schedulers, and futures and messages wake actors on the right scheduler.
 *
 * <p>Timers use the real clock with short delays: the timer wheel advances one tick per scheduler
 * loop, so a controlled clock cannot jump over large durations in reasonable time.
 */
final class CrossSchedulerTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final long CANCELLED_TIMER_DELAY_MS = 800;

  private final List<ActorScheduler> schedulers = new ArrayList<>();
  private ActorScheduler base;

  @BeforeEach
  void setUp() {
    base =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(2)
            .setIoBoundActorThreadCount(1)
            .build();
    base.start();
    schedulers.add(base);
  }

  @AfterEach
  void tearDown() {
    for (final var scheduler : schedulers) {
      try {
        scheduler.stop().get(10, TimeUnit.SECONDS);
      } catch (final IllegalStateException ignored) {
        // already stopped by the test
      } catch (final Exception e) {
        throw new AssertionError(e);
      }
    }
  }

  @Test
  void shouldFireTimersOnThreadsOfTheOwningScheduler() {
    // given
    final var other = newScheduler("other-", 2, 1);
    final var actor = new TestActor("other-zb-actors-");
    actor.onStart =
        () -> {
          actor.scheduleOnce("delayed", 20);
          actor.scheduleAtTimestamp("stamped", 20);
          actor.scheduleRecurring("recurring", 10, 3);
        };

    // when
    other.submitActor(actor).join();

    // then
    await().atMost(TIMEOUT).until(() -> actor.fireCount("recurring") == 3);
    await()
        .atMost(TIMEOUT)
        .until(() -> actor.fireCount("delayed") + actor.fireCount("stamped") == 2);
    assertThat(actor.violations).isEmpty();
  }

  @Test
  void shouldFireTimersOfIoBoundActorsOnTheIoThreadsOfTheOwningScheduler() {
    // given
    final var other = newScheduler("other-", 1, 1);
    final var actor = new TestActor("other-zb-fs-workers-");
    actor.onStart = () -> actor.scheduleOnce("delayed", 20);

    // when
    other.submitActor(actor, SchedulingHints.ioBound()).join();

    // then
    await().atMost(TIMEOUT).until(() -> actor.fireCount("delayed") == 1);
    assertThat(actor.violations).isEmpty();
  }

  @Test
  void shouldCancelTimerOnRequestOfActorOnOtherScheduler() {
    // given
    final var other = newScheduler("other-", 1, 1);
    final var owner = new TestActor("other-zb-actors-");
    final var requester = new TestActor("zb-actors-");
    owner.onStart =
        () -> {
          owner.scheduleOnce("cancelled", CANCELLED_TIMER_DELAY_MS);
          owner.scheduleOnce("kept", CANCELLED_TIMER_DELAY_MS);
        };
    base.submitActor(requester).join();
    other.submitActor(owner).join();

    // when - an actor on another scheduler asks the owner to cancel the timer
    requester.actor.run(() -> owner.actor.run(() -> owner.cancel("cancelled")));

    // then
    await().atMost(TIMEOUT).until(() -> owner.fireCount("kept") == 1);
    assertThat(owner.fireCount("cancelled")).isZero();
    assertThat(owner.violations).isEmpty();
  }

  @Test
  void shouldRunFutureCallbackOnSchedulerOfTheWaitingActor() {
    // given
    final var other = newScheduler("other-", 1, 1);
    final var waiter = new TestActor("other-zb-actors-");
    final var completer = new TestActor("zb-actors-");
    final ActorFuture<Void> future = new CompletableActorFuture<>();
    waiter.onStart =
        () -> waiter.actor.runOnCompletion(future, (ok, error) -> waiter.fired("completed"));
    other.submitActor(waiter).join();
    base.submitActor(completer).join();

    // when - the future is completed from a thread of another scheduler
    completer.actor.run(() -> future.complete(null));

    // then
    await().atMost(TIMEOUT).until(() -> waiter.fireCount("completed") == 1);
    assertThat(waiter.violations).isEmpty();
  }

  @Test
  void shouldKeepSchedulersIndependentWhenOneIsStopped() throws Exception {
    // given
    final var other = newScheduler("other-", 1, 1);
    final var survivor = new TestActor("zb-actors-");
    survivor.onStart = () -> survivor.scheduleOnce("delayed", 100);
    base.submitActor(survivor).join();
    other.submitActor(new TestActor("other-zb-actors-")).join();

    // when
    other.stop().get(10, TimeUnit.SECONDS);

    // then
    await().atMost(TIMEOUT).until(() -> survivor.fireCount("delayed") == 1);
    assertThat(survivor.violations).isEmpty();
  }

  /**
   * Randomly distributes actors over several schedulers, schedules timers, cancels some of them on
   * request of actors of other schedulers, and sends messages between actors. The seed is part of
   * the test name; to reproduce a failure, run the test with that seed only.
   */
  @ParameterizedTest(name = "seed {0}")
  @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
  void shouldHonorTimersAndMessagesAcrossRandomSchedulers(final long seed) throws Exception {
    final var random = new Random(seed);

    // given - 2 to 4 schedulers (the base one included) and 4 to 12 actors spread across them
    final var prefixes = new ArrayList<String>();
    prefixes.add("");
    final var schedulerCount = 2 + random.nextInt(3);
    for (int i = 1; i < schedulerCount; i++) {
      final var prefix = "s" + i + "-";
      prefixes.add(prefix);
      newScheduler(prefix, 1 + random.nextInt(3), 1 + random.nextInt(2));
    }

    final var startedAt = System.nanoTime();
    final var actors = new ArrayList<TestActor>();
    final var oneShots = new ArrayList<String>();
    final var recurring = new ArrayList<String>();
    final var cancelled = new ArrayList<String>();
    final var actorCount = 4 + random.nextInt(9);
    for (int i = 0; i < actorCount; i++) {
      final var schedulerIdx = random.nextInt(schedulerCount);
      final var io = random.nextInt(4) == 0;
      final var group = prefixes.get(schedulerIdx) + (io ? "zb-fs-workers-" : "zb-actors-");
      final var actor = new TestActor(group);
      final var plan = new ArrayList<Runnable>();
      for (int t = 0; t < random.nextInt(6); t++) {
        final var id = actor.actorName + "#" + t;
        switch (random.nextInt(4)) {
          case 0 -> {
            oneShots.add(id);
            final var delay = 10 + random.nextInt(190);
            plan.add(() -> actor.scheduleOnce(id, delay));
          }
          case 1 -> {
            oneShots.add(id);
            final var delay = 10 + random.nextInt(190);
            plan.add(() -> actor.scheduleAtTimestamp(id, delay));
          }
          case 2 -> {
            recurring.add(id);
            final var period = 10 + random.nextInt(50);
            plan.add(() -> actor.scheduleRecurring(id, period, 3));
          }
          default -> {
            cancelled.add(id);
            plan.add(() -> actor.scheduleOnce(id, CANCELLED_TIMER_DELAY_MS));
          }
        }
      }
      actor.onStart = () -> plan.forEach(Runnable::run);
      actors.add(actor);
      schedulers
          .get(schedulerIdx)
          .submitActor(actor, io ? SchedulingHints.ioBound() : SchedulingHints.cpuBound())
          .join();
    }

    // when - timers are cancelled on request of a random actor, usually on another scheduler
    for (final var id : cancelled) {
      final var owner =
          actors.stream().filter(a -> id.startsWith(a.actorName + "#")).findFirst().get();
      final var requester = actors.get(random.nextInt(actorCount));
      requester.actor.run(() -> owner.actor.run(() -> owner.cancel(id)));
    }

    // and - messages are sent between random actors and run on the receiver's scheduler
    final var messages = 50 + random.nextInt(100);
    final var received = new AtomicInteger();
    for (int i = 0; i < messages; i++) {
      final var from = actors.get(random.nextInt(actorCount));
      final var to = actors.get(random.nextInt(actorCount));
      from.actor.run(
          () ->
              to.actor.run(
                  () -> {
                    to.checkThread("message");
                    received.incrementAndGet();
                  }));
    }

    // then - every message is delivered, every due timer fires on its own scheduler's thread
    await().atMost(TIMEOUT).until(() -> received.get() == messages);
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(oneShots).allMatch(id -> fireCount(actors, id) == 1);
              assertThat(recurring).allMatch(id -> fireCount(actors, id) == 3);
            });

    // and - cancelled timers do not fire, not even after they would have been due
    final var elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    final var remainingMs = Math.max(0, CANCELLED_TIMER_DELAY_MS * 3 / 2 - elapsedMs);
    await().pollDelay(Duration.ofMillis(remainingMs)).until(() -> true);
    assertThat(cancelled).allMatch(id -> fireCount(actors, id) == 0);
    assertThat(oneShots).allMatch(id -> fireCount(actors, id) == 1);
    assertThat(recurring).allMatch(id -> fireCount(actors, id) == 3);
    assertThat(actors).allSatisfy(actor -> assertThat(actor.violations).isEmpty());

    // and - actors and schedulers shut down in random order
    final var closes = new ArrayList<ActorFuture<Void>>();
    actors.forEach(actor -> closes.add(actor.closeAsync()));
    closes.forEach(close -> close.join(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
    final var toStop = new ArrayList<>(schedulers);
    Collections.shuffle(toStop, random);
    for (final var scheduler : toStop) {
      scheduler.stop().get(10, TimeUnit.SECONDS);
    }
  }

  private static int fireCount(final List<TestActor> actors, final String id) {
    return actors.stream().mapToInt(actor -> actor.fireCount(id)).sum();
  }

  private ActorScheduler newScheduler(
      final String prefix, final int cpuThreads, final int ioThreads) {
    final var scheduler =
        base.derive()
            .setThreadNamePrefix(prefix)
            .setCpuBoundActorThreadCount(cpuThreads)
            .setIoBoundActorThreadCount(ioThreads)
            .build();
    scheduler.start();
    schedulers.add(scheduler);
    return scheduler;
  }

  /** Records on which threads its timers and messages ran. */
  private static final class TestActor extends Actor {
    private static final AtomicInteger IDS = new AtomicInteger();

    final String actorName = "test-actor-" + IDS.incrementAndGet();
    final Queue<String> violations = new ConcurrentLinkedQueue<>();
    volatile Runnable onStart = () -> {};

    private final String threadNamePrefix;
    private final Map<String, AtomicInteger> fired = new ConcurrentHashMap<>();
    // only accessed from the actor's own thread
    private final Map<String, ScheduledTimer> timers = new ConcurrentHashMap<>();

    TestActor(final String threadNamePrefix) {
      super(null, null, null);
      this.threadNamePrefix = threadNamePrefix;
    }

    int fireCount(final String id) {
      final var count = fired.get(id);
      return count == null ? 0 : count.get();
    }

    void scheduleOnce(final String id, final long delayMs) {
      final var scheduledAt = System.nanoTime();
      timers.put(
          id,
          actor.schedule(
              delayMs,
              () -> {
                checkNotEarly(id, scheduledAt, delayMs);
                fired(id);
              }));
    }

    void scheduleAtTimestamp(final String id, final long delayMs) {
      final var scheduledAt = System.nanoTime();
      timers.put(
          id,
          actor.runAt(
              System.currentTimeMillis() + delayMs,
              () -> {
                checkNotEarly(id, scheduledAt, delayMs);
                fired(id);
              }));
    }

    /** Fires every {@code periodMs} and cancels itself after {@code times} firings. */
    void scheduleRecurring(final String id, final long periodMs, final int times) {
      timers.put(
          id,
          actor.runAtFixedRate(
              Duration.ofMillis(periodMs),
              () -> {
                if (fired(id) == times) {
                  cancel(id);
                }
              }));
    }

    void cancel(final String id) {
      timers.get(id).cancel();
    }

    int fired(final String id) {
      checkThread(id);
      return fired.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
    }

    void checkThread(final String what) {
      final var threadName = Thread.currentThread().getName();
      if (!threadName.startsWith(threadNamePrefix)) {
        violations.add(
            what
                + " ran on "
                + threadName
                + ", expected a thread starting with "
                + threadNamePrefix);
      }
    }

    private void checkNotEarly(final String id, final long scheduledAtNanos, final long delayMs) {
      final var elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - scheduledAtNanos);
      // timers never run before their deadline; allow for clock resolution
      if (elapsedMs < delayMs - 15) {
        violations.add(id + " fired after " + elapsedMs + "ms, delay was " + delayMs + "ms");
      }
    }

    @Override
    protected void onActorStarted() {
      onStart.run();
    }
  }
}
