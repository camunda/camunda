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

import java.time.Duration;
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
  void shouldRestartAfterStop() throws InterruptedException {
    // given -- a scheduler that has been started and stopped
    final var sut = ActorScheduler.newActorScheduler().build();
    sut.start();
    final var stopFuture = sut.stop();
    await().until(stopFuture::isDone);

    // when -- it is started again (rebuilds the executor + threads)
    sut.start();

    // then -- a newly submitted actor actually runs on the rebuilt threads
    final var started = new CountDownLatch(1);
    final var restartedActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            started.countDown();
          }
        };
    sut.submitActor(restartedActor);

    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

    // cleanup
    restartedActor.closeAsync();
    sut.stop();
  }

  private static final class TestActor extends Actor {}
}
