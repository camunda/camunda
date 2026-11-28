/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.monitoring.DetectorNodeMetrics;
import io.camunda.zeebe.broker.system.monitoring.HealthTreeMetrics;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class HealthDetectorNodeMetricsTest {

  private static final String NODES = "zeebe.broker.health.nodes";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final DetectorNodeMetrics metrics = new DetectorNodeMetrics(registry, "Broker-0");
  private final TestDetector detector = new TestDetector();

  @Test
  void shouldReportEachChildAsANodeWithItsPartition() {
    // given
    detector.exposeChildren(metrics);

    // when
    detector.markDegraded("Partition-default-1", "slow", Instant.EPOCH);
    detector.markHealthy("Member-2");

    // then
    final var partition =
        registry.get(NODES).tag("path", "Broker-0/Spikes/Partition-default-1").gauge();
    assertThat(partition.getId().getTag("physicalTenant")).isEqualTo("default");
    assertThat(partition.getId().getTag("partition")).isEqualTo("1");
    assertThat(partition.value()).isEqualTo(HealthTreeMetrics.statusValue(HealthStatus.DEGRADED));
    final var member = registry.get(NODES).tag("path", "Broker-0/Spikes/Member-2").gauge();
    assertThat(member.getId().getTag("partition")).isEqualTo("none");
  }

  @Test
  void shouldFollowTheChildsStatusAndRemoveItWhenTheChildGoes() {
    // given
    detector.exposeChildren(metrics);
    detector.markDegraded("Partition-default-1", "slow", Instant.EPOCH);
    final var gauge =
        registry.get(NODES).tag("path", "Broker-0/Spikes/Partition-default-1").gauge();

    // when
    detector.markHealthy("Partition-default-1");

    // then
    assertThat(gauge.value()).isEqualTo(HealthTreeMetrics.statusValue(HealthStatus.HEALTHY));

    // when
    detector.remove("Partition-default-1");

    // then
    assertThat(registry.find(NODES).gauges()).isEmpty();
  }

  @Test
  void shouldKeepAnExporterChildOnItsPartition() {
    // given
    detector.exposeChildren(metrics);

    // when
    detector.markHealthy("Partition-tenant-a-3:rdbms");

    // then
    final var gauge = registry.get(NODES).gauge();
    assertThat(gauge.getId().getTag("physicalTenant")).isEqualTo("tenant-a");
    assertThat(gauge.getId().getTag("partition")).isEqualTo("3");
  }

  private static final class TestDetector extends HealthDetector {
    private TestDetector() {
      super("Spikes");
    }
  }
}
