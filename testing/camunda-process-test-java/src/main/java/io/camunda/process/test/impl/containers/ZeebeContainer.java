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
package io.camunda.process.test.impl.containers;

import io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs;
import io.camunda.process.test.impl.runtime.ContainerRuntimePorts;
import java.net.URI;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode;
import org.testcontainers.utility.DockerImageName;

public class ZeebeContainer extends GenericContainer<ZeebeContainer> {

  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);
  private static final String ZEEBE_READY_ENDPOINT = "/ready";

  private static final String ZEEBE_ELASTICSEARCH_EXPORTER_CLASSNAME =
      "io.camunda.zeebe.exporter.ElasticsearchExporter";

  public ZeebeContainer(final DockerImageName dockerImageName) {
    super(dockerImageName);
    applyDefaultConfiguration();
  }

  private void applyDefaultConfiguration() {
    withNetwork(Network.SHARED)
        .waitingFor(newDefaultWaitStrategy())
        .withEnv(ContainerRuntimeEnvs.ZEEBE_ENV_CLOCK_CONTROLLED, "true")
        .addExposedPorts(
            ContainerRuntimePorts.ZEEBE_GATEWAY_API,
            ContainerRuntimePorts.ZEEBE_COMMAND_API,
            ContainerRuntimePorts.ZEEBE_INTERNAL_API,
            ContainerRuntimePorts.ZEEBE_MONITORING_API,
            ContainerRuntimePorts.ZEEBE_REST_API);
  }

  public ZeebeContainer withElasticsearchExporter(final String url) {
    withEnv(
        ContainerRuntimeEnvs.ZEEBE_ENV_ELASTICSEARCH_CLASSNAME,
        ZEEBE_ELASTICSEARCH_EXPORTER_CLASSNAME);
    withEnv(ContainerRuntimeEnvs.ZEEBE_ENV_ELASTICSEARCH_ARGS_URL, url);
    withEnv(ContainerRuntimeEnvs.ZEEBE_ENV_ELASTICSEARCH_ARGS_BULK_SIZE, "1");
    return this;
  }

  public static HttpWaitStrategy newDefaultBrokerReadyCheck() {
    return new HttpWaitStrategy()
        .forPath(ZEEBE_READY_ENDPOINT)
        .forPort(ContainerRuntimePorts.ZEEBE_MONITORING_API)
        .forStatusCodeMatching(status -> status >= 200 && status < 300)
        .withReadTimeout(Duration.ofSeconds(10));
  }

  private WaitAllStrategy newDefaultWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(newDefaultBrokerReadyCheck())
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
  }

  public int getGrpcApiPort() {
    return getMappedPort(ContainerRuntimePorts.ZEEBE_GATEWAY_API);
  }

  public int getRestApiPort() {
    return getMappedPort(ContainerRuntimePorts.ZEEBE_REST_API);
  }

  public URI getGrpcApiAddress() {
    return toUriWithPort(getGrpcApiPort());
  }

  public URI getRestApiAddress() {
    return toUriWithPort(getRestApiPort());
  }

  private URI toUriWithPort(final int port) {
    return URI.create("http://" + getHost() + ":" + port);
  }
}
