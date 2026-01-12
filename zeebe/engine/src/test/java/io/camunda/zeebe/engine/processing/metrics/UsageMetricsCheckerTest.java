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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
  }

  @Test
  public void shouldScheduleImmediatelyOnRecovered() {
    // when
    checker.onRecovered(mockProcessingContext);

    // then
    verify(mockProcessingContext).getScheduleService();
    verify(mockProcessingScheduleService).runAtAsync(0L, checker);
  }

  @Test
  public void shouldScheduleToExportedInterval() {
    // given
    when(mockClock.millis()).thenReturn(1L);
    // Use lifecycle method to set up the checker
    checker.onRecovered(mockProcessingContext);
    clearInvocations(mockProcessingScheduleService);

    // when - execute to trigger the normal (non-immediate) scheduling
    checker.execute(mockTaskResultBuilder);

    // then - schedule service is stored during onRecovered, so context is not accessed again
    verify(mockProcessingScheduleService).runAt(2L, checker);
  }

  @Test
  public void shouldCreateMetricsRecordAndNotRescheduleWhenPaused() {
    // given
    when(mockClock.millis()).thenReturn(1L);
    // Initialize then pause (so shouldReschedule = false)
    checker.onRecovered(mockProcessingContext);
    checker.onPaused();
    clearInvocations(mockProcessingScheduleService);

    // when
    checker.execute(mockTaskResultBuilder);

    // then
    verify(mockTaskResultBuilder)
        .appendCommandRecord(eq(UsageMetricIntent.EXPORT), recordCaptor.capture());
    verify(mockProcessingScheduleService, never()).runAt(anyLong(), same(checker));
    assertThat(recordCaptor.getValue())
        .isEqualTo(new UsageMetricRecord().setIntervalType(IntervalType.ACTIVE));
  }

  @Test
  public void shouldCreateMetricsRecordAndReschedule() {
    // given
    when(mockClock.millis()).thenReturn(1L);
    // Use lifecycle method to set up the checker
    checker.onRecovered(mockProcessingContext);
    clearInvocations(mockProcessingScheduleService);

    // when
    checker.execute(mockTaskResultBuilder);

    // then
    verify(mockTaskResultBuilder)
        .appendCommandRecord(eq(UsageMetricIntent.EXPORT), recordCaptor.capture());
    verify(mockProcessingScheduleService).runAt(2, checker);
    assertThat(recordCaptor.getValue())
        .isEqualTo(new UsageMetricRecord().setIntervalType(IntervalType.ACTIVE));
  }

  @Test
  public void shouldCancelPreviousTaskWhenResumed() {
    // given
    when(mockClock.millis()).thenReturn(1L);
    final var task1 = mock(ScheduledTask.class);
    when(mockProcessingScheduleService.runAt(2L, checker)).thenReturn(task1);
    // Use lifecycle methods to set up and execute
    checker.onRecovered(mockProcessingContext);
    clearInvocations(mockProcessingScheduleService);
    // Execute to schedule at normal interval
    checker.execute(mockTaskResultBuilder);
    clearInvocations(mockProcessingScheduleService);

    // when - resume triggers immediate scheduling
    checker.onPaused();
    checker.onResumed();

    // then
    verify(mockProcessingScheduleService).runAtAsync(0, checker);
    verify(task1).cancel();
  }
}
