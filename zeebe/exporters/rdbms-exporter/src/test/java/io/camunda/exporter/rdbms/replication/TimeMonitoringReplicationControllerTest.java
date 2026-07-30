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

import io.camunda.db.rdbms.read.replication.ReplicationLagProvider;
import io.camunda.db.rdbms.read.replication.ReplicationLagStatus;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
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

    scheduledTask = mock(ScheduledTask.class);
    metrics = mock(RdbmsWriterMetrics.class);

    when(controller.scheduleCancellableTask(any(), any())).thenReturn(scheduledTask);
    when(statusProvider.getReplicationStatuses()).thenReturn(List.of());
  }

  private TimeMonitoringReplicationController createController() {
    return new TimeMonitoringReplicationController(
        controller, statusProvider, config, PARTITION_ID, metrics);
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

  @Nested
  class WhenReplicationLagIsAcceptable {

    @Test
    void shouldAcknowledgePositionWhenLagWithinMaxLag() {
      // given
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L))); // 5 s < 30 s

      replicationController.onFlush(100L);

      // when
      replicationController.checkReplication();

      // then
      verify(controller).updateLastExportedRecordPosition(100L);
      assertThat(replicationController.isReplicationInSync()).isTrue();
    }

    @Test
    void shouldAcknowledgeLatestPositionAfterMultipleFlushes() {
      // given
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 1_000L))); // 1 s

      replicationController.onFlush(10L);
      replicationController.onFlush(20L);
      replicationController.onFlush(50L);

      // when
      replicationController.checkReplication();

      // then — only the latest position is acknowledged
      verify(controller).updateLastExportedRecordPosition(50L);
    }

    @Test
    void shouldNotAcknowledgePositionTwiceWhenNotAdvanced() {
      // given
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 1_000L)));

      replicationController.onFlush(100L);
      replicationController.checkReplication(); // acknowledges 100

      // when — second check without a new flush
      replicationController.checkReplication();

      // then — position 100 should only be acknowledged once
      verify(controller, times(1)).updateLastExportedRecordPosition(100L);
    }

    @Test
    void shouldHandleZeroLag() {
      // given
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 0L)));

      replicationController.onFlush(42L);

      // when
      replicationController.checkReplication();

      // then
      verify(controller).updateLastExportedRecordPosition(42L);
      assertThat(replicationController.isReplicationInSync()).isTrue();
    }
  }

  @Nested
  class WhenReplicationLagIsExceeded {

    @Test
    void shouldPauseWhenLagExceedsMaxLag() {
      // given
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 60_000L))); // 60 s > 30 s

      replicationController.onFlush(100L);

      // when
      replicationController.checkReplication();

      // then
      assertThat(replicationController.isReplicationInSync()).isFalse();
      verify(controller, never()).updateLastExportedRecordPosition(100L);
    }

    @Test
    void shouldResumeWhenLagFallsBelowMaxLag() {
      // given
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 60_000L))); // high lag

      replicationController.onFlush(100L);
      replicationController.checkReplication(); // paused

      // when — lag recovers
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L))); // 5 s < 30 s
      replicationController.onFlush(200L);
      replicationController.checkReplication();

      // then
      assertThat(replicationController.isReplicationInSync()).isTrue();
      verify(controller).updateLastExportedRecordPosition(200L);
    }

    @Test
    void shouldPauseWhenNoReplicasConnected() {
      // given
      config.setMinSyncReplicas(1);
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses()).thenReturn(List.of()); // 0 replicas

      replicationController.onFlush(100L);

      // when
      replicationController.checkReplication();

      // then
      assertThat(replicationController.isReplicationInSync()).isFalse();
      verify(controller, never()).updateLastExportedRecordPosition(any(long.class));
    }

    @Test
    void shouldPauseWhenMaxLagExceededAcrossMultipleReplicas() {
      // given
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses())
          .thenReturn(
              List.of(
                  new ReplicationLagStatus("replica-1", 5_000L), // 5 s OK
                  new ReplicationLagStatus("replica-2", 90_000L))); // 90 s > 30 s

      replicationController.onFlush(100L);

      // when
      replicationController.checkReplication();

      // then — worst-case replica determines state
      assertThat(replicationController.isReplicationInSync()).isFalse();
    }
  }

  @Nested
  class WhenPauseOnMaxLagExceededIsDisabled {

    @Test
    void shouldNeverPauseEvenWhenLagExceeded() {
      // given
      config.setPauseOnMaxLagExceeded(false);
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 999_000L))); // very high

      replicationController.onFlush(100L);

      // when
      replicationController.checkReplication();

      // then — the exporter is not blocked, but an unsafe position is still not acknowledged
      assertThat(replicationController.isReplicationInSync()).isTrue();
      verify(controller, never()).updateLastExportedRecordPosition(100L);
    }

    @Test
    void shouldAcknowledgePositionOnceLagIsAcceptable() {
      // given
      config.setPauseOnMaxLagExceeded(false);
      final var replicationController = createController();
      when(statusProvider.getReplicationStatuses())
          .thenReturn(List.of(new ReplicationLagStatus("replica-1", 5_000L))); // 5 s < 30 s

      replicationController.onFlush(100L);

      // when
      replicationController.checkReplication();

      // then — acknowledgment still depends on actual lag, independent of the pause flag
      assertThat(replicationController.isReplicationInSync()).isTrue();
      verify(controller).updateLastExportedRecordPosition(100L);
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
    void shouldTreatNullLagAsZero() {
      // given
      final var replicationController = createController();
      final var statuses = List.of(new ReplicationLagStatus("r1", null));

      // when
      final Duration lag = replicationController.computeMaxReplicaLag(statuses);

      // then
      assertThat(lag).isEqualTo(Duration.ZERO);
    }
  }
}
