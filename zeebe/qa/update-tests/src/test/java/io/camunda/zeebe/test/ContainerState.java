/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.testcontainers.RemoteDebugger;
import io.camunda.zeebe.util.VersionUtil;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeGatewayContainer;
import io.zeebe.containers.ZeebeVolume;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.agrona.LangUtil;
import org.agrona.Strings;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

final class ContainerState implements CloseableResource {

  private static final RetryPolicy<Void> CONTAINER_START_RETRY_POLICY =
      new RetryPolicy<Void>().withMaxRetries(5).withBackoff(3, 30, ChronoUnit.SECONDS);
  private static final Pattern DOUBLE_NEWLINE = Pattern.compile("\n\n");
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(30);
  private static final Logger LOG = LoggerFactory.getLogger(ContainerState.class);
  private static final DockerImageName PREVIOUS_VERSION =
      DockerImageName.parse("camunda/zeebe").withTag(VersionUtil.getPreviousVersion());
  private static final DockerImageName CURRENT_VERSION =
      ZeebeTestContainerDefaults.defaultTestImage();

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

  private final ZeebeVolume volume = ZeebeVolume.newVolume();

  private Network network = Network.SHARED;
  private ZeebeContainer broker;
  private ZeebeGatewayContainer gateway;
  private CamundaClient client;
  private PartitionsActuator partitionsActuator;

  private DockerImageName brokerImage;
  private DockerImageName gatewayImage;
  private boolean withRemoteDebugging;

  private String withUser;
  private String withVersionOverride;

  CamundaClient client() {
    return client;
  }

  ContainerState withOldBroker() {
    broker(PREVIOUS_VERSION);
    // user - needs to be set to `1001` to allow a smooth update from zeebe 8.3 to 8.4,
    // as the default user changed to `1001` with 8.4 and was `1000` with 8.3
    // TODO remove after 8.4 release
    withUser("1001");
    return this;
  }

  ContainerState withNewBroker() {
    // user - `1001` is the default in 8.4
    // group - needs to be set to `0` as the data volume in 8.3 is owned by 1000:0
    // thus zeebe 8.4 needs to run with group `0` to be able to create new files in
    // the root of the data volume (in particular it creates a new `.topology.meta` file)
    // TODO remove after 8.4 release
    withUser("1001:0");

    withVersionOverride(VersionUtil.getVersion().replace("-SNAPSHOT", ""));

    return broker(CURRENT_VERSION);
  }

  ContainerState withNetwork(final Network network) {
    this.network = network;
    return this;
  }

  ContainerState withOldGateway() {
    gatewayImage = PREVIOUS_VERSION;
    return this;
  }

  ContainerState withUser(final String user) {
    withUser = user;
    return this;
  }

  ContainerState withVersionOverride(final String version) {
    withVersionOverride = version;
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
    final String contactPoint;
    broker =
        new ZeebeContainer(brokerImage)
            .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
            .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "128KB")
            .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "64MB")
            .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m")
            .withEnv("ZEEBE_BROKER_DATA_LOGINDEXDENSITY", "1")
            .withEnv("CAMUNDA_DATABASE_SCHEMA_MANAGER_CREATE_SCHEMA", "false")
            .withZeebeData(volume)
            .withNetwork(network);
    this.withRemoteDebugging = withRemoteDebugging;

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

    if (!Strings.isEmpty(withUser)) {
      broker.withCreateContainerCmdModifier(
          createContainerCmd -> createContainerCmd.withUser(withUser));
    }

    if (!Strings.isEmpty(withVersionOverride)) {
      broker.withEnv(VersionUtil.VERSION_OVERRIDE_ENV_NAME, withVersionOverride);
    }

    Failsafe.with(CONTAINER_START_RETRY_POLICY).run(() -> broker.self().start());

    if (gatewayImage == null) {
      contactPoint = broker.getExternalGatewayAddress();
    } else {
      gateway =
          new ZeebeGatewayContainer(gatewayImage)
              .withEnv("ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT", broker.getInternalClusterAddress())
              .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
              .withEnv("CAMUNDA_DATABASE_SCHEMA_MANAGER_CREATE_SCHEMA", "false")
              .withNetwork(network);

      Failsafe.with(CONTAINER_START_RETRY_POLICY).run(() -> gateway.self().start());
      contactPoint = gateway.getExternalGatewayAddress();
    }

    client = CamundaClient.newClientBuilder().gatewayAddress(contactPoint).usePlaintext().build();
    partitionsActuator = PartitionsActuator.of(broker);
  }

  public PartitionsActuator getPartitionsActuator() {
    return partitionsActuator;
  }

  @SuppressWarnings("java:S2925") // allow Thread.sleep usage in test if remote debugging is enabled
  public void onFailure() {
    if (broker != null) {
      log("Broker", broker.getLogs());
    }

    if (gateway != null) {
      log("Gateway", gateway.getLogs());
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
    final String[] lines = broker.getLogs().split("\n");

    return Arrays.stream(lines)
        .filter(line -> Arrays.stream(pieces).allMatch(line::contains))
        .findFirst()
        .orElse(null);
  }

  public long getIncidentKey() {
    final String incidentCreated = getLogContaining("INCIDENT", "CREATED");

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

  /** Close all opened resources. */
  @Override
  public void close() {
    if (client != null) {
      client.close();
      client = null;
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
