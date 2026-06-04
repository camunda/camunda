/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.crac;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ActorSchedulerCheckpointLifecycleTest {

  @Test
  void shouldStopAndRestartSchedulerAcrossCheckpoint() throws InterruptedException {
    // given -- a running scheduler bridged to Spring's CRaC lifecycle
    final var scheduler = ActorScheduler.newActorScheduler().build();
    final var sut = new ActorSchedulerCheckpointLifecycle(scheduler);
    scheduler.start(); // started eagerly by its @Bean during refresh
    assertThat(sut.isRunning()).isTrue();

    // when -- Spring stops it for checkpoint, then starts it on restore
    sut.stop();
    assertThat(sut.isRunning()).isFalse();
    sut.start();
    assertThat(sut.isRunning()).isTrue();

    // then -- the scheduler runs actors again on the rebuilt threads
    final var started = new CountDownLatch(1);
    final var actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            started.countDown();
          }
        };
    scheduler.submitActor(actor);
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

    // cleanup
    actor.closeAsync();
    sut.stop();
  }

  @Test
  void shouldBeNoOpStartWhenAlreadyRunning() {
    // given -- a running scheduler (eager @Bean start) and the bridge
    final var scheduler = ActorScheduler.newActorScheduler().build();
    final var sut = new ActorSchedulerCheckpointLifecycle(scheduler);
    scheduler.start();

    // when -- Spring's initial post-refresh start() runs while already running
    sut.start();

    // then -- it stays running and does not throw
    assertThat(sut.isRunning()).isTrue();

    // cleanup
    sut.stop();
  }
}
