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

import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.*;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.DATABASE_TYPE;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_DATABASE_URL;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_DATABASE_PASSWORD;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_DATABASE_TYPE;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_DATABASE_USERNAME;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_LOGGING_LEVEL_IO_CAMUNDA_DB_RDBMS;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_LOGGING_LEVEL_ORG_MYBATIS;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_DEFAULT_HISTORY_TTL;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MAX_HISTORY_CLEANUP_INTERVAL;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MIN_HISTORY_CLEANUP_INTERVAL;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.UNIFIED_CONFIGURATION_CAMUNDA_ENV_DATABASE_TYPE;

import io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs;
import io.camunda.process.test.impl.runtime.ContainerRuntimePorts;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode;
import org.testcontainers.utility.DockerImageName;

public class CamundaContainer extends GenericContainer<CamundaContainer> {

  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);
  private static final Duration DEFAULT_READINESS_TIMEOUT = Duration.ofSeconds(10);

  private static final String READY_ENDPOINT = "/actuator/health/status";
  private static final String TOPOLOGY_ENDPOINT = "/v2/topology";

  private static final String ACTIVE_SPRING_PROFILES = "broker,consolidated-auth,security";
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
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTED_API,
            "true")
        .withEnv(ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED, "false")
        .withH2()
        .addExposedPorts(
            ContainerRuntimePorts.CAMUNDA_GATEWAY_API,
            ContainerRuntimePorts.CAMUNDA_COMMAND_API,
            ContainerRuntimePorts.CAMUNDA_INTERNAL_API,
            ContainerRuntimePorts.CAMUNDA_MONITORING_API,
            ContainerRuntimePorts.CAMUNDA_REST_API);
  }

  public CamundaContainer withMultitenancy() {
    withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_MULTITENANCY_ENABLED,
            MultitenancyConfiguration.MULTITENANCY_ENABLED)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_ZEEBE_GATEWAY_SECURITY_AUTHENTICATION_MODE,
            MultitenancyConfiguration.ZEEBE_GATEWAY_SECURITY_AUTHENTICATION_MODE)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_SECURITY_MULTITENANCY_CHECKS_ENABLED,
            MultitenancyConfiguration.CAMUNDA_SECURITY_MULTITENANCY_CHECKS_ENABLED)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_SECURITY_MULTITENANCY_API_ENABLED,
            MultitenancyConfiguration.CAMUNDA_SECURITY_MULTITENANCY_API_ENABLED)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED,
            MultitenancyConfiguration.CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTED_API,
            MultitenancyConfiguration.CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_MULTITENANCY_USER_NAME,
            MultitenancyConfiguration.MULTITENANCY_USER_NAME)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_MULTITENANCY_USER_EMAIL,
            MultitenancyConfiguration.MULTITENANCY_USER_EMAIL)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_MULTITENANCY_USER_USERNAME,
            MultitenancyConfiguration.MULTITENANCY_USER_USERNAME)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_MULTITENANCY_USER_PASSWORD,
            MultitenancyConfiguration.MULTITENANCY_USER_PASSWORD)
        .withEnv(
            ContainerRuntimeEnvs.CAMUNDA_ENV_MULTITENANCY_USER_ADMIN_ROLE,
            MultitenancyConfiguration.MULTITENANCY_USER_ADMIN_ROLE)
        .waitingFor(newBasicAuthWaitStrategy());

    return this;
  }

  public CamundaContainer withH2() {
    withEnv(CAMUNDA_ENV_DATABASE_TYPE, DATABASE_TYPE)
        .withEnv(UNIFIED_CONFIGURATION_CAMUNDA_ENV_DATABASE_TYPE, DATABASE_TYPE)
        .withEnv(CAMUNDA_ENV_CAMUNDA_DATABASE_URL, databaseUrL(UUID.randomUUID()))
        .withEnv(CAMUNDA_ENV_DATABASE_USERNAME, DATABASE_USERNAME)
        .withEnv(CAMUNDA_ENV_DATABASE_PASSWORD, DATABASE_PASSWORD)
        .withEnv(
            CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME,
            ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME)
        .withEnv(
            CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL,
            ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL)
        .withEnv(
            CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_DEFAULT_HISTORY_TTL,
            ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_DEFAULT_HISTORY_TTL)
        .withEnv(
            CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MIN_HISTORY_CLEANUP_INTERVAL,
            ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MIN_HISTORY_CLEANUP_INTERVAL)
        .withEnv(
            CAMUNDA_ENV_ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MAX_HISTORY_CLEANUP_INTERVAL,
            ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MAX_HISTORY_CLEANUP_INTERVAL)
        .withEnv(CAMUNDA_ENV_LOGGING_LEVEL_IO_CAMUNDA_DB_RDBMS, LOGGING_LEVEL_IO_CAMUNDA_DB_RDBMS)
        .withEnv(CAMUNDA_ENV_LOGGING_LEVEL_ORG_MYBATIS, LOGGING_LEVEL_ORG_MYBATIS);

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

    withEnv(CAMUNDA_ENV_CAMUNDA_DATABASE_URL, url);
    return this;
  }

  public static HttpWaitStrategy newBasicAuthBrokerReadyCheck() {
    return newDefaultBrokerReadyCheck()
        .withBasicCredentials(
            MultitenancyConfiguration.MULTITENANCY_USER_USERNAME,
            MultitenancyConfiguration.MULTITENANCY_USER_PASSWORD);
  }

  public static HttpWaitStrategy newDefaultBrokerReadyCheck() {
    return new HttpWaitStrategy()
        .forPath(READY_ENDPOINT)
        .forPort(ContainerRuntimePorts.CAMUNDA_MONITORING_API)
        .forStatusCodeMatching(status -> status >= 200 && status < 300)
        .withReadTimeout(DEFAULT_READINESS_TIMEOUT);
  }

  public static HttpWaitStrategy newBasicAuthTopologyReadyCheck() {
    return newDefaultTopologyReadyCheck()
        .withBasicCredentials(
            MultitenancyConfiguration.MULTITENANCY_USER_USERNAME,
            MultitenancyConfiguration.MULTITENANCY_USER_PASSWORD);
  }

  public static HttpWaitStrategy newDefaultTopologyReadyCheck() {
    return new HttpWaitStrategy()
        .forPath(TOPOLOGY_ENDPOINT)
        .forPort(ContainerRuntimePorts.CAMUNDA_REST_API)
        .forStatusCodeMatching(status -> status >= 200 && status < 300)
        .forResponsePredicate(CamundaContainer::isPartitionReady)
        .withReadTimeout(DEFAULT_READINESS_TIMEOUT);
  }

  private static boolean isPartitionReady(final String response) {
    return response.matches(".*\"partitionId\"\\s*:\\s*1.*")
        && response.matches(".*\"role\"\\s*:\\s*\"leader\".*")
        && response.matches(".*\"health\"\\s*:\\s*\"healthy\".*");
  }

  private WaitAllStrategy newDefaultWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(newDefaultBrokerReadyCheck())
        .withStrategy(newDefaultTopologyReadyCheck())
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
  }

  private WaitAllStrategy newBasicAuthWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(newBasicAuthBrokerReadyCheck())
        .withStrategy(newBasicAuthTopologyReadyCheck())
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

  public static class H2Configuration {
    public static final String DATABASE_TYPE = "rdbms";
    public static final String DATABASE_USERNAME = "sa";
    public static final String DATABASE_PASSWORD = "";
    public static final String ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME =
        "io.camunda.exporter.rdbms.RdbmsExporter";
    public static final String ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL = "PT0S";
    public static final String ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_DEFAULT_HISTORY_TTL = "PT2S";
    public static final String ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MIN_HISTORY_CLEANUP_INTERVAL =
        "PT2S";
    public static final String ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MAX_HISTORY_CLEANUP_INTERVAL =
        "PT5S";
    public static final String LOGGING_LEVEL_IO_CAMUNDA_DB_RDBMS = "DEBUG";
    public static final String LOGGING_LEVEL_ORG_MYBATIS = "DEBUG";

    public static String databaseUrL(final UUID uuid) {
      return "jdbc:h2:mem:cpt+" + uuid + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
    }
  }

  /**
   * Contains all configuration values required for running a self-managed, multitenancy-enabled
   * Camunda test runtime.
   */
  public static class MultitenancyConfiguration {
    public static final String MULTITENANCY_ENABLED = "true";
    public static final String ZEEBE_GATEWAY_SECURITY_AUTHENTICATION_MODE = "basic";
    public static final String CAMUNDA_SECURITY_MULTITENANCY_CHECKS_ENABLED = "true";
    public static final String CAMUNDA_SECURITY_MULTITENANCY_API_ENABLED = "true";
    public static final String CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI = "false";
    public static final String CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED = "true";
    /*
     * Although the {@see io.camunda.security.configuration.InitializationConfiguration} creates a
     * demo user by default, the user is explicity defined here in case the demo user, for whatever
     * reason, should change in the future.
     */
    public static final String MULTITENANCY_USER_NAME = "demo";
    public static final String MULTITENANCY_USER_EMAIL = "demo@example.com";
    public static final String MULTITENANCY_USER_USERNAME = "demo";
    public static final String MULTITENANCY_USER_PASSWORD = "demo";
    public static final String MULTITENANCY_USER_ADMIN_ROLE = "demo";

    public static String getBasicAuthCredentials() {
      final String basicAuthString =
          String.format("%s:%s", MULTITENANCY_USER_USERNAME, MULTITENANCY_USER_PASSWORD);

      return new String(
          Base64.getEncoder().encode(basicAuthString.getBytes(StandardCharsets.UTF_8)));
    }
  }
}
