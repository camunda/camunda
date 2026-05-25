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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.replication.ReplicationLogStatus;
import io.camunda.db.rdbms.read.replication.ReplicationLogStatusProvider;
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

class LsnReplicationControllerTest {

  private static final int PARTITION_ID = 1;
  private static final Duration POLLING_INTERVAL = Duration.ofSeconds(5);
  private static final Duration MAX_LAG = Duration.ofSeconds(10);
  private static final int MIN_SYNC_REPLICAS = 1;

  private Controller controller;
  private ReplicationLogStatusProvider lsnProvider;
  private ReplicationConfiguration replicationConfig;
  private InstantSource clock;
  private ScheduledTask scheduledTask;
  private RdbmsWriterMetrics metrics;

  @BeforeEach
  void setUp() {
    controller = mock(Controller.class);
    lsnProvider = mock(ReplicationLogStatusProvider.class);
    replicationConfig = new ReplicationConfiguration();
    replicationConfig.setPollingInterval(POLLING_INTERVAL);
    replicationConfig.setMaxLag(MAX_LAG);
    replicationConfig.setMinSyncReplicas(MIN_SYNC_REPLICAS);
    replicationConfig.setPauseOnMaxLagExceeded(true);

    scheduledTask = mock(ScheduledTask.class);
    clock = mock(InstantSource.class);
    metrics = mock(RdbmsWriterMetrics.class);

    when(clock.millis()).thenReturn(0L);
    when(clock.instant()).thenReturn(java.time.Instant.EPOCH);
    when(controller.scheduleCancellableTask(any(), any())).thenReturn(scheduledTask);
    when(lsnProvider.getCurrent()).thenReturn(100L);
    when(lsnProvider.getReplicationStatuses()).thenReturn(List.of());
  }

  private LsnReplicationController createController() {
    return new LsnReplicationController(
        controller, lsnProvider, replicationConfig, PARTITION_ID, clock, metrics);
  }

  // -----------------------------------------------------------------------
  // Integration-style tests (checkReplication flow)
  // -----------------------------------------------------------------------

  @Test
  void shouldScheduleCheckTaskOnConstruct() {
    // when
    createController();

    // then
    verify(controller, times(1)).scheduleCancellableTask(eq(POLLING_INTERVAL), any());
  }

  @Test
  void shouldEnqueueEntryOnFlush() {
    // given
    final var replicationController = createController();
    when(lsnProvider.getCurrent()).thenReturn(42L);
    when(clock.millis()).thenReturn(1000L);

    // when
    replicationController.onFlush(10L);

    // then – queue should contain the entry; verify by checking that a subsequent
    // checkReplication with confirmed LSN 42 updates the exported position
    when(lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(new ReplicationLogStatus(42L, "replica-1", 0L)));
    replicationController.checkReplication();

    verify(controller, atLeastOnce()).updateLastExportedRecordPosition(10L);
  }

  @Test
  void shouldDropEntryOnFullQueue() {
    // given – create a controller with queue that is already full
    final var replicationController = createController();
    when(lsnProvider.getCurrent()).thenReturn(1L);
    when(clock.millis()).thenReturn(0L);

    // fill the queue completely
    for (int i = 0; i < LsnReplicationController.DEFAULT_QUEUE_CAPACITY; i++) {
      replicationController.onFlush(i);
    }

    // when – one more flush that should be silently dropped
    replicationController.onFlush(LsnReplicationController.DEFAULT_QUEUE_CAPACITY + 1);

    // then – isReplicationInSync still returns true (no exception, no crash)
    assertThat(replicationController.isReplicationInSync()).isTrue();
  }

  @Test
  void shouldPauseWhenLsnQueryExceptionalOnFullQueue() {
    // given – create a controller with queue that is already full
    final var replicationController = createController();
    when(lsnProvider.getCurrent()).thenThrow(new RuntimeException("db error"));
    when(clock.millis()).thenReturn(0L);

    // fill the queue completely
    replicationController.onFlush(1);

    // then – isReplicationInSync returns false due to DB problems
    assertThat(replicationController.isReplicationInSync()).isFalse();
  }

  @Test
  void shouldRemoveConfirmedEntriesOnCheck() {
    // given – 5 entries with lsn 1..5; replica has confirmed up to lsn 2
    final var replicationController = createController();
    when(lsnProvider.getCurrent()).thenReturn(10L);
    when(clock.millis()).thenReturn(0L);
    replicationController.onFlush(100L); // lsn=10 at enqueue time
    when(lsnProvider.getCurrent()).thenReturn(20L);
    replicationController.onFlush(200L);
    when(lsnProvider.getCurrent()).thenReturn(30L);
    replicationController.onFlush(300L);
    when(lsnProvider.getCurrent()).thenReturn(40L);
    replicationController.onFlush(400L);
    when(lsnProvider.getCurrent()).thenReturn(50L);
    replicationController.onFlush(500L);

    // replica has confirmed up to lsn=20 → first 2 entries should be removed
    when(lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(new ReplicationLogStatus(20L, "replica-1", 0L)));

    // when
    replicationController.checkReplication();

    // then – last exported position updated to the highest confirmed position (200)
    verify(controller).updateLastExportedRecordPosition(200L);
  }

