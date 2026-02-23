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

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaProcessTestRemoteRuntime implements CamundaProcessTestRuntime {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaProcessTestRemoteRuntime.class);

  private final URI camundaRestApiAddress;
  private final URI camundaGrpcApiAddress;
  private final URI camundaMonitoringApiAddress;
  private final URI connectorsRestApiAddress;
  private final Duration runtimeConnectionTimeout;
  private final CamundaClientBuilderFactory camundaClientBuilderFactory;

  public CamundaProcessTestRemoteRuntime(final CamundaProcessTestRuntimeBuilder builder) {
    camundaClientBuilderFactory = builder.getConfiguredCamundaClientBuilderFactory();
    camundaMonitoringApiAddress = builder.getRemoteCamundaMonitoringApiAddress();
    connectorsRestApiAddress = builder.getRemoteConnectorsRestApiAddress();
    runtimeConnectionTimeout = builder.getRemoteRuntimeConnectionTimeout();

    final CamundaClientConfiguration clientConfiguration =
        getClientConfiguration(camundaClientBuilderFactory);
    camundaRestApiAddress = clientConfiguration.getRestAddress();
    camundaGrpcApiAddress = clientConfiguration.getGrpcAddress();

    if (builder.isMultiTenancyEnabled()) {
      LOGGER.warn(
          "Multitenancy detected, but not enabled. Activating multitenancy with a remote "
              + "Camunda runtime has no effect. This feature is only supported for self-managed "
              + "Camunda runtimes. Instead, make sure to configure the CamundaRuntimeConfiguration "
              + "with all parameters necessary to authenticate the CamundaClient against the remote "
              + "runtime.");
    }
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
    try {
      Awaitility.await()
          .atMost(runtimeConnectionTimeout)
          .pollInterval(Duration.ofSeconds(1))
          .pollDelay(Duration.ZERO)
          .ignoreExceptions()
          .untilAsserted(this::checkConnectionToRemoteRuntime);
    } catch (final ConditionTimeoutException timeout) {
      throw new RuntimeException(
          Optional.ofNullable(timeout.getCause())
              .map(Throwable::getMessage)
              .orElse("Failed to connect to remote Camunda runtime."));
    }
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
  public URI getCamundaMonitoringApiAddress() {
    return camundaMonitoringApiAddress;
  }

  @Override
  public URI getConnectorsRestApiAddress() {
    return connectorsRestApiAddress;
  }

  @Override
  public CamundaClientBuilderFactory getCamundaClientBuilderFactory() {
    return camundaClientBuilderFactory;
  }

  private void checkConnectionToRemoteRuntime() {
    final Topology topology = queryRemoteRuntimeHealth();

    final boolean isHealthy =
        topology.getBrokers().stream()
            .flatMap(brokerInfo -> brokerInfo.getPartitions().stream())
            .map(PartitionInfo::getHealth)
            .allMatch(PartitionBrokerHealth.HEALTHY::equals);
    final boolean hasAtLeastOnePartition =
        topology.getBrokers().stream()
            .anyMatch(brokerInfo -> !brokerInfo.getPartitions().isEmpty());

    if (isHealthy && hasAtLeastOnePartition) {
      LOGGER.info("Remote Camunda runtime connected. [version: {}]", topology.getGatewayVersion());
    } else if (!isHealthy) {
      final String errorMessage =
          String.format("Remote Camunda runtime is unhealthy. [topology: %s]", topology);
      throw new RemoteRuntimeUnhealthyException(errorMessage);
    } else {
      final String errorMessage =
          String.format(
              "Remote Camunda runtime has zero available partitions. Please check the remote runtime logs for errors. [topology: %s]",
              topology);
      throw new RemoteRuntimeHasNoAvailablePartitionsException(errorMessage);
    }
  }

  private Topology queryRemoteRuntimeHealth() {
    try (final CamundaClient camundaClient = getCamundaClientBuilderFactory().get().build()) {

      return camundaClient.newTopologyRequest().send().join();
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

  public static class RemoteRuntimeUnhealthyException extends IllegalStateException {

    public RemoteRuntimeUnhealthyException(final String message) {
      super(message);
    }
  }

  public static class RemoteRuntimeHasNoAvailablePartitionsException extends IllegalStateException {

    public RemoteRuntimeHasNoAvailablePartitionsException(final String message) {
      super(message);
    }
  }
}
