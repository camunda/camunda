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

public class TasklistContainer extends GenericContainer<TasklistContainer> {

  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);
  private static final String TASKLIST_READY_ENDPOINT = "/actuator/health/readiness";

  public TasklistContainer(final DockerImageName dockerImageName) {
    super(dockerImageName);
    applyDefaultConfiguration();
  }

  private void applyDefaultConfiguration() {
    withNetwork(Network.SHARED)
        .waitingFor(newDefaultWaitStrategy())
        .withEnv(ContainerRuntimeEnvs.TASKLIST_CSRF_PREVENTION_ENABLED, "false")
        .addExposedPorts(ContainerRuntimePorts.TASKLIST_REST_API);
  }

  public TasklistContainer withZeebeApi(final String zeebeGrpcApi, final String zeebeRestApi) {
    withEnv(ContainerRuntimeEnvs.TASKLIST_ENV_ZEEBE_GATEWAYADDRESS, zeebeGrpcApi);
    withEnv(ContainerRuntimeEnvs.TASKLIST_ENV_ZEEBE_RESTADDRESS, zeebeRestApi);
    return this;
  }

  public TasklistContainer withElasticsearchUrl(final String elasticsearchUrl) {
    withEnv(ContainerRuntimeEnvs.TASKLIST_ENV_ELASTICSEARCH_URL, elasticsearchUrl);
    withEnv(ContainerRuntimeEnvs.TASKLIST_ENV_ZEEBEELASTICSEARCH_URL, elasticsearchUrl);
    return this;
  }

  public static HttpWaitStrategy newDefaultTasklistReadyCheck() {
    return new HttpWaitStrategy()
        .forPath(TASKLIST_READY_ENDPOINT)
        .forPort(ContainerRuntimePorts.TASKLIST_REST_API)
        .forStatusCodeMatching(status -> status >= 200 && status < 300)
        .withReadTimeout(Duration.ofSeconds(10));
  }

  private WaitAllStrategy newDefaultWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(newDefaultTasklistReadyCheck())
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
  }

  public int getRestApiPort() {
    return getMappedPort(ContainerRuntimePorts.TASKLIST_REST_API);
  }
}