  @Test
  void shouldNotPauseWhenNoLagExceededOnCheck() {
    // given – 5 entries all enqueued "now"; lag is 0, well within maxLag
    final var replicationController = createController();
    when(lsnProvider.getCurrent()).thenReturn(10L);
    when(clock.millis()).thenReturn(0L);
    for (int i = 1; i <= 5; i++) {
      replicationController.onFlush(i * 100L);
    }

    // current time is 0, so lag = 0
    when(clock.millis()).thenReturn(0L);
    when(lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(new ReplicationLogStatus(10L, "replica-1", 0L)));

    // when
    replicationController.checkReplication();

    // then
    assertThat(replicationController.isReplicationInSync()).isTrue();
  }

  @Test
  void shouldPauseWhenLagExceededOnCheck() {
    // given – 5 entries. The first 2 are enqueued at time 0, the rest at time 0 as well.
    // After enqueue the clock advances beyond maxLag so the head of the queue is stale.
    final var replicationController = createController();
    when(lsnProvider.getCurrent()).thenReturn(10L);
    when(clock.millis()).thenReturn(0L);

    // two entries with lsn=10, enqueued early
    for (int i = 1; i <= 5; i++) {
      when(lsnProvider.getCurrent()).thenReturn(i * 10L);
      when(clock.millis()).thenReturn(i * 10L);
      replicationController.onFlush(i * 100L);
    }

    // advance clock past maxLag (maxLag = 10 s = 10_000 ms)
    when(clock.millis()).thenReturn(MAX_LAG.toMillis() + 1_000L);
    // replica has NOT confirmed anything yet
    when(lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(new ReplicationLogStatus(0L, "replica-1", 0L)));

    // when
    replicationController.checkReplication();

    // then
    assertThat(replicationController.isReplicationInSync()).isFalse();
  }

  @Test
  void shouldResumeWhenOnNoLagExceededOnCheck() {
    // given – first trigger a pause
    final var replicationController = createController();
    for (int i = 1; i <= 5; i++) {
      when(lsnProvider.getCurrent()).thenReturn(i * 10L);
      when(clock.millis()).thenReturn(i * 10L);
      replicationController.onFlush(i * 100L);
    }

    // pause: advance clock past maxLag, replica not up-to-date
    when(clock.millis()).thenReturn(MAX_LAG.toMillis() + 1_000L);
    when(lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(new ReplicationLogStatus(0L, "replica-1", 0L)));
    replicationController.checkReplication();
    assertThat(replicationController.isReplicationInSync()).isFalse();

    // resume: replica catches up and clock is back within maxLag
    when(lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(new ReplicationLogStatus(50L, "replica-1", 0L)));
    replicationController.checkReplication();

    // then
    assertThat(replicationController.isReplicationInSync()).isTrue();
  }

  @Test
  void shouldNotPauseWhenEmptyQueueOnCheck() {
    // given – no entries enqueued at all
    final var replicationController = createController();
    when(lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(new ReplicationLogStatus(0L, "replica-1", 0L)));

    // when
    replicationController.checkReplication();

    // then – empty queue → lag = ZERO → no pause
    assertThat(replicationController.isReplicationInSync()).isTrue();
  }

  @Test
  void shouldPauseWhenEmptyQueueButQuorumNotMetOnCheck() {
    // given – no entries enqueued at all
    final var replicationController = createController();
    when(lsnProvider.getReplicationStatuses()).thenReturn(List.of());

    // when
    replicationController.checkReplication();

    // then – empty queue → lag = ZERO → no pause
    assertThat(replicationController.isReplicationInSync()).isFalse();
  }

  @Test
  void shouldRescheduleAfterCheck() {
    // given
    final var replicationController = createController();
    when(lsnProvider.getReplicationStatuses()).thenReturn(List.of());

    // when
    replicationController.checkReplication();

    // then – one schedule during construction + one after checkReplication
    verify(controller, times(2)).scheduleCancellableTask(eq(POLLING_INTERVAL), any());
  }

