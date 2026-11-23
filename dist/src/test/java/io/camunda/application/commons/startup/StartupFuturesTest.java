/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class StartupFuturesTest {

  @AfterEach
  void clearInterruptFlag() {
    // the interrupt tests leave the flag set on purpose; don't leak it into other tests
    Thread.interrupted();
  }

  @Test
  void shouldReturnResultOfCompletedStep() {
    // given
    final var future = CompletableFuture.completedFuture("done");

    // when
    final var result = StartupFutures.await("the step", future);

    // then
    assertThat(result).isEqualTo("done");
  }

  @Test
  void shouldAbortWaitOnInterrupt() {
    // given - a CompletableFuture#join would ignore the interrupt and keep blocking
    final var future = new CompletableFuture<Void>();
    Thread.currentThread().interrupt();

    // when - then
    assertThatThrownBy(() -> StartupFutures.await("the step", future))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Interrupted while waiting for the step, likely because the application is shutting"
                + " down")
        .hasCauseInstanceOf(InterruptedException.class);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void shouldAbortTimedWaitOnInterrupt() {
    // given - an ActorFuture#join would rethrow the interrupt without any message
    final var future = new CompletableActorFuture<Void>();
    Thread.currentThread().interrupt();

    // when - then
    assertThatThrownBy(() -> StartupFutures.await("the step", future, Duration.ofSeconds(30)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Interrupted while waiting for the step")
        .hasCauseInstanceOf(InterruptedException.class);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void shouldNameStepOnTimeout() {
    // given
    final Future<Void> future = new CompletableFuture<>();

    // when - then
    assertThatThrownBy(() -> StartupFutures.await("the step", future, Duration.ofMillis(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Timed out after PT0.001S waiting for the step")
        .hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void shouldNameStepAndKeepCauseOnFailure() {
    // given
    final var failure = new RuntimeException("port already in use");
    final var future = CompletableFuture.failedFuture(failure);

    // when - then
    assertThatThrownBy(() -> StartupFutures.await("the step", future))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed while waiting for the step: port already in use")
        .hasCause(failure);
  }
}
