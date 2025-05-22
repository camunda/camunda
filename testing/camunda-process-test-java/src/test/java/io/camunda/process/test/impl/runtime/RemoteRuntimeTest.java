/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.process.test.api.CamundaRuntimeMode;
import java.net.URI;
import java.util.Collections;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RemoteRuntimeTest {

  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private Topology topology;

  @Test
  void shouldCreateRuntime() {
    // given/when
    final CamundaRuntime camundaRuntime =
        CamundaContainerRuntime.newBuilder().withRuntimeMode(CamundaRuntimeMode.REMOTE).build();

    // then
    assertThat(camundaRuntime).isInstanceOf(RemoteRuntime.class);
  }

  @Test
  void shouldCreateRuntimeWithDefaults() {
    // given/when
    final CamundaRuntime camundaRuntime =
        CamundaContainerRuntime.newBuilder().withRuntimeMode(CamundaRuntimeMode.REMOTE).build();

    // then
    assertThat(camundaRuntime.getCamundaRestApiAddress())
        .hasHost("0.0.0.0")
        .hasPort(ContainerRuntimePorts.CONNECTORS_REST_API);

    assertThat(camundaRuntime.getCamundaGrpcApiAddress())
        .hasHost("0.0.0.0")
        .hasPort(ContainerRuntimePorts.CAMUNDA_GATEWAY_API);

    assertThat(camundaRuntime.getCamundaMonitoringApiAddress())
        .isEqualTo(URI.create(ContainerRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS));

    assertThat(camundaRuntime.getConnectorsRestApiAddress())
        .isEqualTo(URI.create(ContainerRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS));
  }

  @Test
  void shouldConfigureRuntime() {
    // given
    final String remoteCamundaMonitoringApiAddress = "http://camunda.com:1000";
    final String remoteConnectorsRestApiAddress = "http://camunda.com:2000";

    // when
    final CamundaRuntime camundaRuntime =
        CamundaContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaRuntimeMode.REMOTE)
            .withRemoteCamundaMonitoringApiAddress(remoteCamundaMonitoringApiAddress)
            .withRemoteConnectorsRestApiAddress(remoteConnectorsRestApiAddress)
            .build();

    // then
    assertThat(camundaRuntime.getCamundaMonitoringApiAddress())
        .isEqualTo(URI.create(remoteCamundaMonitoringApiAddress));

    assertThat(camundaRuntime.getConnectorsRestApiAddress())
        .isEqualTo(URI.create(remoteConnectorsRestApiAddress));
  }

  @Test
  void shouldConfigureRuntimeWithClientBuilder() {
    // given
    final URI camundaRestApiAddress = URI.create("http://camunda.com:1000");
    final URI camundaGrpcApiAddress = URI.create("http://camunda.com:2000");

    final Supplier<CamundaClientBuilder> clientBuilderSupplier =
        () ->
            CamundaClient.newClientBuilder()
                .restAddress(camundaRestApiAddress)
                .grpcAddress(camundaGrpcApiAddress);

    // when
    final CamundaRuntime camundaRuntime =
        CamundaContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaRuntimeMode.REMOTE)
            .withCamundaClientBuilder(clientBuilderSupplier)
            .build();

    // then
    assertThat(camundaRuntime.getCamundaRestApiAddress()).isEqualTo(camundaRestApiAddress);
    assertThat(camundaRuntime.getCamundaGrpcApiAddress()).isEqualTo(camundaGrpcApiAddress);

    assertThat(camundaRuntime.getClientBuilderSupplier()).isEqualTo(clientBuilderSupplier);
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

    final CamundaRuntime camundaRuntime =
        CamundaContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaRuntimeMode.REMOTE)
            .withCamundaClientBuilder(() -> camundaClientBuilder)
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

    final CamundaRuntime camundaRuntime =
        CamundaContainerRuntime.newBuilder()
            .withRuntimeMode(CamundaRuntimeMode.REMOTE)
            .withCamundaClientBuilder(() -> camundaClientBuilder)
            .build();

    // when/then
    assertThatThrownBy(camundaRuntime::start)
        .hasMessage("Failed to connect to remote Camunda runtime.");
  }
}