  @Test
  void shouldRescheduleAfterExceptionalCheck() {
    // given – lsnProvider throws
    final var replicationController = createController();
    when(lsnProvider.getReplicationStatuses()).thenThrow(new RuntimeException("db error"));

    // when
    replicationController.checkReplication();

    // then – reschedule must still happen even after an exception
    verify(controller, times(2)).scheduleCancellableTask(eq(POLLING_INTERVAL), any());
  }

  @Test
  void shouldRecordReplicationStatusMetricsOnCheck() {
    // given
    final var replicationController = createController();
    final var statuses = List.of(new ReplicationLogStatus(50L, "replica-1", 100L));
    when(lsnProvider.getReplicationStatuses()).thenReturn(statuses);

    // when
    replicationController.checkReplication();

    // then – metrics should be updated with the current replication state
    verify(metrics, atLeastOnce())
        .recordReplicationStatus(eq(statuses), anyBoolean(), anyLong(), anyLong());
  }

  @Test
  void shouldCancelTaskAfterClose() throws Exception {
    // given
    final var replicationController = createController();
    when(lsnProvider.getReplicationStatuses()).thenReturn(List.of());

    // when
    replicationController.close();

    // then – one schedule during construction + one after checkReplication
    verify(scheduledTask).cancel();
  }

  @Test
  void shouldCancelRescheduledTaskAfterClose() throws Exception {
    // given
    final var replicationController = createController();
    when(lsnProvider.getReplicationStatuses()).thenReturn(List.of());
    final var rescheduledTask = mock(ScheduledTask.class);
    when(controller.scheduleCancellableTask(any(), any())).thenReturn(rescheduledTask);

    // when
    replicationController.checkReplication();
    replicationController.close();

    // then – one schedule during construction + one after checkReplication
    verify(rescheduledTask).cancel();
  }

  // -----------------------------------------------------------------------
  // Unit tests for computeConfirmedLsn()
  // -----------------------------------------------------------------------

  @Nested
  class ComputeConfirmedLsnTest {

    @Test
    void shouldReturnMinValueWhenCurrentLsnIsNegative() {
      // given
      when(lsnProvider.getCurrent()).thenReturn(-1L);
      final var replicationController = createController();

      // when
      final long result = replicationController.computeConfirmedLsn(List.of());

      // then
      assertThat(result).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void shouldReturnNegativeOneWhenNotEnoughReplicas() {
      // given
      replicationConfig.setMinSyncReplicas(2);
      when(lsnProvider.getCurrent()).thenReturn(50L);
      final var replicationController = createController();

      // when – only 1 replica but 2 required
      final long result =
          replicationController.computeConfirmedLsn(
              List.of(new ReplicationLogStatus(100L, "replica-1", 0L)));

      // then
      assertThat(result).isEqualTo(-1L);
    }

    @Test
    void shouldReturnLowestOfTopNReplicaLsns() {
      // given – minSyncReplicas = 2, 3 replicas with lsn 10, 30, 50
      replicationConfig.setMinSyncReplicas(2);
      when(lsnProvider.getCurrent()).thenReturn(50L);
      final var replicationController = createController();

      final var statuses =
          List.of(
              new ReplicationLogStatus(10L, "replica-1", 0L),
              new ReplicationLogStatus(30L, "replica-2", 0L),
              new ReplicationLogStatus(50L, "replica-3", 0L));

      // when
      final long result = replicationController.computeConfirmedLsn(statuses);

      // then – top 2 by lsn are 30 and 50; min of those is 30
      assertThat(result).isEqualTo(30L);
    }

    @Test
    void shouldReturnReplicaLsnWhenExactlyMinSyncReplicas() {
      // given
      when(lsnProvider.getCurrent()).thenReturn(50L);
      final var replicationController = createController();

      final var statuses = List.of(new ReplicationLogStatus(40L, "replica-1", 0L));

      // when
      final long result = replicationController.computeConfirmedLsn(statuses);

      // then
      assertThat(result).isEqualTo(40L);
    }
  }

  // -----------------------------------------------------------------------
  // Unit tests for removeConfirmedLsnEntries()
  // -----------------------------------------------------------------------

  @Nested
  class RemoveConfirmedLsnEntriesTest {

