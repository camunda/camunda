/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static io.camunda.application.commons.security.CamundaSecurityConfiguration.AUTHORIZATION_CHECKS_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;
import static io.camunda.configuration.beans.LegacySearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.CamundaContainer;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.container.CamundaContainer.GatewayContainer;
import io.camunda.container.ZeebeTopologyWaitStrategy;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.testcontainers.RemoteDebugger;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.agrona.LangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

final class ContainerState implements AutoCloseable {

  public static final int PARTITION_COUNT = 2;
  private static final RetryPolicy<Void> CONTAINER_START_RETRY_POLICY =
      new RetryPolicy<Void>().withMaxRetries(5).withBackoff(3, 30, ChronoUnit.SECONDS);
  private static final Pattern DOUBLE_NEWLINE = Pattern.compile("\n\n");
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(30);
  private static final Logger LOG = LoggerFactory.getLogger(ContainerState.class);
  private static final DockerImageName PREVIOUS_VERSION =
      DockerImageName.parse("camunda/camunda").withTag(VersionUtil.getPreviousVersion());
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  static {
    CONTAINER_START_RETRY_POLICY
        .handleIf(
            error ->
                error instanceof ContainerLaunchException
                    && error.getCause() instanceof ContainerFetchException)
        .onRetry(
            event -> {
              LOG.info("Attempt " + event.getAttemptCount());
              LOG.info("Retrying container start after exception:", event.getLastFailure());
            });
  }

  private final CamundaVolume volume = CamundaVolume.newVolume();

  private Network network = Network.SHARED;
  private BrokerContainer broker;
  private GatewayContainer gateway;
  private CamundaClient client;
  private PartitionsActuator partitionsActuator;

  private DockerImageName brokerImage;
  private DockerImageName gatewayImage;
  private boolean withRemoteDebugging;

  private boolean isSpring;
  private TestStandaloneBroker springBroker;
  private Path extractedDataDir;

  CamundaClient client() {
    return client;
  }

  ContainerState withOldBroker() {
    broker(PREVIOUS_VERSION);
    return this;
  }

  ContainerState withNewBroker() {
    isSpring = true;
    return this;
  }

  ContainerState withNetwork(final Network network) {
    this.network = network;
    return this;
  }

  ContainerState withOldGateway() {
    gatewayImage = PREVIOUS_VERSION;
    return this;
  }

  private ContainerState broker(final DockerImageName image) {
    brokerImage = image;
    return this;
  }

  public void start(final boolean enableDebug) {
    start(enableDebug, false);
  }

  public void start(final boolean enableDebug, final boolean withRemoteDebugging) {
    if (isSpring) {
      startSpringBroker(enableDebug);
      if (gatewayImage != null) {
        startOldGatewayForSpringBroker();
      }
      return;
    }

    final URI grpcAddress;
    broker =
        new BrokerContainer(brokerImage)
            .withUnifiedConfig(
                cfg -> {
                  cfg.getCluster().setPartitionCount(PARTITION_COUNT);
                  cfg.getData().getPrimaryStorage().getLogStream().setLogIndexDensity(1);
                  cfg.getData().setSnapshotPeriod(Duration.ofMinutes(1));
                  cfg.getData()
                      .getPrimaryStorage()
                      .getLogStream()
                      .setLogSegmentSize(DataSize.ofMegabytes(64));
                  cfg.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(128));
                  cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                })
            .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
            .withTopologyCheck(new ZeebeTopologyWaitStrategy(1, 1, PARTITION_COUNT))
            .withCamundaData(volume)
            .withNetwork(network);
    this.withRemoteDebugging = withRemoteDebugging;

