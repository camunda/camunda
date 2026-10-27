/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

final class LeaderAppenderMetricsTest {

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final LeaderAppenderMetrics metrics =
      new LeaderAppenderMetrics("myTenant-2", meterRegistry);

  @Test
  void shouldRegisterReplicationLagGaugeWithExpectedNameAndTags() {
    // when
    metrics.observeReplicationLagBytes("follower-1", 4096);

    // then
    final var gauge =
        meterRegistry
            .get("zeebe.raft.replication.lag.bytes")
            .tag("partition", "2")
            .tag("physicalTenant", "myTenant")
            .tag("follower", "follower-1")
            .gauge();
    assertThat(gauge.value()).isEqualTo(4096.0);
  }

  @Test
  void shouldUpdateReplicationLagGaugeValue() {
    // given
    metrics.observeReplicationLagBytes("follower-1", 4096);

    // when
    metrics.observeReplicationLagBytes("follower-1", 1024);

    // then
    assertThat(meterRegistry.get("zeebe.raft.replication.lag.bytes").gauge().value())
        .isEqualTo(1024.0);
  }

  @Test
  void shouldTrackReplicationLagPerFollower() {
    // when
    metrics.observeReplicationLagBytes("follower-1", 4096);
    metrics.observeReplicationLagBytes("follower-2", 2048);

    // then
    assertThat(
            meterRegistry
                .get("zeebe.raft.replication.lag.bytes")
                .tag("follower", "follower-1")
                .gauge()
                .value())
        .isEqualTo(4096.0);
    assertThat(
            meterRegistry
                .get("zeebe.raft.replication.lag.bytes")
                .tag("follower", "follower-2")
                .gauge()
                .value())
        .isEqualTo(2048.0);
  }

  @Test
  void shouldRemoveReplicationLagGaugeOnClose() {
    // given
    metrics.observeReplicationLagBytes("follower-1", 4096);

    // when
    metrics.close();

    // then
    assertThat(meterRegistry.find("zeebe.raft.replication.lag.bytes").gauge()).isNull();
  }
}
