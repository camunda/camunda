/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import io.zeebe.client.ZeebeClient;
import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.ZeebeStandaloneGatewayContainer;
import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.testcontainers.containers.Network;

class ContainerStateRule extends TestWatcher {

  private static final Pattern DOUBLE_NEWLINE = Pattern.compile("\n\n");
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(30);
  private static final Logger LOG = LoggerFactory.getLogger(ContainerStateRule.class);
  private ZeebeBrokerContainer broker;
  private ZeebeStandaloneGatewayContainer gateway;
  private ZeebeClient client;
  private Network network;

  private String brokerVersion;
  private String volumePath;
  private String gatewayVersion;

  public ZeebeClient client() {
    return client;
  }

  @Override
  protected void failed(final Throwable e, final Description description) {
    super.failed(e, description);
    if (broker != null) {
      log("Broker", broker.getLogs());
    }

    if (gateway != null) {
      log("Gateway", gateway.getLogs());
    }
  }

  @Override
  protected void finished(final Description description) {
    close();
  }

  public ContainerStateRule broker(final String version, final String volumePath) {
    this.brokerVersion = version;
    this.volumePath = volumePath;
    return this;
  }

  public ContainerStateRule withStandaloneGateway(final String gatewayVersion) {
    this.gatewayVersion = gatewayVersion;
    return this;
  }

  public void start() {
    network = Network.newNetwork();
    broker =
        new ZeebeBrokerContainer(brokerVersion)
            .withFileSystemBind(volumePath, "/usr/local/zeebe/data")
            .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERNAME", "zeebe-cluster")
            .withNetwork(network)
            .withEmbeddedGateway(gatewayVersion == null)
            .withLogLevel(Level.DEBUG)
            .withDebug(false);

    broker.start();
    String contactPoint = broker.getExternalAddress(ZeebePort.GATEWAY);

    if (gatewayVersion != null) {
      gateway =
          new ZeebeStandaloneGatewayContainer(gatewayVersion)
              .withEnv("ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT", broker.getContactPoint())
              .withEnv("ZEEBE_GATEWAY_CLUSTER_CLUSTERNAME", "zeebe-cluster")
              .withNetwork(network)
              .withLogLevel(Level.DEBUG);
      gateway.start();

      contactPoint = gateway.getExternalAddress(ZeebePort.GATEWAY);
    }

    client = ZeebeClient.newClientBuilder().brokerContactPoint(contactPoint).usePlaintext().build();
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

  /** @return true if the message was found in the specified intent. Otherwise, returns false */
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
  public void close() {
    if (client != null) {
      client.close();
      client = null;
    }

    if (gateway != null) {
      gateway.close();
      gateway = null;
    }

    if (broker != null) {
      broker.shutdownGracefully(CLOSE_TIMEOUT);
      broker = null;
    }

    if (network != null) {
      network.close();
      network = null;
    }

    brokerVersion = null;
    gatewayVersion = null;
    volumePath = null;
  }
}
