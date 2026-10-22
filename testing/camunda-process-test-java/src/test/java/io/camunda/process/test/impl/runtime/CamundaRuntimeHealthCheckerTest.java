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
package io.camunda.process.test.impl.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the cluster is only reported as ready once the engine processes commands. The
 * topology can report healthy partitions while the command API handler of the partition leader is
 * not registered yet, which fails deployments with a server error. The engine rejects an invalid
 * resource with a client error only once it processes commands.
 */
public class CamundaRuntimeHealthCheckerTest {

  private final CamundaClient client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
  private final CamundaClientBuilder builder = mock(CamundaClientBuilder.class);
  private final CamundaClientBuilderFactory factory = () -> builder;

  @Test
  void shouldWaitUntilEngineRejectsInvalidResource() {
    // given
    when(builder.build()).thenReturn(client);
    givenHealthyTopology();
    final CamundaFuture<DeploymentEvent> notReady = failedFuture(problem(500));
    final CamundaFuture<DeploymentEvent> ready = failedFuture(brokerRejection());
    when(client
            .newDeployResourceCommand()
            .useRest()
            .addResourceBytes(any(byte[].class), anyString())
            .send())
        .thenReturn(notReady)
        .thenReturn(ready);

    // when
    final Topology topology =
        CamundaRuntimeHealthChecker.waitUntilClusterReady(factory, Duration.ofSeconds(10));

    // then
    assertThat(topology).isNotNull();
    verify(notReady).join();
    verify(ready).join();
  }

  @Test
  void shouldNotBeReadyWhenProbeFailsWithOtherClientError() {
    // given
    when(builder.build()).thenReturn(client);
    givenHealthyTopology();
    final CamundaFuture<DeploymentEvent> unauthorized = failedFuture(problem(401));
    when(client
            .newDeployResourceCommand()
            .useRest()
            .addResourceBytes(any(byte[].class), anyString())
            .send())
        .thenReturn(unauthorized);

    // when / then
    assertThatThrownBy(
            () -> CamundaRuntimeHealthChecker.waitUntilClusterReady(factory, Duration.ofSeconds(1)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldNotBeReadyWhenGatewayRejectsProbe() {
    // given
    when(builder.build()).thenReturn(client);
    givenHealthyTopology();
    final CamundaFuture<DeploymentEvent> gatewayRejection =
        failedFuture(
            problem(
                400,
                "Expected to handle request Deploy Resources with tenant identifier 'other', but"
                    + " multi-tenancy is disabled"));
    when(client
            .newDeployResourceCommand()
            .useRest()
            .addResourceBytes(any(byte[].class), anyString())
            .send())
        .thenReturn(gatewayRejection);

    // when / then
    assertThatThrownBy(
            () -> CamundaRuntimeHealthChecker.waitUntilClusterReady(factory, Duration.ofSeconds(1)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldNotProbeWhenPartitionsAreUnhealthy() {
    // given
    when(builder.build()).thenReturn(client);
    givenTopology(PartitionBrokerHealth.UNHEALTHY);

    // when / then
    assertThatThrownBy(
            () -> CamundaRuntimeHealthChecker.waitUntilClusterReady(factory, Duration.ofSeconds(1)))
        .isInstanceOf(RuntimeException.class);
    verify(client, never()).newDeployResourceCommand();
  }

  private void givenHealthyTopology() {
    givenTopology(PartitionBrokerHealth.HEALTHY);
  }

  private void givenTopology(final PartitionBrokerHealth health) {
    final Topology topology = mock(Topology.class);
    final BrokerInfo broker = mock(BrokerInfo.class);
    final PartitionInfo partition = mock(PartitionInfo.class);
    when(partition.getHealth()).thenReturn(health);
    when(broker.getPartitions()).thenReturn(Collections.singletonList(partition));
    when(topology.getBrokers()).thenReturn(Collections.singletonList(broker));
    final CamundaFuture<Topology> topologyFuture = completedFuture(topology);
    when(client.newTopologyRequest().send()).thenReturn(topologyFuture);
  }

  private static ProblemException problem(final int code) {
    return problem(code, "problem");
  }

  private static ProblemException problem(final int code, final String detail) {
    return new ProblemException(code, "problem", new ProblemDetail().setDetail(detail));
  }

  private static ProblemException brokerRejection() {
    return problem(
        400,
        "Command 'CREATE' rejected with code 'INVALID_ARGUMENT': Expected to deploy new resources,"
            + " but encountered the following errors:");
  }

  private static <T> CamundaFuture<T> completedFuture(final T value) {
    final CamundaFuture<T> future = mock(CamundaFuture.class);
    when(future.join()).thenReturn(value);
    return future;
  }

  private static <T> CamundaFuture<T> failedFuture(final RuntimeException exception) {
    final CamundaFuture<T> future = mock(CamundaFuture.class);
    when(future.join()).thenThrow(exception);
    return future;
  }
}