    @Test
    void shouldReturnNullWhenQueueIsEmpty() {
      // given
      final var replicationController = createController();

      // when
      final var result = replicationController.removeConfirmedLsnEntries(100L);

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldNotRemoveEntriesWhenConfirmedLsnBelowAll() {
      // given
      final var replicationController = createController();
      when(lsnProvider.getCurrent()).thenReturn(10L);
      when(clock.millis()).thenReturn(0L);
      replicationController.onFlush(100L); // lsn=10
      when(lsnProvider.getCurrent()).thenReturn(20L);
      replicationController.onFlush(200L); // lsn=20

      // when – confirmedLsn=5 is below all entries
      final var result = replicationController.removeConfirmedLsnEntries(5L);

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldRemoveOnlyEntriesUpToConfirmedLsn() {
      // given – 5 entries with lsn 10, 20, 30, 40, 50
      final var replicationController = createController();
      when(clock.millis()).thenReturn(0L);
      for (int i = 1; i <= 5; i++) {
        when(lsnProvider.getCurrent()).thenReturn((long) (i * 10));
        replicationController.onFlush(i * 100L);
      }

      // when – confirm up to lsn=30 → removes entries with lsn 10, 20, 30
      final var result = replicationController.removeConfirmedLsnEntries(30L);

      // then – the returned entry is the last confirmed one (lsn=30, position=300)
      assertThat(result).isNotNull();
      assertThat(result.lsn()).isEqualTo(30L);
      assertThat(result.position()).isEqualTo(300L);
    }

    @Test
    void shouldRemoveAllEntriesWhenConfirmedLsnExceedsAll() {
      // given – 5 entries
      final var replicationController = createController();
      when(clock.millis()).thenReturn(0L);
      for (int i = 1; i <= 5; i++) {
        when(lsnProvider.getCurrent()).thenReturn((long) (i * 10));
        replicationController.onFlush(i * 100L);
      }

      // when – confirmedLsn exceeds max lsn (50)
      final var result = replicationController.removeConfirmedLsnEntries(9999L);

      // then
      assertThat(result).isNotNull();
      assertThat(result.lsn()).isEqualTo(50L);
      assertThat(result.position()).isEqualTo(500L);
    }
  }

  // -----------------------------------------------------------------------
  // Unit tests for getCurrentDbLag()
  // -----------------------------------------------------------------------

  @Nested
  class GetCurrentDbLagTest {

    @Test
    void shouldReturnZeroWhenQueueIsEmpty() {
      // given
      final var replicationController = createController();

      // when
      final var lag = replicationController.getCurrentDbLag();

      // then
      assertThat(lag).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldReturnElapsedTimeSinceOldestEntry() {
      // given – entry enqueued at t=0, current time is t=3000
      final var replicationController = createController();
      when(lsnProvider.getCurrent()).thenReturn(1L);
      when(clock.millis()).thenReturn(0L);
      replicationController.onFlush(100L);

      when(clock.millis()).thenReturn(3_000L);

      // when
      final var lag = replicationController.getCurrentDbLag();

      // then
      assertThat(lag).isEqualTo(Duration.ofMillis(3_000));
    }

    @Test
    void shouldAlwaysUseHeadOfQueueForLag() {
      // given – two entries at times 0 and 5000; current time is 6000
      final var replicationController = createController();
      when(lsnProvider.getCurrent()).thenReturn(1L);
      when(clock.millis()).thenReturn(0L);
      replicationController.onFlush(100L);
      when(clock.millis()).thenReturn(5_000L);
      replicationController.onFlush(200L);

      when(clock.millis()).thenReturn(6_000L);

      // when
      final var lag = replicationController.getCurrentDbLag();

      // then – lag is measured from head (t=0), not tail (t=5000)
      assertThat(lag).isEqualTo(Duration.ofMillis(6_000));
    }
  }

  // -----------------------------------------------------------------------
  // Unit tests for isMaxLagExceeded()
  // -----------------------------------------------------------------------

  @Nested
  class IsMaxLagExceededTest {

    @Test
    void shouldReturnFalseWhenLagIsLessThanMaxLag() {
      // given
      final var replicationController = createController();

      // when
      final boolean exceeded =
          replicationController.isMaxLagExceeded(MAX_LAG.minus(Duration.ofMillis(1)));

      // then
      assertThat(exceeded).isFalse();
    }

    @Test
    void shouldReturnFalseWhenLagEqualsMaxLag() {
      // given
      final var replicationController = createController();

      // when
      final boolean exceeded = replicationController.isMaxLagExceeded(MAX_LAG);

      // then
      assertThat(exceeded).isFalse();
    }

    @Test
    void shouldReturnTrueWhenLagExceedsMaxLag() {
      // given
      final var replicationController = createController();

      // when
      final boolean exceeded =
          replicationController.isMaxLagExceeded(MAX_LAG.plus(Duration.ofMillis(1)));

      // then
      assertThat(exceeded).isTrue();
    }

    @Test
    void shouldReturnFalseForZeroLag() {
      // given
      final var replicationController = createController();

      // when
      final boolean exceeded = replicationController.isMaxLagExceeded(Duration.ZERO);

      // then
      assertThat(exceeded).isFalse();
    }
  }
}
