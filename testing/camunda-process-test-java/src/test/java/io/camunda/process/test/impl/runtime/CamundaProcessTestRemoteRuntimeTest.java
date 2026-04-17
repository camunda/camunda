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
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CamundaProcessTestRemoteRuntimeTest {

  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private Topology topology;

  @Test
  void shouldCreateRuntime() {
    // given/when
    final CamundaProcessTestRuntime camundaRuntime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE)
            .build();

    // then
    assertThat(camundaRuntime).isInstanceOf(CamundaProcessTestRemoteRuntime.class);
  }

  @Test
  void shouldCreateRuntimeWithDefaults() {
    // given/when
    final CamundaProcessTestRuntime camundaRuntime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE)
            .build();

    // then
    assertThat(camundaRuntime.getCamundaRestApiAddress())
        .hasHost("0.0.0.0")
        .hasPort(ContainerRuntimePorts.CAMUNDA_REST_API);

    assertThat(camundaRuntime.getCamundaGrpcApiAddress())
        .hasHost("0.0.0.0")
        .hasPort(ContainerRuntimePorts.CAMUNDA_GATEWAY_API);

    assertThat(camundaRuntime.getCamundaMonitoringApiAddress())
        .isEqualTo(CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS);

    assertThat(camundaRuntime.getConnectorsRestApiAddress())
        .isEqualTo(CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS);
  }

  @Test
  void shouldConfigureRuntime() {
    // given
    final URI remoteCamundaMonitoringApiAddress = URI.create("http://camunda.com:1000");
    final URI remoteConnectorsRestApiAddress = URI.create("http://camunda.com:2000");

    // when
    final CamundaProcessTestRuntime camundaRuntime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE)
            .withRemoteCamundaMonitoringApiAddress(remoteCamundaMonitoringApiAddress)
            .withRemoteConnectorsRestApiAddress(remoteConnectorsRestApiAddress)
            .build();

    // then
    assertThat(camundaRuntime.getCamundaMonitoringApiAddress())
        .isEqualTo(remoteCamundaMonitoringApiAddress);

    assertThat(camundaRuntime.getConnectorsRestApiAddress())
        .isEqualTo(remoteConnectorsRestApiAddress);
  }

  @Test
  void shouldConfigureRuntimeWithClientBuilder() {
    // given
    final URI camundaRestApiAddress = URI.create("http://camunda.com:1000");
    final URI camundaGrpcApiAddress = URI.create("http://camunda.com:2000");

    final CamundaClientBuilderFactory clientBuilderFactory =
        () ->
            CamundaClient.newClientBuilder()
                .restAddress(camundaRestApiAddress)
                .grpcAddress(camundaGrpcApiAddress);

    // when
    final CamundaProcessTestRuntime camundaRuntime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE)
            .withCamundaClientBuilderFactory(clientBuilderFactory)
            .build();

    // then
    assertThat(camundaRuntime.getCamundaRestApiAddress()).isEqualTo(camundaRestApiAddress);
    assertThat(camundaRuntime.getCamundaGrpcApiAddress()).isEqualTo(camundaGrpcApiAddress);
  }

  @Test
  void shouldCheckConnectionOnStart() {
    // given
    when(camundaClientBuilder.build()).thenReturn(camundaClient);

    when(camundaClient.newTopologyRequest().send().join()).thenReturn(topology);

    final BrokerInfo brokerInfo = mock(BrokerInfo.class);
    final PartitionInfo partitionInfo = mock(PartitionInfo.class);
    when(brokerInfo.getPartitions()).thenReturn(Collections.singletonList(partitionInfo));
    when(partitionInfo.getHealth()).thenReturn(PartitionBrokerHealth.HEALTHY);

    when(topology.getBrokers()).thenReturn(Collections.singletonList(brokerInfo));
    when(topology.getGatewayVersion()).thenReturn("testing");

    final CamundaProcessTestRuntime camundaRuntime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE)
            .withCamundaClientBuilderFactory(() -> camundaClientBuilder)
            .build();

    // when/then
    assertThatNoException().isThrownBy(camundaRuntime::start);

    // note: 2 times = 1 for stubbing + 1 for the remote runtime
    verify(camundaClient, times(2)).newTopologyRequest();
  }

  @Test
  void shouldFailOnStartIfConnectionFails() {
    // given
    when(camundaClientBuilder.build()).thenReturn(camundaClient);

    when(camundaClient.newTopologyRequest().send().join())
        .thenThrow(new ClientException("expected"));

    final CamundaProcessTestRuntime camundaRuntime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE)
            .withCamundaClientBuilderFactory(() -> camundaClientBuilder)
            .withRemoteRuntimeConnectionTimeout(Duration.ofSeconds(1))
            .build();

    // when/then
    assertThatThrownBy(camundaRuntime::start)
        .hasMessage("Failed to connect to remote Camunda runtime.");
  }

  @Test
  void shouldFailOnStartIfTheRuntimeIsUnhealthy() {
    // given
    when(camundaClientBuilder.build()).thenReturn(camundaClient);

    when(camundaClient.newTopologyRequest().send().join()).thenReturn(topology);

    final BrokerInfo brokerInfo = mock(BrokerInfo.class);

    final PartitionInfo healthyPartition = mock(PartitionInfo.class);
    when(healthyPartition.getHealth()).thenReturn(PartitionBrokerHealth.HEALTHY);

    final PartitionInfo unhealthyPartition = mock(PartitionInfo.class);
    when(unhealthyPartition.getHealth()).thenReturn(PartitionBrokerHealth.UNHEALTHY);

    when(brokerInfo.getPartitions())
        .thenReturn(Arrays.asList(healthyPartition, unhealthyPartition));

    when(topology.getBrokers()).thenReturn(Collections.singletonList(brokerInfo));

    final CamundaProcessTestRuntime camundaRuntime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE)
            .withCamundaClientBuilderFactory(() -> camundaClientBuilder)
            .withRemoteRuntimeConnectionTimeout(Duration.ofSeconds(1))
            .build();

    // when/then
    assertThatThrownBy(camundaRuntime::start)
        .hasMessageContaining("Remote Camunda runtime is unhealthy. [topology:");
  }

  @Test
  void shouldFailOnStartIfNoPartitionsAreAvailable() {
    // given
    when(camundaClientBuilder.build()).thenReturn(camundaClient);

    when(camundaClient.newTopologyRequest().send().join()).thenReturn(topology);

    final BrokerInfo brokerInfo = mock(BrokerInfo.class);
    when(brokerInfo.getPartitions()).thenReturn(Collections.emptyList());

    when(topology.getBrokers()).thenReturn(Collections.singletonList(brokerInfo));

    final CamundaProcessTestRuntime camundaRuntime =
        CamundaProcessTestContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaProcessTestRuntimeMode.REMOTE)
            .withCamundaClientBuilderFactory(() -> camundaClientBuilder)
            .withRemoteRuntimeConnectionTimeout(Duration.ofSeconds(1))
            .build();

    // when/then
    assertThatThrownBy(camundaRuntime::start)
        .hasMessageContaining(
            "Remote Camunda runtime has zero available partitions. Please check the remote runtime logs for errors.");
  }
}
