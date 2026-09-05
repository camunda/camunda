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

import io.camunda.db.rdbms.read.replication.ReplicationLagStatus;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DelayReplicationSignalStrategyTest {

  private static final Duration DELAY = Duration.ofSeconds(30);

  private ReplicationConfiguration config;
  private InstantSource clock;

  @BeforeEach
  void setUp() {
    config = new ReplicationConfiguration();
    config.setDelay(DELAY);
    clock = mock(InstantSource.class);
  }

  private DelayReplicationSignalStrategy createStrategy() {
    return new DelayReplicationSignalStrategy(config, clock);
  }

  @Test
  void shouldCaptureNowPlusDelayAsFlushMarker() {
    // given
    when(clock.millis()).thenReturn(1_000L);
    final var strategy = createStrategy();

    // when / then - the release time, captured once at flush
    assertThat(strategy.captureFlushMarker()).isEqualTo(1_000L + DELAY.toMillis());
  }

  @Test
  void shouldFetchNoStatuses() {
    // given - there is no provider, no per-replica signal exists for this mode
    final var strategy = createStrategy();

    // when / then
    assertThat(strategy.fetchStatuses()).isEmpty();
  }

  @Test
  void shouldReturnCurrentTimeAsConfirmedMarkerRegardlessOfStatuses() {
    // given
    when(clock.millis()).thenReturn(5_000L);
    final var strategy = createStrategy();

    // when / then - ignores statuses entirely; combined with a marker of flushTime + delay, this
    // reproduces "releaseTimeMs <= now" without any replica signal
    assertThat(strategy.computeConfirmedMarker(List.of())).isEqualTo(5_000L);
    assertThat(strategy.computeConfirmedMarker(List.of(new ReplicationLagStatus("r1", 0L))))
        .isEqualTo(5_000L);
  }

  @Test
  void shouldNeverReturnMoreThanZeroPauseLag() {
    // given - there is no replication signal to judge the exporter out of sync by
    final var strategy = createStrategy();

    // when / then
    assertThat(strategy.computePauseLag(List.of(), Optional.empty())).isEqualTo(Duration.ZERO);
    assertThat(strategy.computePauseLag(List.of(), Optional.of(Duration.ofDays(365))))
        .isEqualTo(Duration.ZERO);
  }

  @Test
  void shouldWaitFullDelayWhenQueueIsEmpty() {
    // given - nothing queued, so there is no release time to wake up for specifically
    final var strategy = createStrategy();

    // when
    final Duration nextDelay = strategy.nextCheckDelay(Duration.ofSeconds(5), Optional.empty());

    // then
    assertThat(nextDelay).isEqualTo(DELAY);
  }

  @Test
  void shouldWaitOnlyUntilOldestQueuedEntryIsDue() {
    // given - the oldest queued entry has already waited 10s out of a 30s delay
    final var strategy = createStrategy();

    // when
    final Duration nextDelay =
        strategy.nextCheckDelay(Duration.ofSeconds(5), Optional.of(Duration.ofSeconds(10)));

    // then
    assertThat(nextDelay).isEqualTo(Duration.ofSeconds(20));
  }

  @Test
  void shouldFloorToOneMillisecondWhenEntryIsAlreadyOverdue() {
    // given - the oldest queued entry has been waiting longer than the delay itself
    final var strategy = createStrategy();

    // when
    final Duration nextDelay =
        strategy.nextCheckDelay(Duration.ofSeconds(5), Optional.of(Duration.ofSeconds(45)));

    // then - never returns zero or negative, which would busy-loop the scheduler
    assertThat(nextDelay).isEqualTo(Duration.ofMillis(1));
  }
}
