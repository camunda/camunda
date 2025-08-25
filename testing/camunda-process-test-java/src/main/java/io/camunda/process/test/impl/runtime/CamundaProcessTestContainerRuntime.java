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
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ConnectorsContainer;
import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.runtime.logging.CamundaLogEntry;
import io.camunda.process.test.impl.runtime.logging.ConnectorsLogEntry;
import io.camunda.process.test.impl.runtime.logging.LogEntry;
import io.camunda.process.test.impl.runtime.logging.Slf4jJsonLogConsumer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class CamundaProcessTestContainerRuntime
    implements AutoCloseable, CamundaProcessTestRuntime {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaProcessTestContainerRuntime.class);

  private static final String NETWORK_ALIAS_CAMUNDA = "camunda";
  private static final String NETWORK_ALIAS_ELASTICSEARCH = "elasticsearch";
  private static final String NETWORK_ALIAS_CONNECTORS = "connectors";

  private static final String ELASTICSEARCH_URL =
      "http://" + NETWORK_ALIAS_ELASTICSEARCH + ":" + ContainerRuntimePorts.ELASTICSEARCH_REST_API;

  private static final String CAMUNDA_GRPC_API =
      "http://" + NETWORK_ALIAS_CAMUNDA + ":" + ContainerRuntimePorts.CAMUNDA_GATEWAY_API;
  private static final String CAMUNDA_REST_API =
      "http://" + NETWORK_ALIAS_CAMUNDA + ":" + ContainerRuntimePorts.CAMUNDA_REST_API;

  private static final URI DISABLED_CONNECTORS_ADDRESS =
      URI.create(
          "http://"
              + NETWORK_ALIAS_CONNECTORS
              + ":"
              + ContainerRuntimePorts.CONNECTORS_REST_API
              + "/disabled");

  private final ContainerFactory containerFactory;

  private final Network network;
  private final CamundaContainer camundaContainer;
  private final ConnectorsContainer connectorsContainer;

  private final boolean connectorsEnabled;
  private final Duration camundaClientRequestTimeout;

  CamundaProcessTestContainerRuntime(
      final CamundaProcessTestRuntimeBuilder builder, final ContainerFactory containerFactory) {
    this.containerFactory = containerFactory;

    network = Network.newNetwork();
    camundaContainer = createCamundaContainer(network, builder);
    connectorsContainer = createConnectorsContainer(network, builder);

    connectorsEnabled = builder.isConnectorsEnabled();
    camundaClientRequestTimeout = builder.getCamundaClientRequestTimeout();
  }

  /*
   * The ES container has been removed in favor of an H2 database solution. However, the secondary
   * storage implementation is going to become configurable in the near future so we're keeping
   * this unused method in the code for now.
   * {@see https://github.com/camunda/camunda/issues/29854}
   */
  private ElasticsearchContainer createElasticsearchContainer(
      final Network network, final CamundaProcessTestRuntimeBuilder builder) {
    final ElasticsearchContainer container =
        containerFactory
            .createElasticsearchContainer(
                builder.getElasticsearchDockerImageName(),
                builder.getElasticsearchDockerImageVersion())
            .withLogConsumer(createContainerLogger(builder.getElasticsearchLoggerName()))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_ELASTICSEARCH)
            .withEnv(ContainerRuntimeEnvs.ELASTICSEARCH_ENV_XPACK_SECURITY_ENABLED, "false")
            .withEnv(builder.getElasticsearchEnvVars());

    builder.getElasticsearchExposedPorts().forEach(container::addExposedPort);

    return container;
  }

  private CamundaContainer createCamundaContainer(
      final Network network, final CamundaProcessTestRuntimeBuilder builder) {
    final CamundaContainer container =
        containerFactory
            .createCamundaContainer(
                builder.getCamundaDockerImageName(), builder.getCamundaDockerImageVersion())
            .withLogConsumer(
                createContainerJsonLogger(builder.getCamundaLoggerName(), CamundaLogEntry.class))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_CAMUNDA)
            .withEnv(builder.getCamundaEnvVars())
            .withAccessToHost(true);

    builder.getCamundaExposedPorts().forEach(container::addExposedPort);

    return container;
  }

  private ConnectorsContainer createConnectorsContainer(
      final Network network, final CamundaProcessTestRuntimeBuilder builder) {
    final ConnectorsContainer container =
        containerFactory
            .createConnectorsContainer(
                builder.getConnectorsDockerImageName(), builder.getConnectorsDockerImageVersion())
            .withLogConsumer(
                createContainerJsonLogger(
                    builder.getConnectorsLoggerName(), ConnectorsLogEntry.class))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_CONNECTORS)
            .withZeebeGrpcApi(CAMUNDA_GRPC_API)
            .withOperateApi(CAMUNDA_REST_API)
            .withEnv(builder.getConnectorsSecrets())
            .withEnv(builder.getConnectorsEnvVars())
            .withAccessToHost(true);

    builder.getConnectorsExposedPorts().forEach(container::addExposedPort);

    return container;
  }

  @Override
  public void start() {
    final List<GenericContainer<?>> containers = new ArrayList<>();
    containers.add(camundaContainer);
    if (connectorsEnabled) {
      containers.add(connectorsContainer);
    }

    LOGGER.info(
        "Starting Camunda container runtime [{}]",
        containers.stream()
            .map(GenericContainer::getDockerImageName)
            .collect(Collectors.joining(", ")));
    final Instant startTime = Instant.now();

    containers.forEach(GenericContainer::start);

    final Instant endTime = Instant.now();
    final Duration startupTime = Duration.between(startTime, endTime);
    LOGGER.info("Camunda container runtime started in {}", startupTime);
  }

  @Override
  public URI getCamundaRestApiAddress() {
    return getCamundaContainer().getRestApiAddress();
  }

  @Override
  public URI getCamundaGrpcApiAddress() {
    return getCamundaContainer().getGrpcApiAddress();
  }

  @Override
  public URI getCamundaMonitoringApiAddress() {
    return getCamundaContainer().getMonitoringApiAddress();
  }

  @Override
  public URI getConnectorsRestApiAddress() {
    if (connectorsEnabled) {
      return getConnectorsContainer().getRestApiAddress();
    } else {
      return DISABLED_CONNECTORS_ADDRESS;
    }
  }

  @Override
  public CamundaClientBuilderFactory getCamundaClientBuilderFactory() {
    return () ->
        CamundaClient.newClientBuilder()
            .restAddress(getCamundaRestApiAddress())
            .grpcAddress(getCamundaGrpcApiAddress())
            .usePlaintext()
            .defaultRequestTimeout(camundaClientRequestTimeout);
  }

  public CamundaContainer getCamundaContainer() {
    return camundaContainer;
  }

  public ConnectorsContainer getConnectorsContainer() {
    return connectorsContainer;
  }

  @Override
  public void close() throws Exception {
    LOGGER.info("Stopping Camunda container runtime");
    final Instant startTime = Instant.now();

    if (connectorsEnabled) {
      connectorsContainer.stop();
    }

    camundaContainer.stop();
    network.close();

    final Instant endTime = Instant.now();
    final Duration shutdownTime = Duration.between(startTime, endTime);
    LOGGER.info("Camunda container runtime stopped in {}", shutdownTime);
  }

  private static Slf4jLogConsumer createContainerLogger(final String name) {
    final Logger logger = LoggerFactory.getLogger(name);
    return new Slf4jLogConsumer(logger, true);
  }

  private static <T extends LogEntry> Slf4jJsonLogConsumer createContainerJsonLogger(
      final String name, final Class<T> logEntryType) {
    final Logger logger = LoggerFactory.getLogger(name);
    return new Slf4jJsonLogConsumer(logger, logEntryType);
  }

  public static CamundaProcessTestRuntimeBuilder newBuilder() {
    return new CamundaProcessTestRuntimeBuilder();
  }

  public static CamundaProcessTestRuntime newDefaultRuntime() {
    return newBuilder().build();
  }
}
