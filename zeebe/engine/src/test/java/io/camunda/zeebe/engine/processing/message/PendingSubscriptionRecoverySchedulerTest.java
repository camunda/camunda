/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.engine.state.immutable.PendingSubscriptionRecovery.NO_ELEMENT_INSTANCE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.PendingProcessMessageSubscriptionState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PendingSubscriptionRecoverySchedulerTest {

  private static final int BATCH_LIMIT = 100;

  private final PendingProcessMessageSubscriptionState processSubscriptionState =
      mock(PendingProcessMessageSubscriptionState.class);
  private final PendingMessageSubscriptionState messageSubscriptionState =
      mock(PendingMessageSubscriptionState.class);
  private final ProcessingScheduleService scheduleService = mock(ProcessingScheduleService.class);
  private final ReadonlyStreamProcessorContext context = mock(ReadonlyStreamProcessorContext.class);
  private final TaskResultBuilder taskResultBuilder = mock(TaskResultBuilder.class);
  private final Queue<Task> scheduledTasks = new ArrayDeque<>();
  private final PendingSubscriptionRecoveryScheduler scheduler =
      new PendingSubscriptionRecoveryScheduler(
          processSubscriptionState, messageSubscriptionState, BATCH_LIMIT);

  @BeforeEach
  void setUp() {
    when(context.getScheduleService()).thenReturn(scheduleService);
    when(scheduleService.runDelayedAsync(any(Duration.class), any(Task.class)))
        .thenAnswer(
            invocation -> {
              scheduledTasks.add(invocation.getArgument(1));
              return null;
            });
  }

  @Test
  void shouldScheduleRecoveryOnAsyncActorInsteadOfRecoveringInline() {
    // when
    scheduler.onRecovered(context);

    // then
    verify(scheduleService, times(2)).runDelayedAsync(eq(Duration.ZERO), any(Task.class));
    verifyNoMoreInteractions(scheduleService);
    verifyNoInteractions(processSubscriptionState, messageSubscriptionState);
  }

  @Test
  void shouldRecoverEachSideChunkByChunk() {
    // given
    when(processSubscriptionState.recoverPending(eq(NO_ELEMENT_INSTANCE), anyInt())).thenReturn(5L);
    when(processSubscriptionState.recoverPending(eq(5L), anyInt())).thenReturn(NO_ELEMENT_INSTANCE);
    when(messageSubscriptionState.recoverPending(eq(NO_ELEMENT_INSTANCE), anyInt())).thenReturn(7L);
    when(messageSubscriptionState.recoverPending(eq(7L), anyInt())).thenReturn(NO_ELEMENT_INSTANCE);

    // when
    scheduler.onRecovered(context);
    runScheduledTasks();

    // then
    verify(processSubscriptionState).recoverPending(eq(NO_ELEMENT_INSTANCE), anyInt());
    verify(processSubscriptionState).recoverPending(eq(5L), anyInt());
    verify(messageSubscriptionState).recoverPending(eq(NO_ELEMENT_INSTANCE), anyInt());
    verify(messageSubscriptionState).recoverPending(eq(7L), anyInt());
    // two initial tasks, then one reschedule per side
    verify(scheduleService, times(4)).runDelayedAsync(eq(Duration.ZERO), any(Task.class));
  }

  @Test
  void shouldStopOnlyTheFailedSide() {
    // given
    when(processSubscriptionState.recoverPending(eq(NO_ELEMENT_INSTANCE), anyInt())).thenReturn(5L);
    when(processSubscriptionState.recoverPending(eq(5L), anyInt()))
        .thenThrow(new RuntimeException("expected"));
    when(messageSubscriptionState.recoverPending(eq(NO_ELEMENT_INSTANCE), anyInt())).thenReturn(7L);
    when(messageSubscriptionState.recoverPending(eq(7L), anyInt())).thenReturn(NO_ELEMENT_INSTANCE);

    // when
    scheduler.onRecovered(context);
    runScheduledTasks();

    // then
    verify(processSubscriptionState, times(1)).recoverPending(eq(5L), anyInt());
    verify(messageSubscriptionState).recoverPending(eq(7L), anyInt());
    // two initial tasks, then one reschedule per side before the failure
    verify(scheduleService, times(4)).runDelayedAsync(any(Duration.class), any(Task.class));
  }

  @Test
  void shouldStartOverInANewLeaderTerm() {
    // given
    when(processSubscriptionState.recoverPending(anyLong(), anyInt())).thenReturn(5L);
    when(messageSubscriptionState.recoverPending(anyLong(), anyInt())).thenReturn(7L);
    scheduler.onRecovered(context);
    scheduledTasks.poll().execute(taskResultBuilder);
    scheduledTasks.poll().execute(taskResultBuilder);
    // the stream processor closes, so the schedule service skips the queued chunks
    scheduledTasks.clear();

    // when
    final var nextTermScheduler =
        new PendingSubscriptionRecoveryScheduler(
            processSubscriptionState, messageSubscriptionState, BATCH_LIMIT);
    nextTermScheduler.onRecovered(context);
    scheduledTasks.poll().execute(taskResultBuilder);
    scheduledTasks.poll().execute(taskResultBuilder);

    // then
    verify(processSubscriptionState, times(2)).recoverPending(eq(NO_ELEMENT_INSTANCE), anyInt());
    verify(messageSubscriptionState, times(2)).recoverPending(eq(NO_ELEMENT_INSTANCE), anyInt());
    verify(processSubscriptionState, never()).recoverPending(eq(5L), anyInt());
    verify(messageSubscriptionState, never()).recoverPending(eq(7L), anyInt());
  }

  private void runScheduledTasks() {
    while (!scheduledTasks.isEmpty()) {
      scheduledTasks.poll().execute(taskResultBuilder);
    }
  }
}
