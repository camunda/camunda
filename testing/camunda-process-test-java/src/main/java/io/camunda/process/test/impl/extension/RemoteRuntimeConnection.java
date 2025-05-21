/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.extension;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntimeBuilder;
import java.net.URI;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteRuntimeConnection implements CamundaRuntimeConnection {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteRuntimeConnection.class);

  private final URI camundaRestApiAddress;
  private final URI camundaGrpcApiAddress;
  private final URI camundaMonitoringApiAddress;
  private final URI connectorsRestApiAddress;
  private final Supplier<CamundaClientBuilder> camundaClientBuilderSupplier;

  public RemoteRuntimeConnection(final CamundaContainerRuntimeBuilder runtimeBuilder) {
    camundaClientBuilderSupplier = runtimeBuilder.getCamundaClientBuilderSupplier();
    camundaMonitoringApiAddress = runtimeBuilder.getRemoteCamundaMonitoringApiAddress();
    connectorsRestApiAddress = runtimeBuilder.getRemoteConnectorsRestApiAddress();

    final CamundaClientConfiguration clientConfiguration =
        getClientConfiguration(camundaClientBuilderSupplier);
    camundaRestApiAddress = clientConfiguration.getRestAddress();
    camundaGrpcApiAddress = clientConfiguration.getGrpcAddress();
  }

  @Override
  public void start() {
    // nothing to start. the runtime is managed remotely.
    LOGGER.info(
        "Connecting to remote runtime. [Camunda REST: {}, Camunda gRPC: {}, Camunda Monitoring: {}, Connectors REST: {}]",
        camundaRestApiAddress,
        camundaGrpcApiAddress,
        camundaMonitoringApiAddress,
        connectorsRestApiAddress);

    // check connection to remote runtime
    checkConnectionToRemoteRuntime();
  }

  @Override
  public URI getCamundaMonitoringApiAddress() {
    return camundaMonitoringApiAddress;
  }

  @Override
  public URI getCamundaRestApiAddress() {
    return camundaRestApiAddress;
  }

  @Override
  public URI getCamundaGrpcApiAddress() {
    return camundaGrpcApiAddress;
  }

  @Override
  public URI getConnectorsRestApiAddress() {
    return connectorsRestApiAddress;
  }

  @Override
  public Supplier<CamundaClientBuilder> createClientBuilder() {
    return camundaClientBuilderSupplier;
  }

  private void checkConnectionToRemoteRuntime() {
    try (final CamundaClient camundaClient = createClientBuilder().get().build()) {
      final Topology topology = camundaClient.newTopologyRequest().send().join();

      final boolean isHealthy =
          topology.getBrokers().stream()
              .flatMap(brokerInfo -> brokerInfo.getPartitions().stream())
              .map(PartitionInfo::getHealth)
              .allMatch(PartitionBrokerHealth.HEALTHY::equals);

      if (isHealthy) {
        LOGGER.info(
            "Remote Camunda runtime connected. [version: {}]", topology.getGatewayVersion());
      } else {
        LOGGER.warn("Remote Camunda runtime is unhealthy. [topology: {}]", topology);
      }

    } catch (final Exception e) {
      throw new RuntimeException("Failed to connect to remote Camunda runtime.", e);
    }
  }

  private CamundaClientConfiguration getClientConfiguration(
      final Supplier<CamundaClientBuilder> camundaClientBuilderSupplier) {
    final CamundaClientBuilder clientBuilder = camundaClientBuilderSupplier.get();
    try (final CamundaClient camundaClient = clientBuilder.build()) {
      return camundaClient.getConfiguration();
    }
  }

  @Override
  public void close() throws Exception {
    // nothing to close. the runtime is managed remotely.
  }
}
