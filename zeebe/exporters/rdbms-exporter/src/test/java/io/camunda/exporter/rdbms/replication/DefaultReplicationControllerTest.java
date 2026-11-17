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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.replication.ReplicationLagStatus;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the queue/debounce/pause/scheduling machinery shared by every replication signal, driven
 * through a mocked {@link ReplicationSignalStrategy} rather than a real provider - the strategies'
 * own decision logic is tested independently in their own test classes.
 */
class DefaultReplicationControllerTest {

  private static final int PARTITION_ID = 1;
  private static final Duration POLLING_INTERVAL = Duration.ofSeconds(5);
  private static final Duration MAX_LAG = Duration.ofSeconds(30);
  private static final int MIN_SYNC_REPLICAS = 1;

  private Controller controller;
  private ReplicationSignalStrategy strategy;
  private ReplicationConfiguration config;
  private ScheduledTask scheduledTask;
  private InstantSource clock;
  private RdbmsWriterMetrics metrics;

  @BeforeEach
  void setUp() {
    controller = mock(Controller.class);
    strategy = mock(ReplicationSignalStrategy.class);
    config = new ReplicationConfiguration();
    config.setPollingInterval(POLLING_INTERVAL);
    config.setMaxLag(MAX_LAG);
    config.setMinSyncReplicas(MIN_SYNC_REPLICAS);
    config.setPauseOnMaxLagExceeded(true);
    // disabled by default so existing tests get one queue entry per onFlush call; dedicated tests
    // below set a non-zero value to exercise debouncing itself
    config.setQueueDebounceTime(Duration.ZERO);

    scheduledTask = mock(ScheduledTask.class);
    clock = mock(InstantSource.class);
    metrics = mock(RdbmsWriterMetrics.class);

    when(clock.millis()).thenReturn(0L);
    when(controller.scheduleCancellableTask(any(), any())).thenReturn(scheduledTask);
    when(strategy.fetchStatuses()).thenReturn(List.of());
    when(strategy.computeConfirmedMarker(any())).thenReturn(ReplicationSignalStrategy.UNCONFIRMED);
    when(strategy.computePauseLag(any(), any())).thenReturn(Duration.ZERO);
    // a mocked strategy does not run the interface's own default method body, so this must be
    // stubbed explicitly even though it mirrors the default's behavior
    when(strategy.nextCheckDelay(any(), any())).thenReturn(POLLING_INTERVAL);
  }

  private DefaultReplicationController createController() {
    return new DefaultReplicationController(
        controller, strategy, config, PARTITION_ID, clock, metrics);
  }

  @Test
  void shouldScheduleCheckTaskOnConstruct() {
    // when
    createController();

    // then
    verify(controller, times(1)).scheduleCancellableTask(eq(POLLING_INTERVAL), any());
  }

  @Test
  void shouldBeInSyncInitially() {
    // when
    final var replicationController = createController();

    // then
    assertThat(replicationController.isReplicationInSync()).isTrue();
  }

  @Test
  void shouldCancelTaskOnClose() throws Exception {
    // given
    final var replicationController = createController();

    // when
    replicationController.close();

    // then
    verify(scheduledTask, times(1)).cancel();
  }

  @Test
  void shouldNotThrowOnDoubleClose() throws Exception {
    // given
    final var replicationController = createController();
    replicationController.close();

    // when / then - a second close() call is a safe no-op, not an NPE
    replicationController.close();
    verify(scheduledTask, times(1)).cancel();
  }

  @Test
  void shouldCancelRescheduledTaskAfterClose() throws Exception {
    // given
    final var replicationController = createController();
    final var rescheduledTask = mock(ScheduledTask.class);
    when(controller.scheduleCancellableTask(any(), any())).thenReturn(rescheduledTask);

    // when
    replicationController.checkReplication();
    replicationController.close();

    // then
    verify(rescheduledTask).cancel();
  }

