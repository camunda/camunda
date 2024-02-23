/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.agrona.collections.MutableBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterTestScheduledTaskTest {
  @Test
  void shouldRun() {
    // given
    final MutableBoolean wasRan = new MutableBoolean();
    final Runnable task = () -> wasRan.set(true);
    final ExporterTestScheduledTask scheduledTask =
        new ExporterTestScheduledTask(Duration.ZERO, task);

    // when
    scheduledTask.run();

    // then
    assertThat(wasRan.get()).as("wrapped task was actually ran").isTrue();
    assertThat(scheduledTask.wasExecuted()).as("task was marked as executed").isTrue();
  }

  @Test
  void shouldCancel() {
    // given
    final MutableBoolean wasRan = new MutableBoolean();
    final Runnable task = () -> wasRan.set(true);
    final ExporterTestScheduledTask scheduledTask =
        new ExporterTestScheduledTask(Duration.ZERO, task);

    // when
    scheduledTask.cancel();
    scheduledTask.run();

    // then
    assertThat(wasRan.get()).as("wrapped task was not actually ran").isFalse();
    assertThat(scheduledTask)
        .as("scheduled task was marked as cancelled and not executed")
        .extracting(ExporterTestScheduledTask::isCanceled, ExporterTestScheduledTask::wasExecuted)
        .containsExactly(true, false);
  }

  @Test
  void shouldNotCancelAfterRun() {
    // given
    final MutableBoolean wasRan = new MutableBoolean();
    final Runnable task = () -> wasRan.set(true);
    final ExporterTestScheduledTask scheduledTask =
        new ExporterTestScheduledTask(Duration.ZERO, task);

    // when
    scheduledTask.run();
    scheduledTask.cancel();

    // then
    assertThat(wasRan.get()).as("wrapped task was actually ran").isTrue();
    assertThat(scheduledTask)
        .as("scheduled task was marked as executed and not cancelled")
        .extracting(ExporterTestScheduledTask::isCanceled, ExporterTestScheduledTask::wasExecuted)
        .containsExactly(false, true);
  }
}
