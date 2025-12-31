/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.jobmetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchRecord;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class JobMetricsCheckerTest {

  private JobMetricsChecker checker;
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

    checker = new JobMetricsChecker(Duration.ofMillis(1), mockClock);
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
  public void shouldScheduleToExportInterval() {
    // given
    when(mockClock.millis()).thenReturn(1L);

    // when
    checker.schedule(false);

    // then
    verify(mockProcessingContext).getScheduleService();
    verify(mockProcessingScheduleService).runAt(2L, checker);
  }

  @Test
  public void shouldCreateJobMetricsBatchRecordAndNotReschedule() {
    // given
    when(mockClock.millis()).thenReturn(1000L);

    // when
    checker.execute(mockTaskResultBuilder);

    // then
    verify(mockTaskResultBuilder)
        .appendCommandRecord(eq(JobMetricsBatchIntent.EXPORT), recordCaptor.capture());
    verify(mockProcessingContext, never()).getScheduleService();
    verify(mockProcessingScheduleService, never()).runAt(anyLong(), same(checker));
    assertThat(recordCaptor.getValue()).isInstanceOf(JobMetricsBatchRecord.class);
    final JobMetricsBatchRecord record = (JobMetricsBatchRecord) recordCaptor.getValue();
    // On first export, batchStartTime is set to current time
    assertThat(record.getBatchStartTime()).isEqualTo(1000L);
  }

  @Test
  public void shouldCreateJobMetricsBatchRecordAndReschedule() {
    // given
    when(mockClock.millis()).thenReturn(1000L);

    // when
    checker.setShouldReschedule(true);
    checker.execute(mockTaskResultBuilder);

    // then
    verify(mockTaskResultBuilder)
        .appendCommandRecord(eq(JobMetricsBatchIntent.EXPORT), recordCaptor.capture());
    verify(mockProcessingContext).getScheduleService();
    verify(mockProcessingScheduleService).runAt(1001L, checker);
    assertThat(recordCaptor.getValue()).isInstanceOf(JobMetricsBatchRecord.class);
  }

  @Test
  public void shouldSetBatchStartTimeToLastExportTimeOnSubsequentExports() {
    // given
    when(mockClock.millis()).thenReturn(1000L);
    checker.execute(mockTaskResultBuilder);

    // when - second execution
    when(mockClock.millis()).thenReturn(2000L);
    checker.execute(mockTaskResultBuilder);

    // then - capture all invocations and check the second one
    verify(mockTaskResultBuilder, org.mockito.Mockito.times(2))
        .appendCommandRecord(eq(JobMetricsBatchIntent.EXPORT), recordCaptor.capture());
    final var allRecords = recordCaptor.getAllValues();
    final JobMetricsBatchRecord secondRecord = (JobMetricsBatchRecord) allRecords.get(1);
    // On subsequent exports, batchStartTime should be the previous export time
    assertThat(secondRecord.getBatchStartTime()).isEqualTo(1000L);
  }

  @Test
  public void shouldCancelPreviousTaskWhenRescheduled() {
    // given
    when(mockClock.millis()).thenReturn(1L);
    final var task1 = mock(ScheduledTask.class);
    final var task2 = mock(ScheduledTask.class);
    when(mockProcessingScheduleService.runAt(anyLong(), same(checker)))
        .thenReturn(task1)
        .thenReturn(task2);

    // when
    checker.schedule(false);
    checker.schedule(false);

    // then
    verify(task1).cancel();
    verify(task2, never()).cancel();
  }
}