  @Test
  void shouldDropEntryOnFullQueue() {
    // given - a controller with a queue that is already full
    config.setQueueCapacity(3);
    final var replicationController = createController();
    when(strategy.captureFlushMarker()).thenReturn(1L);
    for (int i = 0; i < 3; i++) {
      when(clock.millis()).thenReturn((long) i);
      replicationController.onFlush(i);
    }

    // when - one more flush that should be silently dropped
    when(clock.millis()).thenReturn(3L);
    replicationController.onFlush(4);

    // then - no exception, no crash
    assertThat(replicationController.isReplicationInSync()).isTrue();
  }

  @Test
  void shouldPauseWhenCaptureFlushMarkerThrows() {
    // given
    final var replicationController = createController();
    when(strategy.captureFlushMarker()).thenThrow(new RuntimeException("db error"));

    // when
    replicationController.onFlush(1);

    // then - isReplicationInSync returns false due to the failed capture
    assertThat(replicationController.isReplicationInSync()).isFalse();
  }

  @Test
  void shouldRescheduleAfterCheck() {
    // given
    final var replicationController = createController();

    // when
    replicationController.checkReplication();

    // then - one schedule during construction + one after checkReplication
    verify(controller, times(2)).scheduleCancellableTask(eq(POLLING_INTERVAL), any());
  }

  @Test
  void shouldRescheduleAfterExceptionalCheck() {
    // given - the strategy throws while polling statuses
    final var replicationController = createController();
    when(strategy.fetchStatuses()).thenThrow(new RuntimeException("db error"));

    // when
    replicationController.checkReplication();

    // then - reschedule must still happen even after an exception
    verify(controller, times(2)).scheduleCancellableTask(eq(POLLING_INTERVAL), any());
  }

  @Test
  void shouldRescheduleUsingStrategysNextCheckDelay() {
    // given - the strategy asks for a different delay than the configured polling interval
    final var customDelay = Duration.ofMillis(750);
    when(strategy.nextCheckDelay(any(), any())).thenReturn(customDelay);
    final var replicationController = createController();

    // when
    replicationController.checkReplication();

    // then - reschedule uses the strategy's returned delay, not the configured polling interval
    verify(controller).scheduleCancellableTask(eq(customDelay), any());
  }

  @Test
  void shouldRecordReplicationStatusMetricsOnCheck() {
    // given
    final var replicationController = createController();
    final var statuses = List.of(new ReplicationLagStatus("replica-1", 100L));
    doReturn(statuses).when(strategy).fetchStatuses();

    // when
    replicationController.checkReplication();

    // then - metrics should be updated with the current replication state
    verify(metrics).recordReplicationStatus(eq(statuses), anyBoolean(), anyLong(), anyLong());
  }

  @Test
  void shouldAcknowledgeConfirmedPosition() {
    // given
    final var replicationController = createController();
    when(strategy.captureFlushMarker()).thenReturn(10L);
    replicationController.onFlush(100L);

    // when - the strategy reports the entry's marker as confirmed
    when(strategy.computeConfirmedMarker(any())).thenReturn(10L);
    replicationController.checkReplication();

    // then
    verify(controller).updateLastExportedRecordPosition(100L);
  }

  @Test
  void shouldNotAcknowledgeUnconfirmedPosition() {
    // given
    final var replicationController = createController();
    when(strategy.captureFlushMarker()).thenReturn(10L);
    replicationController.onFlush(100L);

    // when - the strategy reports nothing confirmable this round
    when(strategy.computeConfirmedMarker(any())).thenReturn(ReplicationSignalStrategy.UNCONFIRMED);
    replicationController.checkReplication();

    // then
    verify(controller, never()).updateLastExportedRecordPosition(anyLong());
  }

  @Test
  void shouldPauseWhenPauseLagExceedsMaxLag() {
    // given
    final var replicationController = createController();

    // when
    when(strategy.computePauseLag(any(), any())).thenReturn(MAX_LAG.plusSeconds(1));
    replicationController.checkReplication();

    // then
    assertThat(replicationController.isReplicationInSync()).isFalse();
  }

