/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultScheduledTaskRuntimeTest {

  @Nested
  final class RegistrationTest {

    @Test
    void shouldReturnHandleForNewRegistration() {
      // given
      final var runtime = new DefaultScheduledTaskRuntime();

      // when
      final var handle =
          runtime.register(
              "task-a",
              new Schedule.Periodic(Duration.ofMillis(100)),
              ctx -> Result.idle(ctx.resultBuilder()),
              TaskOptions.sync());

      // then
      assertThat(handle).isNotNull();
    }

    @Test
    void shouldThrowWhenRegisteringDuplicateName() {
      // given
      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> Result.idle(ctx.resultBuilder()),
          TaskOptions.sync());

      // when / then
      assertThatThrownBy(
              () ->
                  runtime.register(
                      "task-a",
                      new Schedule.Periodic(Duration.ofMillis(100)),
                      ctx -> Result.idle(ctx.resultBuilder()),
                      TaskOptions.sync()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("task-a");
    }
  }

  @Nested
  final class LifecycleTest {

    @Test
    void shouldSchedulePeriodicTaskAfterOnRecovered() {
      // given
      final var clock = new FakeClock(1000);
      final var scheduleService = new FakeScheduleService(clock);
      final var context = TestProcessorContext.with(scheduleService, clock);
      final var runs = new java.util.concurrent.atomic.AtomicInteger();

      final var runtime = new DefaultScheduledTaskRuntime();
      runtime.register(
          "task-a",
          new Schedule.Periodic(Duration.ofMillis(100)),
          ctx -> {
            runs.incrementAndGet();
            return Result.idle(ctx.resultBuilder());
          },
          TaskOptions.sync());

      // when
      runtime.onRecovered(context);
      scheduleService.advanceTo(1100);

      // then
      assertThat(runs.get()).isEqualTo(1);
    }
  }
}
