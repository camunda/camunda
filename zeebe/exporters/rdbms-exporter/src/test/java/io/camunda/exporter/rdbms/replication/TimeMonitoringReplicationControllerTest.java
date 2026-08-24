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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.replication.ReplicationLagProvider;
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

class TimeMonitoringReplicationControllerTest {

  private static final int PARTITION_ID = 1;
  private static final Duration POLLING_INTERVAL = Duration.ofSeconds(5);
  private static final Duration MAX_LAG = Duration.ofSeconds(30);
  private static final int MIN_SYNC_REPLICAS = 1;

  private Controller controller;
  private ReplicationLagProvider statusProvider;
  private ReplicationConfiguration config;
  private ScheduledTask scheduledTask;
  private InstantSource clock;
  private RdbmsWriterMetrics metrics;

  @BeforeEach
  void setUp() {
    controller = mock(Controller.class);
    statusProvider = mock(ReplicationLagProvider.class);
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
    when(statusProvider.getCurrentDbTime()).thenReturn(0L);
    when(statusProvider.getReplicationStatuses()).thenReturn(List.of());
  }

  private TimeMonitoringReplicationController createController() {
    return new TimeMonitoringReplicationController(
        controller, statusProvider, config, PARTITION_ID, clock, metrics);
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
  void shouldDropEntryOnFullQueue() {
    // given - a controller with a queue that is already full
    config.setQueueCapacity(3);
    final var replicationController = createController();
    when(statusProvider.getCurrentDbTime()).thenReturn(1L);
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
  void shouldPauseWhenStatusProviderQueryExceptionalOnFlush() {
    // given
    final var replicationController = createController();
    when(statusProvider.getCurrentDbTime()).thenThrow(new RuntimeException("db error"));

    // when
    replicationController.onFlush(1);

    // then - isReplicationInSync returns false due to the DB problem, mirroring how a fallible
    // LSN lookup pauses LsnReplicationController on flush failure
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
    // given - statusProvider throws
    final var replicationController = createController();
    when(statusProvider.getReplicationStatuses()).thenThrow(new RuntimeException("db error"));

    // when
    replicationController.checkReplication();

    // then - reschedule must still happen even after an exception
    verify(controller, times(2)).scheduleCancellableTask(eq(POLLING_INTERVAL), any());
  }

  @Test
  void shouldRecordReplicationStatusMetricsOnCheck() {
    // given
    final var replicationController = createController();
    final var statuses = List.of(new ReplicationLagStatus("replica-1", 100L));
    when(statusProvider.getReplicationStatuses()).thenReturn(statuses);

    // when
    replicationController.checkReplication();

    // then - metrics should be updated with the current replication state
    verify(metrics).recordReplicationStatus(eq(statuses), anyBoolean(), anyLong(), anyLong());
  }

  @Nested
  class WhenReplicationLagIsAcceptable {

    @Test
    void shouldAcknowledgePositionOldEnoughToBeCoveredByLag() {
      // given - flushed at t=0
      final var replicationController = createController();
      replicationController.onFlush(100L);

      // when - checked with a confirmed as-of point exactly at the flush time
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L, 0L)));
      replicationController.checkReplication();

      // then
      verify(controller).updateLastExportedRecordPosition(100L);
      assertThat(replicationController.isReplicationInSync()).isTrue();
    }

