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

  /** Mount path of the broker's data folder inside the legacy container image. */
  private static final String BROKER_DATA_PATH_IN_CONTAINER = "/usr/local/camunda/data";

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
      newBroker.start().awaitCompleteTopology(1, PARTITION_COUNT, 1, Duration.ofSeconds(30));
      client = newBroker.newClientBuilder().preferRestOverGrpc(false).build();
    }

    partitionsActuator = PartitionsActuator.of(newBroker);
  }

  private void startNewBrokerWithLegacyGateway() {
    // Bridge container ↔ in-process gossip both ways:
    //   • Gateway → broker: broker advertises host.testcontainers.internal for both its
    //     cluster-API (gossip) and command-API ports, and Testcontainers exposes both through
    //     the SSH-forward sidecar so the gateway container can reach them.
    //   • Broker → gateway: pin the gateway container's cluster-API port to a known host port
    //     and have the gateway advertise localhost:<thatPort>, which the host-side broker can dial.
    final var brokerClusterPort = newBroker.mappedPort(TestZeebePort.CLUSTER);
    final var brokerCommandPort = newBroker.mappedPort(TestZeebePort.COMMAND);
    newBroker.withClusterConfig(
        c -> {
          c.getNetwork().setAdvertisedHost("host.testcontainers.internal");
          c.getNetwork().getInternalApi().setAdvertisedPort(brokerClusterPort);
          c.getNetwork().getCommandApi().setAdvertisedPort(brokerCommandPort);
        });
    Testcontainers.exposeHostPorts(brokerClusterPort, brokerCommandPort);
    newBroker.start();

    final var gatewayHostPort =
        io.camunda.zeebe.test.util.socket.SocketUtil.getNextAddress().getPort();
    final var brokerContactPoint = "host.testcontainers.internal:" + brokerClusterPort;
    oldGateway =
        new GatewayContainer(PREVIOUS_VERSION)
            .withUnifiedConfig(
                cfg -> {
                  cfg.getCluster().setInitialContactPoints(List.of(brokerContactPoint));
                  cfg.getCluster().getNetwork().setHost("0.0.0.0");
                  // Make the gateway reachable from the host-side broker via
                  // localhost:<gatewayHostPort>. The container's port 26502 is pinned to that host
                  // port below.
                  cfg.getCluster().getNetwork().setAdvertisedHost("localhost");
                  cfg.getCluster().getNetwork().getInternalApi().setAdvertisedPort(gatewayHostPort);
                  cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                })
            .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
            .withEnv(CREATE_SCHEMA_ENV_VAR, "false")
            .withEnv(UNPROTECTED_API_ENV_VAR, "true")
            .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false")
            .withTopologyCheck(new ZeebeTopologyWaitStrategy(1, 1, PARTITION_COUNT));
    // Pin the gateway container's cluster API port so the host-side broker can dial it back.
    oldGateway.setPortBindings(List.of(gatewayHostPort + ":26502"));

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
      // CamundaVolume.extract spawns a tiny container with the volume mounted at
      // /usr/local/camunda/data and tars the requested path. By default it tars /tmp, which only
      // contains the archive itself; ask it to tar the volume mount path instead. BusyBox tar
      // strips the leading slash, so entries land at <extractRoot>/usr/local/camunda/data/...
      // The broker resolves data.directory ("data") against its working directory, so picking
      // /usr/local/camunda as the working directory makes the broker read from the right place.
      volume.extract(
          inProcessDataDir, builder -> builder.withContainerPath(BROKER_DATA_PATH_IN_CONTAINER));
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to extract volume to host directory", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LangUtil.rethrowUnchecked(e);
    }
    // The Docker volume is still referenced by the (stopped) old broker container at this point;
    // attempting to remove it now would race the container teardown. Drop the reference and let
    // the Testcontainers reaper clean it up at JVM shutdown.
    volume = null;
    return inProcessDataDir.resolve("usr/local/camunda");
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
    if (newBroker != null) {
      final var records = RecordingExporter.getRecords();
      LOG.error("RecordingExporter captured {} records", records.size());
      records.forEach(r -> LOG.error("  {}", r.toJson()));
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
      // Record#toString() truncates the JSON to 1024 chars (CopiedRecord), which can elide fields
      // we look for (e.g. JOB records carry "elementId" near the tail). Use toJson() instead.
      return RecordingExporter.getRecords().stream()
          .map(Record::toJson)
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
