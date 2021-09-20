/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

final class ActorSchedulerTest {

  @Test
  void shouldThrowIllegalStateExceptionWhenTaskIsSubmittedBeforeActorSchedulerIsNotRunning() {
    // given
    final var testActor = new TestActor();
    final var sut = ActorScheduler.newActorScheduler().build();

    // when + then
    assertThatThrownBy(() -> sut.submitActor(testActor)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sut.submitActor(testActor, 0))
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
    assertThatThrownBy(() -> sut.submitActor(testActor, 0))
        .isInstanceOf(IllegalStateException.class);
  }

  private static final class TestActor extends Actor {}
}