    if (brokerImage.equals(PREVIOUS_VERSION)) {
      broker
          .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "128KB")
          .withEnv("CAMUNDA_DATABASE_TYPE", "NONE")
          .withEnv("CAMUNDA_REST_ENABLED", "false")
          .withEnv(CREATE_SCHEMA_ENV_VAR, "false");
    }

    if (withRemoteDebugging) {
      RemoteDebugger.configureContainer(broker);
      LOG.info("================================================");
      LOG.info("About to start broker....");
      LOG.info(
          "The broker will wait for a debugger to connect to it at port "
              + RemoteDebugger.DEFAULT_REMOTE_DEBUGGER_PORT
              + ". It will wait for "
              + RemoteDebugger.DEFAULT_START_TIMEOUT.toString());
      LOG.info("================================================");
    }

    if (enableDebug) {
      broker = broker.withEnv("ZEEBE_DEBUG", "true");
    }

    Failsafe.with(CONTAINER_START_RETRY_POLICY).run(() -> broker.self().start());

    if (gatewayImage == null) {
      grpcAddress = broker.getGrpcAddress();
    } else {
      gateway =
          new GatewayContainer(gatewayImage)
              .withUnifiedConfig(
                  cfg -> {
                    cfg.getCluster()
                        .setInitialContactPoints(List.of(broker.getInternalClusterAddress()));
                    cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                  })
              .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
              .withEnv(CREATE_SCHEMA_ENV_VAR, "false")
              .withEnv(UNPROTECTED_API_ENV_VAR, "true")
              .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false")
              .withTopologyCheck(new ZeebeTopologyWaitStrategy(1, 1, PARTITION_COUNT))
              .withNetwork(network);

      if (gatewayImage.equals(PREVIOUS_VERSION)) {
        // Gateway configuration is not part of the Unified config yet in 8.8.x
        gateway
            .withEnv(
                "ZEEBE_GATEWAY_CLUSTER_INITIALCONTACTPOINTS", broker.getInternalClusterAddress())
            .withEnv("ZEEBE_GATEWAY_NETWORK_HOST", "0.0.0.0")
            .withEnv("ZEEBE_GATEWAY_CLUSTER_MEMBERID", gateway.getInternalHost())
            .withEnv("ZEEBE_GATEWAY_CLUSTER_HOST", gateway.getInternalHost());
      }

      Failsafe.with(CONTAINER_START_RETRY_POLICY).run(() -> gateway.self().start());
      grpcAddress = gateway.getGrpcAddress();
    }

    client =
        CamundaClient.newClientBuilder().grpcAddress(grpcAddress).preferRestOverGrpc(false).build();
    partitionsActuator = PartitionsActuator.of(broker);
  }

  private void startSpringBroker(final boolean enableDebug) {
    try {
      extractedDataDir = Files.createTempDirectory("camunda-update-test-data");
    } catch (final IOException e) {
      LangUtil.rethrowUnchecked(e);
      return;
    }

    try {
      volume.extract(
          extractedDataDir,
          builder -> builder.withContainerPath(CamundaContainer.DEFAULT_CAMUNDA_DATA_PATH));
    } catch (final IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LangUtil.rethrowUnchecked(e);
      return;
    }

    final var workingDir = extractedDataDir.resolve("usr/local/camunda");

    RecordingExporter.reset();

    springBroker =
        new TestStandaloneBroker()
            .withWorkingDirectory(workingDir)
            .withUnauthenticatedAccess()
            .withUnifiedConfig(
                cfg -> {
                  cfg.getCluster().setPartitionCount(PARTITION_COUNT);
                  cfg.getData().getPrimaryStorage().getLogStream().setLogIndexDensity(1);
                  cfg.getData().setSnapshotPeriod(Duration.ofMinutes(1));
                  cfg.getData()
                      .getPrimaryStorage()
                      .getLogStream()
                      .setLogSegmentSize(DataSize.ofMegabytes(64));
                  cfg.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(128));
                  cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                  cfg.getSystem().getUpgrade().setEnableVersionCheck(false);
                  if (gatewayImage != null) {
                    // Advertise a hostname that Docker containers can resolve to reach this broker
                    cfg.getCluster().getNetwork().setAdvertisedHost("host.testcontainers.internal");
                    cfg.getCluster()
                        .getNetwork()
                        .getInternalApi()
                        .setAdvertisedHost("host.testcontainers.internal");
                    cfg.getCluster()
                        .getNetwork()
                        .getCommandApi()
                        .setAdvertisedHost("host.testcontainers.internal");
                  }
                });

    if (enableDebug) {
      springBroker.withRecordingExporter(true);
    }

    springBroker.start();

    client = springBroker.newClientBuilder().preferRestOverGrpc(false).build();
    partitionsActuator = PartitionsActuator.of(springBroker);
  }

  private void startOldGatewayForSpringBroker() {
    final int clusterPort = springBroker.mappedPort(TestZeebePort.CLUSTER);
    final int commandPort = springBroker.mappedPort(TestZeebePort.COMMAND);
    Testcontainers.exposeHostPorts(clusterPort, commandPort);

    gateway =
        new GatewayContainer(gatewayImage)
            .withUnifiedConfig(
                cfg -> {
                  cfg.getCluster()
                      .setInitialContactPoints(
                          List.of("host.testcontainers.internal:" + clusterPort));
                  cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                })
            .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
            .withEnv(CREATE_SCHEMA_ENV_VAR, "false")
            .withEnv(UNPROTECTED_API_ENV_VAR, "true")
            .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false")
            .withAccessToHost(true)
            .withTopologyCheck(new ZeebeTopologyWaitStrategy(1, 1, PARTITION_COUNT))
            .withNetwork(network);

    if (gatewayImage.equals(PREVIOUS_VERSION)) {
      gateway
          .withEnv(
              "ZEEBE_GATEWAY_CLUSTER_INITIALCONTACTPOINTS",
              "host.testcontainers.internal:" + clusterPort)
          .withEnv("ZEEBE_GATEWAY_NETWORK_HOST", "0.0.0.0")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_MEMBERID", gateway.getInternalHost())
          .withEnv("ZEEBE_GATEWAY_CLUSTER_HOST", gateway.getInternalHost());
    }

    Failsafe.with(CONTAINER_START_RETRY_POLICY).run(() -> gateway.self().start());

    client.close();
    client =
        CamundaClient.newClientBuilder()
            .grpcAddress(gateway.getGrpcAddress())
            .preferRestOverGrpc(false)
            .build();
  }

  public PartitionsActuator getPartitionsActuator() {
    return partitionsActuator;
  }

  @SuppressWarnings("java:S2925") // allow Thread.sleep usage in test if remote debugging is enabled
  public void onFailure() {
    if (isSpring) {
      log("SpringBroker", getLogs());
      if (gateway != null) {
        log("Gateway", gateway.getLogs());
      }
    } else {
      if (broker != null) {
        log("Broker", broker.getLogs());
      }
      if (gateway != null) {
        log("Gateway", gateway.getLogs());
      }
    }

    if (withRemoteDebugging) {
      try {
        LOG.info("Blocking for an hour to allow analysis with remote debugging");
        Thread.sleep(Duration.ofHours(1).toMillis());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        LangUtil.rethrowUnchecked(e);
      }
    }
  }

  private void log(final String type, final String log) {
    if (LOG.isErrorEnabled()) {
      LOG.error(
          String.format(
              "%n===============================================%n%s logs%n===============================================%n%s",
              type, log.replaceAll(DOUBLE_NEWLINE.pattern(), "\n")));
    }
  }

  /**
   * @return true if a record was found the element with the specified intent. Otherwise, returns
   *     false
   */
  public boolean hasElementInState(final String elementId, final String intent) {
    return hasLogContaining(
        String.format("\"elementId\":\"%s\"", elementId),
        String.format("\"intent\":\"%s\"", intent));
  }

  /**
   * @return true if the message was found in the specified intent. Otherwise, returns false
   */
  public boolean hasMessageInState(final String name, final String intent) {
    return hasLogContaining(
        String.format("\"name\":\"%s\"", name), String.format("\"intent\":\"%s\"", intent));
  }

  // returns true if it finds a line that contains every piece.
  boolean hasLogContaining(final String... pieces) {
    return getLogContaining(pieces) != null;
  }

  public String getLogContaining(final String... pieces) {
    final String[] lines = getLogs().split("\n");

    return Arrays.stream(lines)
        .filter(line -> Arrays.stream(pieces).allMatch(line::contains))
        .findFirst()
        .orElse(null);
  }

  public long getIncidentKey() {
    final String incidentCreated =
        getLogContaining("\"valueType\":\"INCIDENT\"", "\"intent\":\"CREATED\"");

    final Pattern pattern = Pattern.compile("(\"key\":)(\\d+)", Pattern.CASE_INSENSITIVE);
    final Matcher matcher = pattern.matcher(incidentCreated);

    long incidentKey = -1;
    if (matcher.find()) {
      try {
        incidentKey = Long.parseLong(matcher.group(2));
      } catch (final NumberFormatException | IndexOutOfBoundsException e) {
        // no key was found
      }
    }

    return incidentKey;
  }

  private String getLogs() {
    if (!isSpring) {
      return broker.getLogs();
    }
    return RecordingExporter.getRecords().stream()
        .map(
            r -> {
              try {
                return MAPPER.writeValueAsString(r);
              } catch (final JsonProcessingException e) {
                return "";
              }
            })
        .collect(Collectors.joining("\n"));
  }

  /** Close all opened resources. */
  @Override
  public void close() {
    if (client != null) {
      client.close();
      client = null;
    }

    if (isSpring) {
      if (gateway != null) {
        gateway.shutdownGracefully(CLOSE_TIMEOUT);
        gateway = null;
      }
      if (springBroker != null) {
        springBroker.close();
        springBroker = null;
      }
      if (extractedDataDir != null) {
        try {
          FileUtil.deleteFolder(extractedDataDir);
        } catch (final IOException e) {
          throw new RuntimeException(e);
        }
        extractedDataDir = null;
      }
      isSpring = false;
      return;
    }

    if (gateway != null) {
      gateway.shutdownGracefully(CLOSE_TIMEOUT);
      gateway = null;
    }

    if (broker != null) {
      broker.shutdownGracefully(CLOSE_TIMEOUT);
      broker = null;
    }

    brokerImage = null;
    gatewayImage = null;
  }
}
