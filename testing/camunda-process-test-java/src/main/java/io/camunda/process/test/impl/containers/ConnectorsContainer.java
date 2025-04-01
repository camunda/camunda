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

import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CONNECTORS_ENV_CAMUNDA_CLIENT_GRPC_ADDRESS;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CONNECTORS_ENV_CAMUNDA_CLIENT_REST_ADDRESS;

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

public class ConnectorsContainer extends GenericContainer<ConnectorsContainer> {

  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);
  private static final String CONNECTORS_READY_ENDPOINT = "/actuator/health/readiness";

  private static final String LOG_APPENDER_STACKDRIVER = "stackdriver";

  public ConnectorsContainer(final DockerImageName dockerImageName) {
    super(dockerImageName);
    applyDefaultConfiguration();
  }

  private void applyDefaultConfiguration() {
    withNetwork(Network.SHARED)
        .waitingFor(newDefaultWaitStrategy())
        .withEnv("management.endpoints.web.exposure.include", "health")
        .withEnv("management.endpoint.health.probes.enabled", "true")
        .withEnv(ContainerRuntimeEnvs.CONNECTORS_ENV_LOG_APPENDER, LOG_APPENDER_STACKDRIVER)
        .addExposedPorts(ContainerRuntimePorts.CONNECTORS_REST_API);
  }

  public ConnectorsContainer withZeebeGrpcApi(final String zeebeGrpcApi) {
    withEnv(CONNECTORS_ENV_CAMUNDA_CLIENT_GRPC_ADDRESS, zeebeGrpcApi);
    return this;
  }

  public ConnectorsContainer withOperateApi(final String operateRestApi) {
    withEnv(CONNECTORS_ENV_CAMUNDA_CLIENT_REST_ADDRESS, operateRestApi);
    return this;
  }

  public static HttpWaitStrategy newDefaultConnectorsReadyCheck() {
    return new HttpWaitStrategy()
        .forPath(CONNECTORS_READY_ENDPOINT)
        .forPort(ContainerRuntimePorts.CONNECTORS_REST_API)
        .forStatusCodeMatching(status -> status >= 200 && status < 300)
        .withReadTimeout(Duration.ofSeconds(10));
  }

  private WaitAllStrategy newDefaultWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(newDefaultConnectorsReadyCheck())
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
  }

  public int getRestApiPort() {
    return getMappedPort(ContainerRuntimePorts.CONNECTORS_REST_API);
  }

  public URI getRestApiAddress() {
    return URI.create("http://" + getHost() + ":" + getRestApiPort());
  }
}
