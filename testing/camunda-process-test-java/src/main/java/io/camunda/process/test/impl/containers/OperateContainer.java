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
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode;
import org.testcontainers.utility.DockerImageName;

public class OperateContainer extends GenericContainer<OperateContainer> {

  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);
  private static final String OPERATE_READY_ENDPOINT = "/actuator/health/readiness";

  public OperateContainer(final DockerImageName dockerImageName) {
    super(dockerImageName);
    applyDefaultConfiguration();
  }

  private void applyDefaultConfiguration() {
    withNetwork(Network.SHARED)
        .waitingFor(newDefaultWaitStrategy())
        .withEnv(ContainerRuntimeEnvs.CAMUNDA_OPERATE_IMPORTER_READERBACKOFF, "1000")
        .addExposedPorts(ContainerRuntimePorts.OPERATE_REST_API);
  }

  public OperateContainer withZeebeGrpcApi(final String zeebeGrpcApi) {
    withEnv(ContainerRuntimeEnvs.OPERATE_ENV_ZEEBE_GATEWAYADDRESS, zeebeGrpcApi);
    return this;
  }

  public OperateContainer withElasticsearchUrl(final String elasticsearchUrl) {
    withEnv(ContainerRuntimeEnvs.OPERATE_ENV_ELASTICSEARCH_URL, elasticsearchUrl);
    withEnv(ContainerRuntimeEnvs.OPERATE_ENV_ZEEBEELASTICSEARCH_URL, elasticsearchUrl);
    return this;
  }

  public static HttpWaitStrategy newDefaultOperateReadyCheck() {
    return new HttpWaitStrategy()
        .forPath(OPERATE_READY_ENDPOINT)
        .forPort(ContainerRuntimePorts.OPERATE_REST_API)
        .forStatusCodeMatching(status -> status >= 200 && status < 300)
        .withReadTimeout(Duration.ofSeconds(10));
  }

  private WaitAllStrategy newDefaultWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(newDefaultOperateReadyCheck())
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
  }

  public int getRestApiPort() {
    return getMappedPort(ContainerRuntimePorts.OPERATE_REST_API);
  }
}
