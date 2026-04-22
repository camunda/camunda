/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.TopologyRequestStep1;
import io.camunda.zeebe.client.api.response.Topology;
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

    final ZeebeFuture<Topology> firstAttempt = mock(ZeebeFuture.class);
    when(firstAttempt.join()).thenThrow(new RuntimeException("transient"));

    final ZeebeFuture<Topology> secondAttempt = mock(ZeebeFuture.class);
    when(secondAttempt.join()).thenReturn(topology);

    final var request = mock(TopologyRequestStep1.class);
    when(request.send()).thenReturn(firstAttempt, secondAttempt);

    final var client = mock(ZeebeClient.class);
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
    final var client = mock(ZeebeClient.class);

    // when — just construct, do not call awaitAndPrintTopology
    new ConnectionMonitor(client, registry);

    // then
    assertThat(registry.find(AppMetricsDoc.CONNECTED.getName()).gauge().value())
        .describedAs("Gauge must be registered at 0 immediately on bean creation")
        .isEqualTo(0.0);
  }

  /** Returns a completed {@link ZeebeFuture} whose {@link Topology} has no brokers. */
  private static ZeebeFuture<Topology> topologySuccess() {
    final var topology = mock(Topology.class);
    when(topology.getBrokers()).thenReturn(Collections.emptyList());
    final ZeebeFuture<Topology> future = mock(ZeebeFuture.class);
    when(future.join()).thenReturn(topology);
    return future;
  }

  private static ZeebeClient mockClientReturning(final ZeebeFuture<Topology> future) {
    final var request = mock(TopologyRequestStep1.class);
    when(request.send()).thenReturn(future);
    final var client = mock(ZeebeClient.class);
    when(client.newTopologyRequest()).thenReturn(request);
    return client;
  }
}
