/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.scheduler.ActorScheduler.DefaultActorThreadFactory;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ActorSchedulerTest {

  @Test
  void shouldCloseUnscheduledTask() {
    // given
    final var testActor = new TestActor();

    // when
    assertThat(testActor.closeAsync()).isDone();
  }

  @Test
  void shouldWaitForCloseAfterSchedulingTask() {
    // given
    final var latch = new CountDownLatch(1);
    final var testActor =
        new Actor() {
          @Override
          protected void onActorClosing() {
            try {
              latch.await();
            } catch (final InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        };

    final var scheduler = ActorScheduler.newActorScheduler().build();
    scheduler.start();
    scheduler.submitActor(testActor);

    // then -- closing is blocked at first
    final var closeFuture = testActor.closeAsync();
    assertThat(closeFuture).isNotDone();
    // then -- closing completes eventually
    latch.countDown();
    assertThat(closeFuture).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenTaskIsSubmittedBeforeActorSchedulerIsNotRunning() {
    // given
    final var testActor = new TestActor();
    final var sut = ActorScheduler.newActorScheduler().build();

    // when + then
    assertThatThrownBy(() -> sut.submitActor(testActor)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sut.submitActor(testActor, SchedulingHints.cpuBound()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenTaskIsSubmittedAfterActorSchedulerIsStopped() {
    // given
    final var testActor = new TestActor();
    final var sut = ActorScheduler.newActorScheduler().build();

    sut.start();
    final var stopFuture = sut.stop();

    await().until(stopFuture::isDone);

    // when + then
    assertThatThrownBy(() -> sut.submitActor(testActor)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sut.submitActor(testActor, SchedulingHints.cpuBound()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectIoBoundActorWhenSchedulerHasNoIoBoundThreads() throws Exception {
    // given
    try (final var sut =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("no-io")
            .setIoBoundActorThreadCount(0)
            .build()) {
      sut.start();
      final var testActor = new TestActor();

      // when + then
      assertThatThrownBy(() -> sut.submitActor(testActor, SchedulingHints.ioBound()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("TestActor")
          .hasMessageContaining("no-io");
      assertThat(sut.submitActor(testActor, SchedulingHints.cpuBound()))
          .succeedsWithin(Duration.ofSeconds(5));
    }
  }

  @Test
  void shouldCreateActorThreadsInGivenThreadGroup() throws Exception {
    // given
    final var group = new ThreadGroup("tenant-" + UUID.randomUUID());
    try (final var sut =
        ActorScheduler.newActorScheduler()
            .setActorThreadFactory(new DefaultActorThreadFactory("prefix-", group))
            .build()) {
      sut.start();

      // when
      final var cpuGroup = threadGroupOf(sut, SchedulingHints.cpuBound());
      final var ioGroup = threadGroupOf(sut, SchedulingHints.ioBound());
      final var inheritingGroup = groupOfThreadStartedByActor(sut);

      // then
      assertThat(cpuGroup).isSameAs(group);
      assertThat(ioGroup).isSameAs(group);
      assertThat(inheritingGroup).isSameAs(group);
    }
  }

  private static ThreadGroup threadGroupOf(
      final ActorSchedulingService scheduler, final SchedulingHints hints) throws Exception {
    final var group = new CompletableFuture<ThreadGroup>();
    scheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            group.complete(Thread.currentThread().getThreadGroup());
          }
        },
        hints);
    return group.get(10, TimeUnit.SECONDS);
  }

  private static ThreadGroup groupOfThreadStartedByActor(final ActorSchedulingService scheduler)
      throws Exception {
    final var group = new CompletableFuture<ThreadGroup>();
    scheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            new Thread(() -> group.complete(Thread.currentThread().getThreadGroup())).start();
          }
        });
    return group.get(10, TimeUnit.SECONDS);
  }

  private static final class TestActor extends Actor {}
}
