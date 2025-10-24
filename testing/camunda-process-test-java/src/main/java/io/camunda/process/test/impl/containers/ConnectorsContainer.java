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

  public ConnectorsContainer withMultiTenancy() {
    withEnv(
            ContainerRuntimeEnvs.CONNECTORS_ENV_CAMUNDA_CLIENT_AUTH_METHOD,
            MultiTenancyConfiguration.CAMUNDA_CLIENT_AUTH_METHOD)
        .withEnv(
            ContainerRuntimeEnvs.CONNECTORS_ENV_CAMUNDA_CLIENT_AUTH_USERNAME,
            MultiTenancyConfiguration.CAMUNDA_CLIENT_AUTH_USERNAME)
        .withEnv(
            ContainerRuntimeEnvs.CONNECTORS_ENV_CAMUNDA_CLIENT_AUTH_PASSWORD,
            MultiTenancyConfiguration.CAMUNDA_CLIENT_AUTH_PASSWORD)
        .withEnv(
            ContainerRuntimeEnvs.CONNECTORS_ENV_CAMUNDA_CLIENT_TENANTID,
            MultiTenancyConfiguration.CAMUNDA_CLIENT_TENANTID)
        .withEnv(
            ContainerRuntimeEnvs.CONNECTORS_ENV_CAMUNDA_CLIENT_WORKER_DEFAULTS_TENANTIDS,
            MultiTenancyConfiguration.CAMUNDA_CLIENT_WORKER_DEFAULTS_TENANTIDS)
        /*
         * Basic Auth has a very limited request throughput, requiring tests with multitenancy to have an increased
         * polling interval so as to not cause timeout exceptions.
         */
        .withEnv(
            ContainerRuntimeEnvs.CONNECTORS_ENV_POLLING_INTERVAL,
            MultiTenancyConfiguration.POLLING_INTERVAL)
        .waitingFor(newBasicAuthWaitStrategy());

    return this;
  }

  public static HttpWaitStrategy newBasicAuthConnectorsReadyCheck() {
    return newDefaultConnectorsReadyCheck()
        .withBasicCredentials(
            MultiTenancyConfiguration.CAMUNDA_CLIENT_AUTH_USERNAME,
            MultiTenancyConfiguration.CAMUNDA_CLIENT_AUTH_PASSWORD);
  }

  public static HttpWaitStrategy newDefaultConnectorsReadyCheck() {
    return new HttpWaitStrategy()
        .forPath(CONNECTORS_READY_ENDPOINT)
        .forPort(ContainerRuntimePorts.CONNECTORS_REST_API)
        .forStatusCodeMatching(status -> status >= 200 && status < 300)
        .withReadTimeout(Duration.ofSeconds(10));
  }

  private WaitAllStrategy newBasicAuthWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(newBasicAuthConnectorsReadyCheck())
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
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

  /**
   * Contains all configuration values required for running a self-managed, multitenancy-enabled
   * Camunda connectors runtime.
   */
  public static class MultiTenancyConfiguration {
    private static final String CAMUNDA_CLIENT_AUTH_METHOD = "basic";
    private static final String CAMUNDA_CLIENT_AUTH_USERNAME =
        CamundaContainer.MultiTenancyConfiguration.MULTITENANCY_USER_USERNAME;
    private static final String CAMUNDA_CLIENT_AUTH_PASSWORD =
        CamundaContainer.MultiTenancyConfiguration.MULTITENANCY_USER_PASSWORD;
    private static final String CAMUNDA_CLIENT_TENANTID = "<default>";
    private static final String CAMUNDA_CLIENT_WORKER_DEFAULTS_TENANTIDS = "<default>";
    private static final String POLLING_INTERVAL = "1000";
  }
}
