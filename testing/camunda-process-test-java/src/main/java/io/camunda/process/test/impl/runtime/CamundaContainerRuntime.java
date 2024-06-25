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

import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.containers.OperateContainer;
import io.camunda.process.test.impl.containers.TasklistContainer;
import io.camunda.process.test.impl.containers.ZeebeContainer;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class CamundaContainerRuntime implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaContainerRuntime.class);

  private static final String NETWORK_ALIAS_ZEEBE = "zeebe";
  private static final String NETWORK_ALIAS_ELASTICSEARCH = "elasticsearch";
  private static final String NETWORK_ALIAS_OPERATE = "operate";
  private static final String NETWORK_ALIAS_TASKLIST = "tasklist";

  private static final String ELASTICSEARCH_URL =
      "http://" + NETWORK_ALIAS_ELASTICSEARCH + ":" + ContainerRuntimePorts.ELASTICSEARCH_REST_API;

  private static final String ZEEBE_GRPC_API =
      NETWORK_ALIAS_ZEEBE + ":" + ContainerRuntimePorts.ZEEBE_GATEWAY_API;
  private static final String ZEEBE_REST_API =
      NETWORK_ALIAS_ZEEBE + ":" + ContainerRuntimePorts.ZEEBE_REST_API;

  private final ContainerFactory containerFactory;

  private final Network network;
  private final ZeebeContainer zeebeContainer;
  private final ElasticsearchContainer elasticsearchContainer;
  private final OperateContainer operateContainer;
  private final TasklistContainer tasklistContainer;

  CamundaContainerRuntime(
      final CamundaContainerRuntimeBuilder builder, final ContainerFactory containerFactory) {
    this.containerFactory = containerFactory;
    network = Network.newNetwork();

    elasticsearchContainer = createElasticsearchContainer(network, builder);
    zeebeContainer = createZeebeContainer(network, builder);
    operateContainer = createOperateContainer(network, builder);
    tasklistContainer = createTasklistContainer(network, builder);
  }

  private ElasticsearchContainer createElasticsearchContainer(
      final Network network, final CamundaContainerRuntimeBuilder builder) {
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

  private ZeebeContainer createZeebeContainer(
      final Network network, final CamundaContainerRuntimeBuilder builder) {
    final ZeebeContainer container =
        containerFactory
            .createZeebeContainer(
                builder.getZeebeDockerImageName(), builder.getZeebeDockerImageVersion())
            .withLogConsumer(createContainerLogger(builder.getZeebeLoggerName()))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_ZEEBE)
            .withElasticsearchExporter(ELASTICSEARCH_URL)
            .withEnv(builder.getZeebeEnvVars());

    builder.getZeebeExposedPorts().forEach(container::addExposedPort);

    return container;
  }

  private OperateContainer createOperateContainer(
      final Network network, final CamundaContainerRuntimeBuilder builder) {
    final OperateContainer container =
        containerFactory
            .createOperateContainer(
                ContainerRuntimeDefaults.OPERATE_DOCKER_IMAGE_NAME,
                builder.getOperateDockerImageVersion())
            .withLogConsumer(createContainerLogger(builder.getOperateLoggerName()))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_OPERATE)
            .withZeebeGrpcApi(ZEEBE_GRPC_API)
            .withElasticsearchUrl(ELASTICSEARCH_URL)
            .withEnv(builder.getOperateEnvVars());

    builder.getOperateExposedPorts().forEach(container::addExposedPort);

    return container;
  }

  private TasklistContainer createTasklistContainer(
      final Network network, final CamundaContainerRuntimeBuilder builder) {
    final TasklistContainer container =
        containerFactory
            .createTasklistContainer(
                ContainerRuntimeDefaults.TASKLIST_DOCKER_IMAGE_NAME,
                builder.getTasklistDockerImageVersion())
            .withLogConsumer(createContainerLogger(builder.getTasklistLoggerName()))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_TASKLIST)
            .withZeebeApi(ZEEBE_GRPC_API, ZEEBE_REST_API)
            .withElasticsearchUrl(ELASTICSEARCH_URL)
            .withEnv(builder.getTasklistEnvVars());

    builder.getTasklistExposedPorts().forEach(container::addExposedPort);

    return container;
  }

  public void start() {
    LOGGER.info("Starting Camunda container runtime");
    final Instant startTime = Instant.now();

    elasticsearchContainer.start();
    Stream.of(zeebeContainer, operateContainer, tasklistContainer)
        .parallel()
        .forEach(GenericContainer::start);

    final Instant endTime = Instant.now();
    final Duration startupTime = Duration.between(startTime, endTime);
    LOGGER.info("Camunda container runtime started in {}", startupTime);
  }

  public ZeebeContainer getZeebeContainer() {
    return zeebeContainer;
  }

  public ElasticsearchContainer getElasticsearchContainer() {
    return elasticsearchContainer;
  }

  public OperateContainer getOperateContainer() {
    return operateContainer;
  }

  public TasklistContainer getTasklistContainer() {
    return tasklistContainer;
  }

  @Override
  public void close() throws Exception {
    LOGGER.info("Stopping Camunda container runtime");
    final Instant startTime = Instant.now();

    Stream.of(zeebeContainer, operateContainer, tasklistContainer)
        .parallel()
        .forEach(GenericContainer::stop);

    elasticsearchContainer.stop();
    network.close();

    final Instant endTime = Instant.now();
    final Duration shutdownTime = Duration.between(startTime, endTime);
    LOGGER.info("Camunda container runtime stopped in {}", shutdownTime);
  }

  private static Slf4jLogConsumer createContainerLogger(final String name) {
    final Logger logger = LoggerFactory.getLogger(name);
    return new Slf4jLogConsumer(logger);
  }

  public static CamundaContainerRuntimeBuilder newBuilder() {
    return new CamundaContainerRuntimeBuilder();
  }

  public static CamundaContainerRuntime newDefaultRuntime() {
    return newBuilder().build();
  }
}
