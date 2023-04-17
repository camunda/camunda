/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.DueDateChecker;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DueDateCheckerTest {

  @Test
  public void shouldNotScheduleTwoTasks() {
    // given
    final var dueDateChecker = new DueDateChecker(100, false, (builder) -> 0L);
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService, timeout(1000)).runDelayed(eq(Duration.ZERO), any(Task.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    final var currentTimeMillis = System.currentTimeMillis();
    dueDateChecker.schedule(currentTimeMillis + 1000); // in one second
    dueDateChecker.schedule(currentTimeMillis + 1000); // in one second

    // then
    verify(mockScheduleService, timeout(1000)).runDelayed(any(), any(Task.class));
  }

  @Test
  public void shouldScheduleForAnEarlierTasks() {
    // given
    final var dueDateChecker = new DueDateChecker(100, false, (builder) -> 0L);
    final var mockContext = mock(ReadonlyStreamProcessorContext.class);
    final var mockScheduleService = mock(ProcessingScheduleService.class);

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    dueDateChecker.onRecovered(mockContext);
    verify(mockScheduleService, timeout(1000)).runDelayed(eq(Duration.ZERO), any(Task.class));
    Mockito.clearInvocations(mockScheduleService);

    // when
    final var currentTimeMillis = System.currentTimeMillis();
    dueDateChecker.schedule(currentTimeMillis + 1000); // in one second
    dueDateChecker.schedule(currentTimeMillis + 100); // in 100 millis

    // then
    verify(mockScheduleService, timeout(1000).times(2)).runDelayed(any(), any(Task.class));
  }
}
