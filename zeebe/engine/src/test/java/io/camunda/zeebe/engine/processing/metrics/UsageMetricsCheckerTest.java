/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class UsageMetricsCheckerTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();
  private UsageMetricsChecker checker;
  private InstantSource mockClock;
  private TaskResultBuilder mockTaskResultBuilder;
  private ReadonlyStreamProcessorContext mockProcessingContext;
  private ProcessingScheduleService mockProcessingScheduleService;
  private ArgumentCaptor<UnifiedRecordValue> recordCaptor;

  @Before
  public void setUp() {
    mockClock = mock(InstantSource.class);
    mockTaskResultBuilder = mock(TaskResultBuilder.class);
    mockProcessingContext = mock(ReadonlyStreamProcessorContext.class);
    mockProcessingScheduleService = mock(ProcessingScheduleService.class);
    when(mockProcessingContext.getScheduleService()).thenReturn(mockProcessingScheduleService);
    recordCaptor = ArgumentCaptor.forClass(UnifiedRecordValue.class);

    checker = new UsageMetricsChecker(Duration.ofMillis(1), mockClock);
    checker.setProcessingContext(mockProcessingContext);
  }

  @Test
  public void shouldScheduleImmediately() {
    // when
    checker.schedule(true);

    // then
    verify(mockProcessingContext).getScheduleService();
    verify(mockProcessingScheduleService).runAtAsync(0L, checker);
  }

  @Test
  public void shouldScheduleToExportedInterval() {
    // given
    when(mockClock.millis()).thenReturn(1L);

    // when
    checker.schedule(false);

    // then
    verify(mockProcessingContext).getScheduleService();
    verify(mockProcessingScheduleService).runAt(2L, checker);
  }

  @Test
  public void shouldCreateMetricsRecordAndNotReschedule() {
    // given
    when(mockClock.millis()).thenReturn(1L);

    // when
    checker.execute(mockTaskResultBuilder);

    // then
    verify(mockTaskResultBuilder)
        .appendCommandRecord(eq(UsageMetricIntent.EXPORT), recordCaptor.capture());
    verify(mockProcessingContext, never()).getScheduleService();
    verify(mockProcessingScheduleService, never()).runAt(anyLong(), same(checker));
    assertThat(recordCaptor.getValue())
        .isEqualTo(new UsageMetricRecord().setIntervalType(IntervalType.ACTIVE));
  }

  @Test
  public void shouldCreateMetricsRecordAndReschedule() {
    // given
    when(mockClock.millis()).thenReturn(1L);

    // when
    checker.setShouldReschedule(true);
    checker.execute(mockTaskResultBuilder);

    // then
    verify(mockTaskResultBuilder)
        .appendCommandRecord(eq(UsageMetricIntent.EXPORT), recordCaptor.capture());
    verify(mockProcessingContext).getScheduleService();
    verify(mockProcessingScheduleService).runAt(2, checker);
    assertThat(recordCaptor.getValue())
        .isEqualTo(new UsageMetricRecord().setIntervalType(IntervalType.ACTIVE));
  }

  @Test
  public void shouldCancelPreviousTaskWhenRescheduled() {
    // given
    when(mockClock.millis()).thenReturn(1L);
    final var task1 = mock(ScheduledTask.class);
    when(mockProcessingScheduleService.runAt(2, checker)).thenReturn(task1);
    checker.schedule(false);

    // when
    checker.schedule(true);

    // then
    verify(mockProcessingContext, times(2)).getScheduleService();
    verify(mockProcessingScheduleService).runAt(2, checker);
    verify(mockProcessingScheduleService).runAtAsync(0, checker);
    verify(task1).cancel();
  }
}