    @Test
    void shouldNotAcknowledgePositionFlushedMoreRecentlyThanConfirmedAsOfPoint() {
      // given - flushed just now (t=0)
      final var replicationController = createController();
      replicationController.onFlush(100L);

      // when - the replica has only confirmed up to before the flush happened
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L, -5_000L)));
      replicationController.checkReplication();

      // then - lag is within maxLag, so not paused, but the position is too recent to confirm
      assertThat(replicationController.isReplicationInSync()).isTrue();
      verify(controller, never()).updateLastExportedRecordPosition(anyLong());
    }

    @Test
    void shouldAcknowledgeOnlyPositionsAtOrBeforeConfirmedAsOfPoint() {
      // given - two flushes 10 s apart
      final var replicationController = createController();
      replicationController.onFlush(100L); // t=0
      when(clock.millis()).thenReturn(10_000L);
      when(statusProvider.getCurrentDbTime()).thenReturn(10_000L);
      replicationController.onFlush(200L); // t=10s

      // when - the replica has confirmed up to t=7s
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L, 7_000L)));
      replicationController.checkReplication();

      // then - only the position flushed before the cutoff is acknowledged
      verify(controller).updateLastExportedRecordPosition(100L);
      verify(controller, never()).updateLastExportedRecordPosition(200L);
    }

    @Test
    void shouldAcknowledgeRemainingPositionOnceItIsOldEnough() {
      // given - as above, position 100 already confirmed, position 200 still pending
      final var replicationController = createController();
      replicationController.onFlush(100L); // t=0
      when(clock.millis()).thenReturn(10_000L);
      when(statusProvider.getCurrentDbTime()).thenReturn(10_000L);
      replicationController.onFlush(200L); // t=10s
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L, 7_000L)));
      replicationController.checkReplication();

      // when - checked again once the replica's confirmed as-of point reaches t=11s
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L, 11_000L)));
      replicationController.checkReplication();

      // then
      verify(controller).updateLastExportedRecordPosition(200L);
    }

    @Test
    void shouldNotAcknowledgePositionTwiceWhenNotAdvanced() {
      // given
      final var replicationController = createController();
      replicationController.onFlush(100L);
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 1_000L, 9_000L)));
      replicationController.checkReplication(); // confirms 100

      // when - second check without a new flush; the confirmed as-of point is unchanged
      replicationController.checkReplication();

      // then - position 100 should only be acknowledged once
      verify(controller, times(1)).updateLastExportedRecordPosition(100L);
    }

    @Test
    void shouldHandleZeroLag() {
      // given
      final var replicationController = createController();
      replicationController.onFlush(42L);

      // when
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 0L, 0L)));
      replicationController.checkReplication();

      // then
      verify(controller).updateLastExportedRecordPosition(42L);
      assertThat(replicationController.isReplicationInSync()).isTrue();
    }

    @Test
    void shouldNotAcknowledgeNewerEntryWhenReplicaAsOfPointIsStaleEvenAsWallClockAdvances() {
      // given - flushed at t=0, replica has confirmed exactly up to t=0
      final var replicationController = createController();
      replicationController.onFlush(100L);
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 3_000L, 0L)));
      replicationController.checkReplication(); // confirms 100

      // when - a new position is flushed long after the replica's last confirmed point, and a lot
      // of wall-clock time passes on subsequent checks, but the replica's own as-of point - e.g.
      // because it went quiet or disconnected - never advances past t=0
      when(clock.millis()).thenReturn(50_000L);
      when(statusProvider.getCurrentDbTime()).thenReturn(50_000L);
      replicationController.onFlush(200L); // t=50s
      when(clock.millis()).thenReturn(120_000L);
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 3_000L, 0L)));
      replicationController.checkReplication();

      // then - position 200 must not be confirmed just because wall-clock time passed; only the
      // replica's own confirmed as-of point advancing past t=50s may confirm it
      verify(controller, never()).updateLastExportedRecordPosition(200L);
    }
  }

  @Nested
  class WhenReplicationLagIsExceeded {

    @Test
    void shouldPauseWhenLagExceedsMaxLag() {
      // given - flushed just now, so it would not be confirmed regardless
      final var replicationController = createController();
      replicationController.onFlush(100L);

      // when
      when(statusProvider.getReplicationStatuses())
          .thenReturn(
              List.of(new ReplicationLagStatus("replica-1", 60_000L, -60_000L))); // 60 s > 30 s
      replicationController.checkReplication();

      // then
      assertThat(replicationController.isReplicationInSync()).isFalse();
      verify(controller, never()).updateLastExportedRecordPosition(100L);
    }

    @Test
    void shouldResumeWhenLagFallsBelowMaxLag() {
      // given
      final var replicationController = createController();
      replicationController.onFlush(100L);
      when(statusProvider.getReplicationStatuses())
          .thenReturn(
              List.of(new ReplicationLagStatus("replica-1", 60_000L, -60_000L))); // high lag
      replicationController.checkReplication(); // paused

      // when - lag recovers, and the replica has confirmed up to the newer position's flush time
      when(clock.millis()).thenReturn(10_000L);
      when(statusProvider.getCurrentDbTime()).thenReturn(10_000L);
      replicationController.onFlush(200L);
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L, 10_000L))); // 5s<30s
      replicationController.checkReplication();

      // then
      assertThat(replicationController.isReplicationInSync()).isTrue();
      verify(controller).updateLastExportedRecordPosition(200L);
    }

    @Test
    void shouldPauseWhenNoReplicasConnected() {
      // given - a position that would otherwise be old enough to confirm
      config.setMinSyncReplicas(1);
      final var replicationController = createController();
      replicationController.onFlush(100L);
      when(clock.millis()).thenReturn(100_000L);
      when(statusProvider.getReplicationStatuses()).thenReturn(List.of()); // 0 replicas

      // when
      replicationController.checkReplication();

      // then - quorum not met means nothing can be trusted, regardless of position age
      assertThat(replicationController.isReplicationInSync()).isFalse();
      verify(controller, never()).updateLastExportedRecordPosition(anyLong());
    }

    @Test
    void shouldPauseWhenMaxLagExceededAcrossMultipleReplicas() {
      // given
      final var replicationController = createController();
      replicationController.onFlush(100L);

      // when
      when(statusProvider.getReplicationStatuses())
          .thenReturn(
              List.of(
                  new ReplicationLagStatus("replica-1", 5_000L), // 5 s OK
                  new ReplicationLagStatus("replica-2", 90_000L))); // 90 s > 30 s
      replicationController.checkReplication();

      // then - worst-case replica determines state
      assertThat(replicationController.isReplicationInSync()).isFalse();
    }
  }

  @Nested
  class WhenPauseOnMaxLagExceededIsDisabled {

    @Test
    void shouldNeverPauseRegardlessOfLagOrQuorum() {
      // given
      config.setPauseOnMaxLagExceeded(false);
      final var replicationController = createController();
      replicationController.onFlush(100L);

      // when
      when(statusProvider.getReplicationStatuses())
          .thenReturn(
              List.of(new ReplicationLagStatus("replica-1", 999_000L, -999_000L))); // very high
      replicationController.checkReplication();

      // then - the exporter is not blocked, but the just-flushed position still isn't old enough
      assertThat(replicationController.isReplicationInSync()).isTrue();
      verify(controller, never()).updateLastExportedRecordPosition(100L);
    }

    @Test
    void shouldAcknowledgeOldEnoughPositionEvenWhenPauseIsDisabled() {
      // given
      config.setPauseOnMaxLagExceeded(false);
      final var replicationController = createController();
      replicationController.onFlush(100L);

      // when - the replica has confirmed up to the flush time
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L, 0L))); // 5 s < 30 s
      replicationController.checkReplication();

      // then - acknowledgment depends only on position age, independent of the pause flag
      assertThat(replicationController.isReplicationInSync()).isTrue();
      verify(controller).updateLastExportedRecordPosition(100L);
    }
  }

  @Nested
  class QueueDebounceTest {

    @Test
    void shouldDebounceFlushesWithinDebounceWindow() {
      // given - flushes 1s and 4s after the first, both within a 5s debounce window
      config.setQueueDebounceTime(Duration.ofSeconds(5));
      final var replicationController = createController();
      replicationController.onFlush(100L); // queued at t=0
      when(clock.millis()).thenReturn(1_000L);
      replicationController.onFlush(200L); // debounced
      when(clock.millis()).thenReturn(4_000L);
      replicationController.onFlush(300L); // debounced

      // when - checked with a confirmed as-of point well after all three positions
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 0L, 10_000L)));
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
      replicationController.onFlush(100L); // queued at t=0

      // when - next flush happens after the debounce window has elapsed
      when(clock.millis()).thenReturn(6_000L);
      when(statusProvider.getCurrentDbTime()).thenReturn(6_000L);
      replicationController.onFlush(200L); // queued at t=6000

      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 0L, 20_000L)));
      replicationController.checkReplication();

      // then - both entries were queued, so the latest is acknowledged
      verify(controller).updateLastExportedRecordPosition(200L);
    }

    @Test
    void shouldStillUpdateLatestFlushedPositionMetricWhenDebounced() {
      // given
      config.setQueueDebounceTime(Duration.ofSeconds(5));
      final var replicationController = createController();
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

    private AbstractReplicationController.PendingEntry drainConfirmed(
        final TimeMonitoringReplicationController replicationController, final Duration lag) {
      final long safeBeforeMs = clock.millis() - lag.toMillis();
      return replicationController.drainConfirmed(entry -> entry.enqueueTimeMs() <= safeBeforeMs);
    }

    @Test
    void shouldReturnNullWhenQueueIsEmpty() {
      // given
      final var replicationController = createController();

      // when
      final var result = drainConfirmed(replicationController, Duration.ofSeconds(5));

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldNotRemoveEntriesNewerThanSafeCutoff() {
      // given
      final var replicationController = createController();
      replicationController.onFlush(100L);

      // when - cutoff is in the future relative to the flush, but only barely: 1s elapsed, 5s lag
      when(clock.millis()).thenReturn(1_000L);
      final var result = drainConfirmed(replicationController, Duration.ofSeconds(5));

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldRemoveOnlyEntriesUpToSafeCutoff() {
      // given - three flushes at t=0, t=5s, t=10s
      final var replicationController = createController();
      replicationController.onFlush(100L);
      when(clock.millis()).thenReturn(5_000L);
      when(statusProvider.getCurrentDbTime()).thenReturn(5_000L);
      replicationController.onFlush(200L);
      when(clock.millis()).thenReturn(10_000L);
      when(statusProvider.getCurrentDbTime()).thenReturn(10_000L);
      replicationController.onFlush(300L);

      // when - checked at t=12s with a 5s lag: cutoff is t=7s
      when(clock.millis()).thenReturn(12_000L);
      final var result = drainConfirmed(replicationController, Duration.ofSeconds(5));

      // then - entries at t=0 and t=5s are confirmed; the one at t=10s is not
      assertThat(result).isNotNull();
      assertThat(result.position()).isEqualTo(200L);
    }

    @Test
    void shouldRemoveAllEntriesWhenLagIsZeroAndAllEnqueuedInThePast() {
      // given
      final var replicationController = createController();
      replicationController.onFlush(100L);
      when(clock.millis()).thenReturn(1_000L);
      when(statusProvider.getCurrentDbTime()).thenReturn(1_000L);
      replicationController.onFlush(200L);

      // when
      when(clock.millis()).thenReturn(2_000L);
      final var result = drainConfirmed(replicationController, Duration.ZERO);

      // then
      assertThat(result).isNotNull();
      assertThat(result.position()).isEqualTo(200L);
    }
  }

  @Nested
  class ComputeMaxReplicaLag {

    @Test
    void shouldReturnZeroForEmptyList() {
      // given
      final var replicationController = createController();

      // when
      final Duration lag = replicationController.computeMaxReplicaLag(List.of());

      // then
      assertThat(lag).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldReturnMaxLagAcrossReplicas() {
      // given
      final var replicationController = createController();
      final var statuses =
          List.of(
              new ReplicationLagStatus("r1", 5_000L),
              new ReplicationLagStatus("r2", 20_000L),
              new ReplicationLagStatus("r3", 10_000L));

      // when
      final Duration lag = replicationController.computeMaxReplicaLag(statuses);

      // then
      assertThat(lag).isEqualTo(Duration.ofMillis(20_000L));
    }

    @Test
    void shouldTreatNullLagAsWorstCase() {
      // given - an unknown lag must not look like "no lag at all"
      final var replicationController = createController();
      final var statuses = List.of(new ReplicationLagStatus("r1", null));

      // when
      final Duration lag = replicationController.computeMaxReplicaLag(statuses);

      // then
      assertThat(lag).isEqualTo(Duration.ofMillis(Long.MAX_VALUE));
    }
  }

  @Nested
  class ComputeConfirmedAsOfMs {

    @Test
    void shouldReturnMinValueForEmptyList() {
      // given
      final var replicationController = createController();

      // when
      final long asOfMs = replicationController.computeConfirmed(List.of());

      // then
      assertThat(asOfMs).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void shouldReturnMinValueWhenFewerThanMinSyncReplicasConnected() {
      // given - minSyncReplicas=2, but only one replica reporting
      config.setMinSyncReplicas(2);
      final var replicationController = createController();
      final var statuses = List.of(new ReplicationLagStatus("r1", 5_000L, 20_000L));

      // when
      final long asOfMs = replicationController.computeConfirmed(statuses);

      // then
      assertThat(asOfMs).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void shouldReturnLowestAsOfAmongTheTopMinSyncReplicas() {
      // given - minSyncReplicas=2 with 3 replicas connected; the worst of the two BEST replicas
      // determines the confirmed point, mirroring LsnReplicationController.computeConfirmedLsn
      config.setMinSyncReplicas(2);
      final var replicationController = createController();
      final var statuses =
          List.of(
              new ReplicationLagStatus("r1", 5_000L, 20_000L),
              new ReplicationLagStatus("r2", 20_000L, 5_000L),
              new ReplicationLagStatus("r3", 10_000L, 10_000L));

      // when
      final long asOfMs = replicationController.computeConfirmed(statuses);

      // then - top 2 by as-of are r1 (20_000) and r3 (10_000); the straggler r2 (5_000) is outside
      // the required quorum and must not be able to block confirmation
      assertThat(asOfMs).isEqualTo(10_000L);
    }

    @Test
    void shouldIgnoreNullAsOfFromReplicaBeyondQuorum() {
      // given - minSyncReplicas=1, one replica genuinely confirmed, one extra with an unknown
      // as-of point; the extra replica falls outside the required quorum
      final var replicationController = createController();
      final var statuses =
          List.of(
              new ReplicationLagStatus("r1", 5_000L, 20_000L),
              new ReplicationLagStatus("r2", 5_000L, null));

      // when
      final long asOfMs = replicationController.computeConfirmed(statuses);

      // then
      assertThat(asOfMs).isEqualTo(20_000L);
    }

    @Test
    void shouldTreatNullAsOfAsWorstCaseWhenWithinQuorum() {
      // given - minSyncReplicas=2 with exactly 2 replicas connected, so both fall within the
      // required quorum; the unknown as-of point must not look like "confirmed up to now"
      config.setMinSyncReplicas(2);
      final var replicationController = createController();
      final var statuses =
          List.of(
              new ReplicationLagStatus("r1", 5_000L, 20_000L),
              new ReplicationLagStatus("r2", 5_000L, null));

      // when
      final long asOfMs = replicationController.computeConfirmed(statuses);

      // then
      assertThat(asOfMs).isEqualTo(Long.MIN_VALUE);
    }
  }
}
