/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class DistributionMetricsTest {

  private static final String PENDING = "zeebe.command.distributions.pending";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final DistributionMetrics metrics = new DistributionMetrics(registry);

  @Test
  void shouldTagEveryDeclaredQueueWithItsOwnId() {
    // given
    final var queues = DistributionQueue.getValues();

    // when
    Arrays.stream(queues).forEach(q -> metrics.addPendingDistribution(2, q.getQueueId()));

    // then
    assertThat(queues)
        .allSatisfy(q -> assertThat(pending(2, q.getQueueId())).as(q.getQueueId()).isEqualTo(1.0));
  }

  @Test
  void shouldTagDistributionsWithoutQueueAsNone() {
    // when
    metrics.addPendingDistribution(2, null);

    // then
    assertThat(pending(2, DistributionMetrics.NO_QUEUE)).isEqualTo(1.0);
  }

  @Test
  void shouldNotCreateMetersPerUserControlledQueueId() {
    // given - e.g. cluster variable names are used as queue ids
    final var queueIds = IntStream.range(0, 100).mapToObj(i -> "variable-" + i).toList();

    // when
    queueIds.forEach(id -> metrics.addPendingDistribution(2, id));
    queueIds.forEach(id -> metrics.removePendingDistribution(2, id));
    queueIds.forEach(id -> metrics.retryInflightDistribution(2, id));

    // then
    assertThat(registry.find(PENDING).gauges()).hasSize(1);
    assertThat(pending(2, DistributionMetrics.OTHER_QUEUE)).isZero();
    assertThat(
            registry
                .find("zeebe.command.distributions.inflight.retries")
                .tag("queue", DistributionMetrics.OTHER_QUEUE)
                .counter()
                .count())
        .isEqualTo(100.0);
  }

  @Test
  void shouldNotTagSentAcknowledgementsWithQueue() {
    // when
    metrics.sentAcknowledgeDistribution(1);
    metrics.sentAcknowledgeDistribution(1);

    // then
    final var counter = registry.find("zeebe.command.distributions.acknowledged.sent").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(2.0);
    assertThat(counter.getId().getTag("queue")).isNull();
    assertThat(counter.getId().getTag("targetPartition")).isEqualTo("1");
  }

  private double pending(final int targetPartition, final String queue) {
    return registry
        .get(PENDING)
        .tag("targetPartition", String.valueOf(targetPartition))
        .tag("queue", queue)
        .gauge()
        .value();
  }
}
