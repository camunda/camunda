/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DelayReplicationControllerTest {

  private static final int PARTITION_ID = 1;
  private static final Duration DELAY = Duration.ofSeconds(30);

  private Controller controller;
  private InstantSource clock;
  private ScheduledTask scheduledTask;

  @BeforeEach
  void setUp() {
    controller = mock(Controller.class);
    clock = mock(InstantSource.class);
    scheduledTask = mock(ScheduledTask.class);

    when(clock.millis()).thenReturn(0L);
    when(controller.scheduleCancellableTask(any(), any())).thenReturn(scheduledTask);
  }

  private DelayReplicationController createController() {
    return new DelayReplicationController(controller, DELAY, clock, PARTITION_ID);
  }

  @Test
  void shouldScheduleCheckTaskOnConstruct() {
    // when
    createController();

    // then
    verify(controller, times(1)).scheduleCancellableTask(eq(DELAY), any());
  }

  @Test
  void shouldNotAcknowledgeBeforeDelayExpires() {
    // given
    final var replicationController = createController();
    when(clock.millis()).thenReturn(0L);
    replicationController.onFlush(100L);

    // when - check before delay has elapsed
    when(clock.millis()).thenReturn(DELAY.toMillis() - 1);
    replicationController.checkDue();

    // then
    verify(controller, never()).updateLastExportedRecordPosition(100L);
  }

  @Test
  void shouldAcknowledgeAfterDelayExpires() {
    // given
    final var replicationController = createController();
    when(clock.millis()).thenReturn(0L);
    replicationController.onFlush(100L);

    // when - check exactly at delay boundary
    when(clock.millis()).thenReturn(DELAY.toMillis());
    replicationController.checkDue();

    // then
    verify(controller).updateLastExportedRecordPosition(100L);
  }

  @Test
  void shouldAcknowledgeOnlyExpiredEntries() {
    // given - two flushes: first at t=0 (expires at t=delay), second at t=delay-1 (not yet expired)
    final var replicationController = createController();
    when(clock.millis()).thenReturn(0L);
    replicationController.onFlush(100L);
    when(clock.millis()).thenReturn(DELAY.toMillis() - 1);
    replicationController.onFlush(200L);

    // when - check at exactly t=delay; only the first entry has expired
    when(clock.millis()).thenReturn(DELAY.toMillis());
    replicationController.checkDue();

    // then - only position 100 acknowledged
    verify(controller).updateLastExportedRecordPosition(100L);
    verify(controller, never()).updateLastExportedRecordPosition(200L);
  }

  @Test
  void shouldAcknowledgeHighestExpiredPosition() {
    // given - three entries all enqueued at t=0, all expire at t=delay
    final var replicationController = createController();
    when(clock.millis()).thenReturn(0L);
    replicationController.onFlush(100L);
    replicationController.onFlush(200L);
    replicationController.onFlush(300L);

    // when - all entries have expired
    when(clock.millis()).thenReturn(DELAY.toMillis());
    replicationController.checkDue();

    // then - only the last (highest) position is acknowledged in the final call
    verify(controller).updateLastExportedRecordPosition(300L);
    verify(controller, never()).updateLastExportedRecordPosition(100L);
    verify(controller, never()).updateLastExportedRecordPosition(200L);
  }

  @Test
  void shouldNotAcknowledgeOnClose() throws Exception {
    // given - a flush is pending
    final var replicationController = createController();
    when(clock.millis()).thenReturn(0L);
    replicationController.onFlush(100L);

    // when - close before delay expires
    replicationController.close();

    // then - position was never acknowledged (safe: will re-export from last committed position)
    verify(controller, never()).updateLastExportedRecordPosition(any(Long.class));
  }

  @Test
  void shouldRescheduleAfterCheck() {
    // given
    final var replicationController = createController();

    // when
    replicationController.checkDue();

    // then - one schedule on construction + one after checkDue
    verify(controller, times(2)).scheduleCancellableTask(eq(DELAY), any());
  }

  @Test
  void shouldCancelTaskAfterClose() throws Exception {
    // given
    final var replicationController = createController();

    // when
    replicationController.close();

    // then
    verify(scheduledTask).cancel();
  }

  @Test
  void shouldNotRescheduleAfterCloseduringCheck() throws Exception {
    // given - close is called; checkTask is set to null
    final var replicationController = createController();
    replicationController.close(); // sets checkTask to null

    // when - simulate checkDue running after close set task to null
    replicationController.checkDue();

    // then - only the initial schedule from the constructor; checkDue must not add another one
    verify(controller, times(1)).scheduleCancellableTask(eq(DELAY), any());
  }

  @Test
  void shouldDropEntryOnFullQueue() {
    // given - fill the queue to capacity
    final var replicationController = createController();
    when(clock.millis()).thenReturn(0L);
    for (int i = 0; i < DelayReplicationController.DEFAULT_QUEUE_CAPACITY; i++) {
      replicationController.onFlush(i);
    }

    // when - one more flush that should be silently dropped
    replicationController.onFlush(DelayReplicationController.DEFAULT_QUEUE_CAPACITY + 1L);

    // then - no exception, controller is still in sync
    assertThat(replicationController.isReplicationInSync()).isTrue();
  }
}
