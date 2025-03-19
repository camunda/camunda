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
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode;
import org.testcontainers.utility.DockerImageName;

public class CamundaContainer extends GenericContainer<CamundaContainer> {

  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);
  private static final String READY_ENDPOINT = "/ready";

  private static final String ACTIVE_SPRING_PROFILES = "broker,consolidated-auth";
  private static final String LOG_APPENDER_STACKDRIVER = "Stackdriver";

  private static final String CAMUNDA_EXPORTER_CLASSNAME = "io.camunda.exporter.CamundaExporter";
  private static final String CAMUNDA_EXPORTER_BULK_SIZE = "1";

  public CamundaContainer(final DockerImageName dockerImageName) {
    super(dockerImageName);
    applyDefaultConfiguration();
  }

  private void applyDefaultConfiguration() {
    withNetwork(Network.SHARED)
        .waitingFor(newDefaultWaitStrategy())
        .withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_SPRING_PROFILES_ACTIVE, ACTIVE_SPRING_PROFILES)
        .withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_ZEEBE_CLOCK_CONTROLLED, "true")
        .withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_ZEEBE_LOG_APPENDER, LOG_APPENDER_STACKDRIVER)
        .withRdbms()
        .addExposedPorts(
            ContainerRuntimePorts.CAMUNDA_GATEWAY_API,
            ContainerRuntimePorts.CAMUNDA_COMMAND_API,
            ContainerRuntimePorts.CAMUNDA_INTERNAL_API,
            ContainerRuntimePorts.CAMUNDA_MONITORING_API,
            ContainerRuntimePorts.CAMUNDA_REST_API);
  }

  public CamundaContainer withRdbms() {
    withEnv("CAMUNDA_DATABASE_TYPE", "rdbms")
        .withEnv(
            "SPRING_DATASOURCE_URL",
            "jdbc:h2:mem:testdb+" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        .withEnv("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver")
        .withEnv("SPRING_DATASOURCE_USERNAME", "sa")
        .withEnv("SPRING_DATASOURCE_PASSWORD", "")
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME", "io.camunda.exporter.rdbms.RdbmsExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL", "PT0S")
        .withEnv("ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_DEFAULT_HISTORY_TTL", "PT2S")
        .withEnv("ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MIN_HISTORY_CLEANUP_INTERVAL", "PT2S")
        .withEnv("ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MAX_HISTORY_CLEANUP_INTERVAL", "PT5S")
        .withEnv("LOGGING_LEVEL_IO_CAMUNDA_DB_RDBMS", "DEBUG")
        .withEnv("LOGGING_LEVEL_ORG_MYBATIS", "DEBUG");

    return this;
  }

  public CamundaContainer withElasticsearchUrl(final String url) {
    withEnv(
        ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_EXPORTER_CLASSNAME, CAMUNDA_EXPORTER_CLASSNAME);
    withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_EXPORTER_ARGS_CONNECT_URL, url);
    withEnv(
        ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_EXPORTER_ARGS_BULK_SIZE,
        CAMUNDA_EXPORTER_BULK_SIZE);

    withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_OPERATE_ELASTICSEARCH_URL, url);
    withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_OPERATE_ZEEBEELASTICSEARCH_URL, url);

    withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_TASKLIST_ELASTICSEARCH_URL, url);
    withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_TASKLIST_ZEEBEELASTICSEARCH_URL, url);

    withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_DATABASE_URL, url);
    return this;
  }

  public static HttpWaitStrategy newDefaultBrokerReadyCheck() {
    return new HttpWaitStrategy()
        .forPath(READY_ENDPOINT)
        .forPort(ContainerRuntimePorts.CAMUNDA_MONITORING_API)
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
    return getMappedPort(ContainerRuntimePorts.CAMUNDA_GATEWAY_API);
  }

  public int getRestApiPort() {
    return getMappedPort(ContainerRuntimePorts.CAMUNDA_REST_API);
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

  public URI getMonitoringApiAddress() {
    return toUriWithPort(getMonitoringApiPort());
  }

  public int getMonitoringApiPort() {
    return getMappedPort(ContainerRuntimePorts.CAMUNDA_MONITORING_API);
  }
}