  @Test
  void shouldResumeWhenPauseLagFallsWithinMaxLag() {
    // given - first trigger a pause
    final var replicationController = createController();
    when(strategy.computePauseLag(any(), any())).thenReturn(MAX_LAG.plusSeconds(1));
    replicationController.checkReplication();
    assertThat(replicationController.isReplicationInSync()).isFalse();

    // when - the lag recovers
    when(strategy.computePauseLag(any(), any())).thenReturn(Duration.ZERO);
    replicationController.checkReplication();

    // then
    assertThat(replicationController.isReplicationInSync()).isTrue();
  }

  @Test
  void shouldNeverPauseWhenPauseOnMaxLagExceededIsDisabled() {
    // given
    config.setPauseOnMaxLagExceeded(false);
    final var replicationController = createController();

    // when - a huge lag is reported, which would otherwise trigger a pause
    when(strategy.computePauseLag(any(), any()))
        .thenReturn(ReplicationSignalStrategy.PAUSE_WORST_CASE);
    replicationController.checkReplication();

    // then
    assertThat(replicationController.isReplicationInSync()).isTrue();
  }

  @Test
  void shouldPauseWhenStrategyReportsPauseWorstCaseEvenWithNonEmptyQueue() {
    // given - a position is queued and NOT yet confirmed
    final var replicationController = createController();
    when(strategy.captureFlushMarker()).thenReturn(10L);
    replicationController.onFlush(100L);

    // when - the strategy reports PAUSE_WORST_CASE, regardless of queue state; the shared
    // controller always trusts whatever the strategy decides, whether or not a particular
    // strategy's own logic happens to be gated by queue emptiness.
    when(strategy.computePauseLag(any(), any()))
        .thenReturn(ReplicationSignalStrategy.PAUSE_WORST_CASE);
    replicationController.checkReplication();

    // then
    assertThat(replicationController.isReplicationInSync()).isFalse();
  }

  @Nested
  class QueueDebounceTest {

    @Test
    void shouldDebounceFlushesWithinDebounceWindow() {
      // given - flushes 1s and 4s after the first, both within a 5s debounce window
      config.setQueueDebounceTime(Duration.ofSeconds(5));
      final var replicationController = createController();
      when(strategy.captureFlushMarker()).thenReturn(10L);
      replicationController.onFlush(100L); // queued at t=0
      when(clock.millis()).thenReturn(1_000L);
      when(strategy.captureFlushMarker()).thenReturn(20L);
      replicationController.onFlush(200L); // debounced
      when(clock.millis()).thenReturn(4_000L);
      when(strategy.captureFlushMarker()).thenReturn(30L);
      replicationController.onFlush(300L); // debounced

      // when - checked with a confirmation threshold well after all three markers
      when(strategy.computeConfirmedMarker(any())).thenReturn(100L);
      replicationController.checkReplication();

      // then - only the first, actually-queued entry is acknowledged
      verify(controller).updateLastExportedRecordPosition(100L);
      verify(controller, never()).updateLastExportedRecordPosition(200L);
      verify(controller, never()).updateLastExportedRecordPosition(300L);
    }

    @Test
    void shouldQueueNewEntryOnceDebounceWindowElapses() {
      // given
      config.setQueueDebounceTime(Duration.ofSeconds(5));
      final var replicationController = createController();
      when(strategy.captureFlushMarker()).thenReturn(10L);
      replicationController.onFlush(100L); // queued at t=0

      // when - next flush happens after the debounce window has elapsed
      when(clock.millis()).thenReturn(6_000L);
      when(strategy.captureFlushMarker()).thenReturn(20L);
      replicationController.onFlush(200L); // queued at t=6000

      when(strategy.computeConfirmedMarker(any())).thenReturn(20L);
      replicationController.checkReplication();

      // then - both entries were queued, so the latest is acknowledged
      verify(controller).updateLastExportedRecordPosition(200L);
    }

