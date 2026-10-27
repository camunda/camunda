/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.replication.ReplicationLagProvider;
import io.camunda.db.rdbms.read.replication.ReplicationLagStatus;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TimeMonitoringReplicationSignalStrategyTest {

  private ReplicationLagProvider statusProvider;
  private ReplicationConfiguration config;

  @BeforeEach
  void setUp() {
    statusProvider = mock(ReplicationLagProvider.class);
    config = new ReplicationConfiguration();
    config.setMinSyncReplicas(1);
  }

  private TimeMonitoringReplicationSignalStrategy createStrategy() {
    return new TimeMonitoringReplicationSignalStrategy(statusProvider, config);
  }

  @Test
  void shouldCaptureCurrentDbClockMsAsFlushMarker() {
    // given
    when(statusProvider.getCurrentDbTime()).thenReturn(4_200L);
    final var strategy = createStrategy();

    // when / then
    assertThat(strategy.captureFlushMarker()).isEqualTo(4_200L);
  }

  @Test
  void shouldFetchStatusesFromProvider() {
    // given
    final var statuses = List.of(new ReplicationLagStatus("replica-1", 0L, 0L));
    when(statusProvider.getReplicationStatuses()).thenReturn(statuses);
    final var strategy = createStrategy();

    // when / then
    assertThat(strategy.fetchStatuses()).isEqualTo(statuses);
  }

  @Nested
  class ComputeConfirmedMarkerTest {

    @Test
    void shouldReturnUnconfirmedForEmptyList() {
      // given
      final var strategy = createStrategy();

      // when
      final long asOfMs = strategy.computeConfirmedMarker(List.of());

      // then
      assertThat(asOfMs).isEqualTo(ReplicationSignalStrategy.UNCONFIRMED);
    }

    @Test
    void shouldReturnUnconfirmedWhenFewerThanMinSyncReplicasConnected() {
      // given - minSyncReplicas=2, but only one replica reporting
      config.setMinSyncReplicas(2);
      final var strategy = createStrategy();
      final var statuses = List.of(new ReplicationLagStatus("r1", 5_000L, 20_000L));

      // when
      final long asOfMs = strategy.computeConfirmedMarker(statuses);

      // then
      assertThat(asOfMs).isEqualTo(ReplicationSignalStrategy.UNCONFIRMED);
    }

    @Test
    void shouldReturnLowestAsOfAmongTheTopMinSyncReplicas() {
      // given - minSyncReplicas=2 with 3 replicas connected; the worst of the two BEST replicas
      // determines the confirmed point, mirroring LsnReplicationSignalStrategy's LSN computation
      config.setMinSyncReplicas(2);
      final var strategy = createStrategy();
      final var statuses =
          List.of(
              new ReplicationLagStatus("r1", 5_000L, 20_000L),
              new ReplicationLagStatus("r2", 20_000L, 5_000L),
              new ReplicationLagStatus("r3", 10_000L, 10_000L));

      // when
      final long asOfMs = strategy.computeConfirmedMarker(statuses);

      // then - top 2 by as-of are r1 (20_000) and r3 (10_000); the straggler r2 (5_000) is outside
      // the required quorum and must not be able to block confirmation
      assertThat(asOfMs).isEqualTo(10_000L);
    }

    @Test
    void shouldIgnoreNullAsOfFromReplicaBeyondQuorum() {
      // given - minSyncReplicas=1, one replica genuinely confirmed, one extra with an unknown
      // as-of point; the extra replica falls outside the required quorum
      final var strategy = createStrategy();
      final var statuses =
          List.of(
              new ReplicationLagStatus("r1", 5_000L, 20_000L),
              new ReplicationLagStatus("r2", 5_000L, null));

      // when
      final long asOfMs = strategy.computeConfirmedMarker(statuses);

      // then
      assertThat(asOfMs).isEqualTo(20_000L);
    }

    @Test
    void shouldTreatNullAsOfAsWorstCaseWhenWithinQuorum() {
      // given - minSyncReplicas=2 with exactly 2 replicas connected, so both fall within the
      // required quorum; the unknown as-of point must not look like "confirmed up to now"
      config.setMinSyncReplicas(2);
      final var strategy = createStrategy();
      final var statuses =
          List.of(
              new ReplicationLagStatus("r1", 5_000L, 20_000L),
              new ReplicationLagStatus("r2", 5_000L, null));

      // when
      final long asOfMs = strategy.computeConfirmedMarker(statuses);

      // then
      assertThat(asOfMs).isEqualTo(ReplicationSignalStrategy.UNCONFIRMED);
    }
  }

  @Nested
  class ComputePauseLagTest {

    @Test
    void shouldReturnPauseWorstCaseForEmptyListWhenQuorumRequired() {
      // given - minSyncReplicas=1 (default), no replicas connected
      final var strategy = createStrategy();

      // when
      final Duration lag = strategy.computePauseLag(List.of(), Optional.empty());

      // then
      assertThat(lag).isEqualTo(ReplicationSignalStrategy.PAUSE_WORST_CASE);
    }

    @Test
    void shouldReturnPauseWorstCaseWhenQuorumNotMetAndQueueEmpty() {
      // given - minSyncReplicas=2, only one replica reporting, and no queue-head signal available
      config.setMinSyncReplicas(2);
      final var strategy = createStrategy();
      final var statuses = List.of(new ReplicationLagStatus("r1", 1_000L, 0L));

      // when
      final Duration lag = strategy.computePauseLag(statuses, Optional.empty());

      // then
      assertThat(lag).isEqualTo(ReplicationSignalStrategy.PAUSE_WORST_CASE);
    }

    @Test
    void shouldReturnQueueHeadAgeWhenQuorumNotMetButQueueNonEmpty() {
      // given - minSyncReplicas=2, only one replica reporting (with a much smaller reported lag
      // than the queue-head age), and a position is queued and waiting
      config.setMinSyncReplicas(2);
      final var strategy = createStrategy();
      final var statuses = List.of(new ReplicationLagStatus("r1", 100L, 0L));

      // when - a replica shortage is graced by queueHeadAge (and so, ultimately, by maxLag)
      // instead of forcing an immediate worst-case pause
      final Duration lag = strategy.computePauseLag(statuses, Optional.of(Duration.ofSeconds(5)));

      // then - queueHeadAge wins over the partial replica-reported lag
      assertThat(lag).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void shouldReturnQueueHeadAgeWhenNoReplicasConnectedButQueueNonEmpty() {
      // given - no replicas reporting at all: there is no replica-reported lag to fall back to,
      // but a position is queued and aging
      final var strategy = createStrategy();

      // when - must not silently reduce to Duration.ZERO just because statuses is empty; the
      // shared controller compares this against maxLag itself, so a pause happens once this
      // queue-head age actually exceeds maxLag, not immediately
      final Duration lag = strategy.computePauseLag(List.of(), Optional.of(Duration.ofSeconds(10)));

      // then
      assertThat(lag).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldReturnWorstLagAmongTheTopMinSyncReplicas() {
      // given - minSyncReplicas=2 with 3 replicas connected; only the worst of the two BEST
      // (lowest-lag) replicas determines the pause decision, mirroring
      // computeConfirmedMarker's top-N handling
      config.setMinSyncReplicas(2);
      final var strategy = createStrategy();
      final var statuses =
          List.of(
              new ReplicationLagStatus("r1", 5_000L),
              new ReplicationLagStatus("r2", 20_000L),
              new ReplicationLagStatus("r3", 10_000L));

      // when
      final Duration lag = strategy.computePauseLag(statuses, Optional.empty());

      // then - top 2 by lowest lag are r1 (5_000) and r3 (10_000); the straggler r2 (20_000) is
      // outside the required quorum and must not be able to force a pause on its own
      assertThat(lag).isEqualTo(Duration.ofMillis(10_000L));
    }

    @Test
    void shouldTreatNullLagAsWorstCase() {
      // given - an unknown lag must not look like "no lag at all"
      final var strategy = createStrategy();
      final var statuses = List.of(new ReplicationLagStatus("r1", null));

      // when
      final Duration lag = strategy.computePauseLag(statuses, Optional.empty());

      // then
      assertThat(lag).isEqualTo(Duration.ofMillis(Long.MAX_VALUE));
    }

    @Test
    void shouldIgnoreQueueHeadAge() {
      // given - this signal has its own replica-reported lag, so queueHeadAge must not influence
      // the result at all, whether empty or present
      final var strategy = createStrategy();
      final var statuses = List.of(new ReplicationLagStatus("r1", 5_000L));

      // when
      final Duration lagWithNoQueueAge = strategy.computePauseLag(statuses, Optional.empty());
      final Duration lagWithHugeQueueAge =
          strategy.computePauseLag(statuses, Optional.of(Duration.ofDays(365)));

      // then
      assertThat(lagWithNoQueueAge)
          .isEqualTo(lagWithHugeQueueAge)
          .isEqualTo(Duration.ofMillis(5_000L));
    }
  }
}
