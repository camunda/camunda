/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.api.response.Topology;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConnectionMonitor}. Covers the two behaviors the extracted component is
 * responsible for — flipping the {@code app.connected} gauge on first successful topology, and
 * retrying transient failures.
 */
class ConnectionMonitorTest {

  @Test
  void shouldFlipConnectedGaugeOnSuccessfulTopology() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var client = mockClientReturning(topologySuccess());
    final var monitor = new ConnectionMonitor(client, registry);

    // when
    monitor.awaitAndPrintTopology();

    // then
    assertThat(registry.find(AppMetricsDoc.CONNECTED.getName()).gauge().value())
        .describedAs("app.connected gauge should be 1 after topology succeeds")
        .isEqualTo(1.0);
  }

  @Test
  void shouldRetryOnTransientFailureAndSucceed() {
    // given — first join() throws, second returns successfully
    final var registry = new SimpleMeterRegistry();
    final var topology = mock(Topology.class);
    when(topology.getBrokers()).thenReturn(Collections.emptyList());

    final CamundaFuture<Topology> firstAttempt = mock(CamundaFuture.class);
    when(firstAttempt.join()).thenThrow(new RuntimeException("transient"));

    final CamundaFuture<Topology> secondAttempt = mock(CamundaFuture.class);
    when(secondAttempt.join()).thenReturn(topology);

    final var request = mock(TopologyRequestStep1.class);
    when(request.send()).thenReturn(firstAttempt, secondAttempt);

    final var client = mock(CamundaClient.class);
    when(client.newTopologyRequest()).thenReturn(request);

    final var monitor = new ConnectionMonitor(client, registry);

    // when
    monitor.awaitAndPrintTopology();

    // then
    assertThat(registry.find(AppMetricsDoc.CONNECTED.getName()).gauge().value())
        .describedAs("app.connected should still flip to 1 after one failed attempt")
        .isEqualTo(1.0);
  }

  @Test
  void shouldInitializeConnectedGaugeAtZeroBeforeTopologyCompletes() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var client = mock(CamundaClient.class);

    // when — just construct, do not call awaitAndPrintTopology
    new ConnectionMonitor(client, registry);

    // then
    assertThat(registry.find(AppMetricsDoc.CONNECTED.getName()).gauge().value())
        .describedAs("Gauge must be registered at 0 immediately on bean creation")
        .isEqualTo(0.0);
  }

  /** Returns a completed {@link CamundaFuture} whose {@link Topology} has no brokers. */
  private static CamundaFuture<Topology> topologySuccess() {
    final var topology = mock(Topology.class);
    when(topology.getBrokers()).thenReturn(Collections.emptyList());
    final CamundaFuture<Topology> future = mock(CamundaFuture.class);
    when(future.join()).thenReturn(topology);
    return future;
  }

  private static CamundaClient mockClientReturning(final CamundaFuture<Topology> future) {
    final var request = mock(TopologyRequestStep1.class);
    when(request.send()).thenReturn(future);
    final var client = mock(CamundaClient.class);
    when(client.newTopologyRequest()).thenReturn(request);
    return client;
  }
}
