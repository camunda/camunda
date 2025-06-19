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

import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.DATABASE_PASSWORD;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.DATABASE_TYPE;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.DATABASE_USERNAME;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.LOGGING_LEVEL_IO_CAMUNDA_DB_RDBMS;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.LOGGING_LEVEL_ORG_MYBATIS;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_DEFAULT_HISTORY_TTL;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MAX_HISTORY_CLEANUP_INTERVAL;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MIN_HISTORY_CLEANUP_INTERVAL;
import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.databaseUrL;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.*;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaProcessTestEmbeddedRuntime implements CamundaProcessTestRuntime {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaProcessTestEmbeddedRuntime.class);

  private final TestCamundaApplication application;

  public CamundaProcessTestEmbeddedRuntime(final CamundaProcessTestRuntimeBuilder runtimeBuilder) {
    application = new TestCamundaApplication();
    application
        .withProperty("zeebe.clock.controlled", "true")
        .withProperty("camunda.database.type", DATABASE_TYPE)
        .withProperty("camunda.database.url", databaseUrL(UUID.randomUUID()))
        .withProperty("camunda.database.username", DATABASE_USERNAME)
        .withProperty("camunda.database.password", DATABASE_PASSWORD)
        .withProperty("logging.level.io.camunda.db.rdbms", LOGGING_LEVEL_IO_CAMUNDA_DB_RDBMS)
        .withProperty("logging.level.org.mybatis", LOGGING_LEVEL_ORG_MYBATIS)
        .withProperty(
            "zeebe.broker.exporters.rdbms.args.flushInterval",
            ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_FLUSH_INTERVAL)
        .withProperty(
            "zeebe.broker.exporters.rdbms.args.defaultHistoryTTL",
            ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_DEFAULT_HISTORY_TTL)
        .withProperty(
            "zeebe.broker.exporters.rdbms.args.minHistoryCleanupInterval",
            ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MIN_HISTORY_CLEANUP_INTERVAL)
        .withProperty(
            "zeebe.broker.exporters.rdbms.args.maxHistoryCleanupInterval",
            ZEEBE_BROKER_EXPORTERS_RDBMS_ARGS_MAX_HISTORY_CLEANUP_INTERVAL)
        .withExporter("rdbms", cfg -> cfg.setClassName("-"));
  }

  @Override
  public void start() {

    LOGGER.info("Starting Camunda 8 Orchestration Cluster...");

    application.start();
    checkConnectionToRemoteRuntime();
  }

  @Override
  public URI getCamundaRestApiAddress() {
    return application.restAddress();
  }

  @Override
  public URI getCamundaGrpcApiAddress() {
    return application.grpcAddress();
  }

  @Override
  public URI getCamundaMonitoringApiAddress() {
    return application.monitoringUri();
  }

  @Override
  public URI getConnectorsRestApiAddress() {
    return null;
  }

  @Override
  public CamundaClientBuilderFactory getCamundaClientBuilderFactory() {
    return () ->
        CamundaClient.newClientBuilder()
            .restAddress(getCamundaRestApiAddress())
            .grpcAddress(getCamundaGrpcApiAddress())
            .usePlaintext();
  }

  @Override
  public void close() throws Exception {
    LOGGER.info("Closing Camunda 8 Orchestration Cluster...");
    application.close();
  }

  private void checkConnectionToRemoteRuntime() {
    try (final CamundaClient camundaClient = getCamundaClientBuilderFactory().get().build()) {

      boolean isHealthy = false;

      while (!isHealthy) {
        final Topology topology = camundaClient.newTopologyRequest().send().join();
        isHealthy =
            !topology.getBrokers().get(0).getPartitions().isEmpty()
                && topology.getBrokers().stream()
                    .flatMap(brokerInfo -> brokerInfo.getPartitions().stream())
                    .map(PartitionInfo::getHealth)
                    .allMatch(PartitionBrokerHealth.HEALTHY::equals);

        if (isHealthy) {
          LOGGER.info(
              "Camunda 8 Orchestration up and running. [version: {}]",
              topology.getGatewayVersion());
        }
      }

    } catch (final Exception e) {
      throw new RuntimeException("Failed to connect to remote Camunda runtime.", e);
    }
  }
}
