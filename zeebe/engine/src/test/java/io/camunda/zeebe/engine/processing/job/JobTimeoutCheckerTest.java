/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.intent.JobIntent.TIME_OUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

public class JobTimeoutCheckerTest {
  public static final int NUMBER_OF_ACTIVE_JOBS = 10;
  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableProcessingState processingState;
  private MutableJobState jobState;
  private ReadonlyStreamProcessorContext mockContext;
  private ProcessingScheduleService mockScheduleService;
  private TaskResultBuilder mockTaskResultBuilder;

  @Before
  public void setUp() {
    processingState = stateRule.getProcessingState();
    jobState = processingState.getJobState();

    for (int i = 1; i <= NUMBER_OF_ACTIVE_JOBS; i++) {
      createAndActivateJobRecord(i, newJobRecord().setDeadline(i));
    }

    mockContext = mock(ReadonlyStreamProcessorContext.class);
    mockScheduleService = mock(ProcessingScheduleService.class);
    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);
    mockTaskResultBuilder = mock(TaskResultBuilder.class);
  }

  private void createAndActivateJobRecord(final long key, final JobRecord record) {
    jobState.create(key, record);
    jobState.activate(key, record);
  }

  private JobRecord newJobRecord() {
    return newJobRecord(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private JobRecord newJobRecord(final String tenantId) {
    final JobRecord jobRecord = new JobRecord();

    jobRecord.setRetries(2).setDeadline(256L).setType("test").setTenantId(tenantId);

    return jobRecord;
  }

  @Test
  public void shouldRescheduleWithPollingIntervalAfterSuccessfulExecution() {
    // Given
    when(mockTaskResultBuilder.appendCommandRecord(anyLong(), any(), any())).thenReturn(true);
    final ArgumentCaptor<Long> timestampCaptor = ArgumentCaptor.forClass(Long.class);

    final Duration pollingInterval = EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;
    final int batchLimit = Integer.MAX_VALUE;

    final var task =
        new JobTimeoutChecker(jobState, pollingInterval, batchLimit, InstantSource.system());
    task.setProcessingContext(mockContext);
    task.setShouldReschedule(true);

    // When
    task.execute(mockTaskResultBuilder);

    // then
    final var inOrder = inOrder(mockTaskResultBuilder);
    for (long i = 1; i <= NUMBER_OF_ACTIVE_JOBS; i++) {
      inOrder.verify(mockTaskResultBuilder).appendCommandRecord(eq(i), eq(TIME_OUT), any());
    }
    inOrder.verify(mockTaskResultBuilder).build();
    verifyNoMoreInteractions(mockTaskResultBuilder);

    verify(mockScheduleService, times(1))
        .runAt(timestampCaptor.capture(), ArgumentMatchers.<Task>any());
    assertThat(timestampCaptor.getValue())
        .isLessThanOrEqualTo(ActorClock.currentTimeMillis() + pollingInterval.toMillis());
  }

  @Test
  public void shouldRescheduleImmediatelyIfYieldedDueToBatchLimit() {
    // Given
    when(mockTaskResultBuilder.appendCommandRecord(anyLong(), any(), any())).thenReturn(true);
    final ArgumentCaptor<Long> timestampCaptor = ArgumentCaptor.forClass(Long.class);

    final Duration pollingInterval = EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;
    final int batchLimit = 3;

    final var task =
        new JobTimeoutChecker(jobState, pollingInterval, batchLimit, InstantSource.system());
    task.setProcessingContext(mockContext);
    task.setShouldReschedule(true);

    // When
    task.execute(mockTaskResultBuilder);

    // then
    final var inOrder = inOrder(mockTaskResultBuilder);
    for (long i = 1; i <= batchLimit; i++) {
      inOrder.verify(mockTaskResultBuilder).appendCommandRecord(eq(i), eq(TIME_OUT), any());
    }
    inOrder.verify(mockTaskResultBuilder).build();
    verifyNoMoreInteractions(mockTaskResultBuilder);

    verify(mockScheduleService, times(1))
        .runAt(timestampCaptor.capture(), ArgumentMatchers.<Task>any());
    assertThat(timestampCaptor.getValue()).isLessThanOrEqualTo(ActorClock.currentTimeMillis());

    /* TEST verify next execute will start where left off */

    // When
    task.execute(mockTaskResultBuilder);

    // then
    for (long i = batchLimit + 1; i <= 2 * batchLimit; i++) {
      inOrder.verify(mockTaskResultBuilder).appendCommandRecord(eq(i), eq(TIME_OUT), any());
    }
    inOrder.verify(mockTaskResultBuilder).build();
    verifyNoMoreInteractions(mockTaskResultBuilder);
  }

  @Test
  public void shouldRescheduleImmediatelyIfFailedToAppendTimeoutCommand() {
    // Given
    when(mockTaskResultBuilder.appendCommandRecord(anyLong(), any(), any()))
        .thenReturn(true)
        .thenReturn(false);
    final ArgumentCaptor<Long> timestampCaptor = ArgumentCaptor.forClass(Long.class);

    final Duration pollingInterval = EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;
    final int batchLimit = Integer.MAX_VALUE;

    final var task =
        new JobTimeoutChecker(jobState, pollingInterval, batchLimit, InstantSource.system());
    task.setProcessingContext(mockContext);
    task.setShouldReschedule(true);

    // When
    task.execute(mockTaskResultBuilder);

    // then
    final var inOrder = inOrder(mockTaskResultBuilder);

    inOrder.verify(mockTaskResultBuilder).appendCommandRecord(eq(1L), eq(TIME_OUT), any());
    inOrder.verify(mockTaskResultBuilder).appendCommandRecord(eq(2L), eq(TIME_OUT), any());
    inOrder.verify(mockTaskResultBuilder).build();

    verifyNoMoreInteractions(mockTaskResultBuilder);
    verify(mockScheduleService, times(1))
        .runAt(timestampCaptor.capture(), ArgumentMatchers.<Task>any());
    assertThat(timestampCaptor.getValue()).isLessThanOrEqualTo(ActorClock.currentTimeMillis());
  }
}