    @Test
    void shouldStillUpdateLatestFlushedPositionMetricWhenDebounced() {
      // given
      config.setQueueDebounceTime(Duration.ofSeconds(5));
      final var replicationController = createController();
      when(strategy.captureFlushMarker()).thenReturn(10L);
      replicationController.onFlush(100L);

      // when - debounced flush
      when(clock.millis()).thenReturn(1_000L);
      replicationController.onFlush(200L);
      replicationController.checkReplication();

      // then - metrics reflect the latest flushed position even though it wasn't queued
      verify(metrics).recordReplicationStatus(any(), anyBoolean(), eq(200L), anyLong());
    }
  }

  @Nested
  class RemoveConfirmedEntriesTest {

    @Test
    void shouldReturnNullWhenQueueIsEmpty() {
      // given
      final var replicationController = createController();

      // when
      final var result = replicationController.drainConfirmed(100L);

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldNotRemoveEntriesWhenConfirmedMarkerBelowAll() {
      // given
      final var replicationController = createController();
      when(strategy.captureFlushMarker()).thenReturn(10L);
      replicationController.onFlush(100L);
      when(strategy.captureFlushMarker()).thenReturn(20L);
      replicationController.onFlush(200L);

      // when - confirmedMarker=5 is below all entries
      final var result = replicationController.drainConfirmed(5L);

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldRemoveOnlyEntriesUpToConfirmedMarker() {
      // given - 5 entries with markers 10, 20, 30, 40, 50
      final var replicationController = createController();
      for (int i = 1; i <= 5; i++) {
        when(strategy.captureFlushMarker()).thenReturn((long) (i * 10));
        replicationController.onFlush(i * 100L);
      }

      // when - confirm up to marker=30 -> removes entries with marker 10, 20, 30
      final var result = replicationController.drainConfirmed(30L);

      // then - the returned entry is the last confirmed one (marker=30, position=300)
      assertThat(result).isNotNull();
      assertThat(result.marker()).isEqualTo(30L);
      assertThat(result.position()).isEqualTo(300L);
    }

    @Test
    void shouldRemoveAllEntriesWhenConfirmedMarkerExceedsAll() {
      // given - 5 entries
      final var replicationController = createController();
      for (int i = 1; i <= 5; i++) {
        when(strategy.captureFlushMarker()).thenReturn((long) (i * 10));
        replicationController.onFlush(i * 100L);
      }

      // when - confirmedMarker exceeds the highest marker (50)
      final var result = replicationController.drainConfirmed(9999L);

      // then
      assertThat(result).isNotNull();
      assertThat(result.marker()).isEqualTo(50L);
      assertThat(result.position()).isEqualTo(500L);
    }
  }

  @Nested
  class QueueHeadAgeTest {

    @Test
    void shouldReturnEmptyWhenQueueIsEmpty() {
      // given
      final var replicationController = createController();

      // when
      final var age = replicationController.queueHeadAge();

      // then - distinct from "an entry that is zero milliseconds old"
      assertThat(age).isEmpty();
    }

    @Test
    void shouldReturnElapsedTimeSinceOldestEntry() {
      // given - entry enqueued at t=0, current time is t=3000
      final var replicationController = createController();
      when(strategy.captureFlushMarker()).thenReturn(1L);
      replicationController.onFlush(100L);

      when(clock.millis()).thenReturn(3_000L);

      // when
      final var age = replicationController.queueHeadAge();

      // then
      assertThat(age).contains(Duration.ofMillis(3_000));
    }

    @Test
    void shouldAlwaysUseHeadOfQueueForAge() {
      // given - two entries at times 0 and 5000; current time is 6000
      final var replicationController = createController();
      when(strategy.captureFlushMarker()).thenReturn(1L);
      replicationController.onFlush(100L);
      when(clock.millis()).thenReturn(5_000L);
      replicationController.onFlush(200L);

      when(clock.millis()).thenReturn(6_000L);

      // when
      final var age = replicationController.queueHeadAge();

      // then - age is measured from head (t=0), not tail (t=5000)
      assertThat(age).contains(Duration.ofMillis(6_000));
    }
  }
}
