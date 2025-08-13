/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime;

import static io.camunda.process.test.impl.containers.CamundaContainer.H2Configuration.*;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaProcessTestEmbeddedRuntime implements CamundaProcessTestRuntime {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaProcessTestEmbeddedRuntime.class);

  private final EmbeddedCamundaApplication application;

  public CamundaProcessTestEmbeddedRuntime(final CamundaProcessTestRuntimeBuilder runtimeBuilder) {
    application = new EmbeddedCamundaApplication();
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

    runtimeBuilder.getCamundaEnvVars().forEach(application::withProperty);

    // runtimeBuilder.getCamundaLoggerName()
  }

  @Override
  public void start() {

    application.start();
  }

  @Override
  public URI getCamundaRestApiAddress() {
    return URI.create("http://localhost:" + application.restPort());
  }

  @Override
  public URI getCamundaGrpcApiAddress() {
    return URI.create("http://localhost:" + application.grpcPort());
  }

  @Override
  public URI getCamundaMonitoringApiAddress() {
    return URI.create("http://localhost:" + application.monitoringPort());
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
    application.stop();
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
