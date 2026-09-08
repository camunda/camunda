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

import io.camunda.db.rdbms.read.replication.ReplicationLsnProvider;
import io.camunda.db.rdbms.read.replication.ReplicationLsnStatus;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.RegionConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LsnReplicationSignalStrategyTest {

  private ReplicationLsnProvider lsnProvider;
  private ReplicationConfiguration config;

  @BeforeEach
  void setUp() {
    lsnProvider = mock(ReplicationLsnProvider.class);
    config = new ReplicationConfiguration();
    config.setMinSyncReplicas(1);
    when(lsnProvider.getCurrent()).thenReturn(100L);
  }

  private LsnReplicationSignalStrategy createStrategy() {
    return new LsnReplicationSignalStrategy(lsnProvider, config);
  }

  @Test
  void shouldCaptureCurrentLsnAsFlushMarker() {
    // given
    when(lsnProvider.getCurrent()).thenReturn(42L);
    final var strategy = createStrategy();

    // when / then
    assertThat(strategy.captureFlushMarker()).isEqualTo(42L);
  }

  @Test
  void shouldFetchStatusesFromProvider() {
    // given
    final var statuses = List.of(new ReplicationLsnStatus(10L, "replica-1", 0L));
    when(lsnProvider.getReplicationStatuses()).thenReturn(statuses);
    final var strategy = createStrategy();

    // when / then
    assertThat(strategy.fetchStatuses()).isEqualTo(statuses);
  }

  @Nested
  class ComputeConfirmedMarkerTest {

    @Test
    void shouldReturnUnconfirmedWhenCurrentLsnIsNegative() {
      // given
      when(lsnProvider.getCurrent()).thenReturn(-1L);
      final var strategy = createStrategy();

      // when
      final long result = strategy.computeConfirmedMarker(List.of());

      // then
      assertThat(result).isEqualTo(ReplicationSignalStrategy.UNCONFIRMED);
    }

    @Test
    void shouldReturnUnconfirmedWhenNotEnoughReplicas() {
      // given
      config.setMinSyncReplicas(2);
      when(lsnProvider.getCurrent()).thenReturn(50L);
      final var strategy = createStrategy();

      // when - only 1 replica but 2 required
      final long result =
          strategy.computeConfirmedMarker(List.of(new ReplicationLsnStatus(100L, "replica-1", 0L)));

      // then
      assertThat(result).isEqualTo(ReplicationSignalStrategy.UNCONFIRMED);
    }

    @Test
    void shouldReturnLowestOfTopNReplicaLsns() {
      // given - minSyncReplicas = 2, 3 replicas with lsn 10, 30, 50
      config.setMinSyncReplicas(2);
      when(lsnProvider.getCurrent()).thenReturn(50L);
      final var strategy = createStrategy();

      final var statuses =
          List.of(
              new ReplicationLsnStatus(10L, "replica-1", 0L),
              new ReplicationLsnStatus(30L, "replica-2", 0L),
              new ReplicationLsnStatus(50L, "replica-3", 0L));

      // when
      final long result = strategy.computeConfirmedMarker(statuses);

      // then - top 2 by lsn are 30 and 50; min of those is 30
      assertThat(result).isEqualTo(30L);
    }

    @Test
    void shouldReturnReplicaLsnWhenExactlyMinSyncReplicas() {
      // given
      when(lsnProvider.getCurrent()).thenReturn(50L);
      final var strategy = createStrategy();

      final var statuses = List.of(new ReplicationLsnStatus(40L, "replica-1", 0L));

      // when
      final long result = strategy.computeConfirmedMarker(statuses);

      // then
      assertThat(result).isEqualTo(40L);
    }
  }

  @Nested
  class ComputePauseLagTest {

    @Test
    void shouldReturnPauseWorstCaseWhenQuorumNotMetAndQueueEmpty() {
      // given - quorum lost, and the queue is empty so there's no other lag signal to judge
      // staleness by
      config.setMinSyncReplicas(2);
      final var strategy = createStrategy();

      // when
      final Duration lag =
          strategy.computePauseLag(
              List.of(new ReplicationLsnStatus(10L, "replica-1", 0L)), Optional.empty());

      // then
      assertThat(lag).isEqualTo(ReplicationSignalStrategy.PAUSE_WORST_CASE);
    }

    @Test
    void shouldNotReturnPauseWorstCaseWhenQuorumNotMetButQueueNonEmpty() {
      // given - quorum lost, but a position is still queued: its own queue-head-age already
      // signals staleness, so a replica shortage alone must not additionally force an immediate
      // pause on top of that
      config.setMinSyncReplicas(2);
      final var strategy = createStrategy();

      // when
      final Duration lag =
          strategy.computePauseLag(
              List.of(new ReplicationLsnStatus(10L, "replica-1", 0L)),
              Optional.of(Duration.ofSeconds(3)));

      // then
      assertThat(lag).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldReturnQueueHeadAgeWhenQuorumMet() {
      // given - this signal has no per-replica lag of its own, so it falls back to how long the
      // oldest still-unconfirmed queued entry has been waiting
      final var strategy = createStrategy();
      final var statuses = List.of(new ReplicationLsnStatus(10L, "replica-1", 0L));

      // when
      final Duration lag = strategy.computePauseLag(statuses, Optional.of(Duration.ofSeconds(7)));

      // then
      assertThat(lag).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void shouldReturnZeroWhenQuorumMetAndQueueEmpty() {
      // given - nothing queued and quorum is fine: no lag signal at all
      final var strategy = createStrategy();
      final var statuses = List.of(new ReplicationLsnStatus(10L, "replica-1", 0L));

      // when
      final Duration lag = strategy.computePauseLag(statuses, Optional.empty());

      // then
      assertThat(lag).isEqualTo(Duration.ZERO);
    }
  }

  @Nested
  class RegionAwareTest {

    @BeforeEach
    void setUpRegions() {
      config.getRegionAwareness().setEnabled(true);
      config.getRegionAwareness().setRegions(List.of(region("us-east", "us-east-.*", 1)));
    }

    @Test
    void shouldReturnUnconfirmedWhenOneMandatoryRegionFallsShortEvenIfGlobalCountIsEnough() {
      // given - two regions, each needs 1 replica; us-west has none even though us-east alone
      // would already satisfy a flat minSyncReplicas=1
      config
          .getRegionAwareness()
          .setRegions(
              List.of(region("us-east", "us-east-.*", 1), region("us-west", "us-west-.*", 1)));
      final var strategy = createStrategy();
      final var statuses =
          List.of(
              new ReplicationLsnStatus(50L, "replica-1", 0L, null, "us-east-1"),
              new ReplicationLsnStatus(60L, "replica-2", 0L, null, "us-east-2"));

      // when
      final long result = strategy.computeConfirmedMarker(statuses);

      // then
      assertThat(result).isEqualTo(ReplicationSignalStrategy.UNCONFIRMED);
    }

    @Test
    void shouldConfirmLowestLsnAmongEachRegionsOwnTopNThenWorstAcrossRegions() {
      // given - us-east needs 2, us-west needs 1
      config
          .getRegionAwareness()
          .setRegions(
              List.of(region("us-east", "us-east-.*", 2), region("us-west", "us-west-.*", 1)));
      final var strategy = createStrategy();
      final var statuses =
          List.of(
              new ReplicationLsnStatus(10L, "replica-1", 0L, null, "us-east-1"),
              new ReplicationLsnStatus(30L, "replica-2", 0L, null, "us-east-2"),
              new ReplicationLsnStatus(90L, "replica-3", 0L, null, "us-west-1"));

      // when
      final long result = strategy.computeConfirmedMarker(statuses);

      // then - us-east's own top 2 are 10 and 30, worst of those is 10; us-west's top 1 is 90;
      // the worst across both mandatory regions is 10
      assertThat(result).isEqualTo(10L);
    }

    @Test
    void shouldIgnoreReplicasNotMatchingAnyConfiguredRegion() {
      // given - only us-east is declared; an unrelated label must not count toward it
      final var strategy = createStrategy();
      final var statuses =
          List.of(new ReplicationLsnStatus(80L, "replica-1", 0L, null, "eu-central-1"));

      // when
      final long result = strategy.computeConfirmedMarker(statuses);

      // then - us-east still has 0 matching replicas against its minReplicas=1
      assertThat(result).isEqualTo(ReplicationSignalStrategy.UNCONFIRMED);
    }

    @Test
    void shouldCreditThePrimaryRegionSoOnlyRemainingSecondariesAreRequired() {
      // given - us-east hosts the primary and wants 2 nodes total; with the primary's automatic
      // credit, only 1 real secondary is needed to satisfy it
      config.getRegionAwareness().setPrimaryRegion("us-east");
      config.getRegionAwareness().setRegions(List.of(region("us-east", "us-east-.*", 2)));
      final var strategy = createStrategy();
      final var statuses =
          List.of(new ReplicationLsnStatus(70L, "replica-1", 0L, null, "us-east-1"));

      // when
      final long result = strategy.computeConfirmedMarker(statuses);

      // then - the primary's synthetic credit is always-best, so the real secondary's own lsn
      // (the worse of the two) is what gets returned
      assertThat(result).isEqualTo(70L);
    }

    @Test
    void shouldPauseWhenAMandatoryRegionIsBelowItsOwnMinReplicasAndQueueIsEmpty() {
      // given - us-east requires 1 replica but has none
      final var strategy = createStrategy();

      // when
      final Duration lag = strategy.computePauseLag(List.of(), Optional.empty());

      // then
      assertThat(lag).isEqualTo(ReplicationSignalStrategy.PAUSE_WORST_CASE);
    }

    @Test
    void shouldReportRegionsBelowQuorum() {
      // given - us-east requires 1 replica but has none
      final var strategy = createStrategy();

      // when
      final var below = strategy.regionsBelowQuorum(List.of());

      // then
      assertThat(below).containsExactly("us-east");
    }

    @Test
    void shouldReportNoRegionsBelowQuorumWhenHealthy() {
      // given
      final var strategy = createStrategy();
      final var statuses =
          List.of(new ReplicationLsnStatus(50L, "replica-1", 0L, null, "us-east-1"));

      // when
      final var below = strategy.regionsBelowQuorum(statuses);

      // then
      assertThat(below).isEmpty();
    }

    private RegionConfiguration region(
        final String name, final String pattern, final int minReplicas) {
      final var region = new RegionConfiguration();
      region.setName(name);
      region.setPattern(pattern);
      region.setMinReplicas(minReplicas);
      return region;
    }
  }
}
