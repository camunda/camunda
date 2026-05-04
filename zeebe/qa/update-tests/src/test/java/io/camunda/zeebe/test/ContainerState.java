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

import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.container.CamundaContainer.GatewayContainer;
import io.camunda.container.ZeebeTopologyWaitStrategy;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.testcontainers.RemoteDebugger;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.agrona.LangUtil;
import org.agrona.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * Test fixture used by update tests. Runs the previous version as a Docker container; runs the
 * current version directly via Spring Boot ({@link TestStandaloneBroker}). The two are mutually
 * exclusive: at any given time the state is backed by either an old broker container or an
 * in-process current broker, never both.
 *
 * <p>Data is handed over from the old container to the in-process broker by extracting the Docker
 * volume to a host directory and pointing the in-process broker at it via {@link
 * TestStandaloneBroker#withWorkingDirectory(Path)}.
 *
 * <p>An optional legacy {@link GatewayContainer} can be paired with the in-process broker; the
 * broker advertises itself as {@code host.testcontainers.internal} and the gateway dials back via
 * {@link Testcontainers#exposeHostPorts(int...)}.
 */
final class ContainerState implements AutoCloseable {

  public static final int PARTITION_COUNT = 2;

  private static final RetryPolicy<Void> CONTAINER_START_RETRY_POLICY =
      new RetryPolicy<Void>().withMaxRetries(5).withBackoff(3, 30, ChronoUnit.SECONDS);
  private static final Pattern DOUBLE_NEWLINE = Pattern.compile("\n\n");
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(30);
  private static final Logger LOG = LoggerFactory.getLogger(ContainerState.class);
  private static final DockerImageName PREVIOUS_VERSION =
      DockerImageName.parse("camunda/camunda").withTag(VersionUtil.getPreviousVersion());

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

  /** Volume backing the old container's data folder; null once it has been extracted/closed. */
  private CamundaVolume volume;

  /** Host directory holding extracted data for the in-process broker; null when not in use. */
  private Path inProcessDataDir;

  private Network network = Network.SHARED;

  // mutually exclusive
  private BrokerContainer oldBroker;
  private TestStandaloneBroker newBroker;

  /** Optional legacy gateway container; only valid alongside an in-process new broker. */
  private GatewayContainer oldGateway;

  private CamundaClient client;
  private PartitionsActuator partitionsActuator;
  private boolean useNewBroker;
  private boolean useOldGateway;
  private String withUser;

  CamundaClient client() {
    return client;
  }

  ContainerState withOldBroker() {
    useNewBroker = false;
    return this;
  }

  ContainerState withNewBroker() {
    useNewBroker = true;
    return this;
  }

  ContainerState withNetwork(final Network network) {
    this.network = network;
    return this;
  }

  ContainerState withOldGateway() {
    useOldGateway = true;
    return this;
  }

  ContainerState withUser(final String user) {
    withUser = user;
    return this;
  }

  public void start(final boolean enableDebug) {
    start(enableDebug, false);
  }

  public void start(final boolean enableDebug, final boolean withRemoteDebugging) {
    if (useNewBroker) {
      startNewBroker();
    } else {
      startOldBroker(enableDebug, withRemoteDebugging);
    }
  }

  private void startOldBroker(final boolean enableDebug, final boolean withRemoteDebugging) {
    if (volume == null) {
      volume = CamundaVolume.newVolume();
    }
    oldBroker =
        new BrokerContainer(PREVIOUS_VERSION)
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
            .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "128KB")
            .withEnv("CAMUNDA_DATABASE_TYPE", "NONE")
            .withEnv("CAMUNDA_REST_ENABLED", "false")
            .withEnv(CREATE_SCHEMA_ENV_VAR, "false")
            .withTopologyCheck(new ZeebeTopologyWaitStrategy(1, 1, PARTITION_COUNT))
            .withCamundaData(volume)
            .withNetwork(network);

    if (withRemoteDebugging) {
      RemoteDebugger.configureContainer(oldBroker);
      LOG.info("================================================");
      LOG.info("About to start broker....");
      LOG.info(
          "The broker will wait for a debugger to connect to it at port "
              + RemoteDebugger.DEFAULT_REMOTE_DEBUGGER_PORT
              + ". It will wait for "
              + RemoteDebugger.DEFAULT_START_TIMEOUT);
      LOG.info("================================================");
    }
    if (enableDebug) {
      oldBroker.withEnv("ZEEBE_DEBUG", "true");
    }
    if (!Strings.isEmpty(withUser)) {
      oldBroker.withCreateContainerCmdModifier(c -> c.withUser(withUser));
    }

    Failsafe.with(CONTAINER_START_RETRY_POLICY).run(() -> oldBroker.self().start());

    client =
        CamundaClient.newClientBuilder()
            .grpcAddress(oldBroker.getGrpcAddress())
            .preferRestOverGrpc(false)
            .build();
    partitionsActuator = PartitionsActuator.of(oldBroker);
  }

  private void startNewBroker() {
    // The current version reports "X.Y.Z-SNAPSHOT" by default; this trips
    // VersionCompatibilityCheck (pre-release versions are rejected). Override to the release form.
    VersionUtil.overrideVersionForTesting(VersionUtil.getVersion().replace("-SNAPSHOT", ""));

    // RecordingExporter is global static state; clear records left from a previous scenario.
    RecordingExporter.reset();

    final Path workingDir = handoverFromVolumeIfPresent();

    newBroker =
        new TestStandaloneBroker()
            .withRecordingExporter(true)
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
                });
    if (workingDir != null) {
      newBroker.withWorkingDirectory(workingDir);
    }

    if (useOldGateway) {
      startNewBrokerWithLegacyGateway();
    } else {
      newBroker.start();
      client = newBroker.newClientBuilder().preferRestOverGrpc(false).build();
    }

    partitionsActuator = PartitionsActuator.of(newBroker);
  }

  private void startNewBrokerWithLegacyGateway() {
    // The legacy gateway container needs to dial the in-process broker. Make the broker advertise
    // itself as host.testcontainers.internal so the container can reach it through the
    // Testcontainers SSH-forward sidecar.
    final var clusterPort = newBroker.mappedPort(TestZeebePort.CLUSTER);
    newBroker.withClusterConfig(
        c -> {
          c.getNetwork().setAdvertisedHost("host.testcontainers.internal");
          c.getNetwork().getInternalApi().setAdvertisedPort(clusterPort);
        });
    Testcontainers.exposeHostPorts(clusterPort);
    newBroker.start();

    final var brokerContactPoint = "host.testcontainers.internal:" + clusterPort;
    oldGateway =
        new GatewayContainer(PREVIOUS_VERSION)
            .withUnifiedConfig(
                cfg -> {
                  cfg.getCluster().setInitialContactPoints(List.of(brokerContactPoint));
                  cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                })
            .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
            .withEnv(CREATE_SCHEMA_ENV_VAR, "false")
            .withEnv(UNPROTECTED_API_ENV_VAR, "true")
            .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false")
            // Pre-8.10 gateway image: Unified config does not yet drive the gateway
            .withEnv("ZEEBE_GATEWAY_CLUSTER_INITIALCONTACTPOINTS", brokerContactPoint)
            .withEnv("ZEEBE_GATEWAY_NETWORK_HOST", "0.0.0.0")
            .withTopologyCheck(new ZeebeTopologyWaitStrategy(1, 1, PARTITION_COUNT))
            .withNetwork(network);
    oldGateway.withEnv("ZEEBE_GATEWAY_CLUSTER_MEMBERID", oldGateway.getInternalHost());
    oldGateway.withEnv("ZEEBE_GATEWAY_CLUSTER_HOST", oldGateway.getInternalHost());

    Failsafe.with(CONTAINER_START_RETRY_POLICY).run(() -> oldGateway.self().start());

    client =
        CamundaClient.newClientBuilder()
            .grpcAddress(oldGateway.getGrpcAddress())
            .preferRestOverGrpc(false)
            .build();
  }

  private Path handoverFromVolumeIfPresent() {
    if (volume == null) {
      return null;
    }
    try {
      inProcessDataDir = Files.createTempDirectory("update-test-data");
      // The broker resolves data.directory ("data") relative to its working directory. The volume
      // root holds the data directory's contents directly (mount path was
      // /usr/local/camunda/data), so extract under <workingDir>/data so paths line up.
      final Path dataSubdir = Files.createDirectory(inProcessDataDir.resolve("data"));
      volume.extract(dataSubdir);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to extract volume to host directory", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LangUtil.rethrowUnchecked(e);
    }
    volume.close();
    volume = null;
    return inProcessDataDir;
  }

  public PartitionsActuator getPartitionsActuator() {
    return partitionsActuator;
  }

  @SuppressWarnings("java:S2925") // allow Thread.sleep usage in test if remote debugging is enabled
  public void onFailure() {
    if (oldBroker != null) {
      log("Broker", oldBroker.getLogs());
    }
    if (oldGateway != null) {
      log("Gateway", oldGateway.getLogs());
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
   * Returns true if any record produced by the active broker contains all the given substrings in
   * its JSON form. For the old container this scans the broker logs; for the in-process current
   * broker this scans records captured by the {@link RecordingExporter}.
   *
   * <p>Both backends produce identical JSON via {@code Record#toString()}, so callers can match on
   * the same strings (e.g. {@code "\"intent\":\"CREATED\""}, {@code "\"valueType\":\"INCIDENT\""}).
   */
  public boolean hasLogContaining(final String... pieces) {
    return getLogContaining(pieces) != null;
  }

  public String getLogContaining(final String... pieces) {
    if (oldBroker != null) {
      return Arrays.stream(oldBroker.getLogs().split("\n"))
          .filter(line -> Arrays.stream(pieces).allMatch(line::contains))
          .findFirst()
          .orElse(null);
    }
    if (newBroker != null) {
      return RecordingExporter.getRecords().stream()
          .map(Record::toString)
          .filter(s -> Arrays.stream(pieces).allMatch(s::contains))
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  public boolean hasElementInState(final String elementId, final String intent) {
    return hasLogContaining(
        String.format("\"elementId\":\"%s\"", elementId),
        String.format("\"intent\":\"%s\"", intent));
  }

  public boolean hasMessageInState(final String name, final String intent) {
    return hasLogContaining(
        String.format("\"name\":\"%s\"", name), String.format("\"intent\":\"%s\"", intent));
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

  /** Closes the active broker and any associated resources. Safe to call repeatedly. */
  @Override
  public void close() {
    if (client != null) {
      client.close();
      client = null;
    }
    if (oldGateway != null) {
      oldGateway.shutdownGracefully(CLOSE_TIMEOUT);
      oldGateway = null;
    }
    if (oldBroker != null) {
      oldBroker.shutdownGracefully(CLOSE_TIMEOUT);
      oldBroker = null;
    }
    if (newBroker != null) {
      newBroker.close();
      newBroker = null;
      VersionUtil.resetVersionForTesting();
      RecordingExporter.reset();
    }
    if (inProcessDataDir != null) {
      try {
        FileUtil.deleteFolder(inProcessDataDir);
      } catch (final IOException e) {
        LOG.warn("Failed to delete in-process data directory {}", inProcessDataDir, e);
      }
      inProcessDataDir = null;
    }
    partitionsActuator = null;
    useOldGateway = false;
  }
}
