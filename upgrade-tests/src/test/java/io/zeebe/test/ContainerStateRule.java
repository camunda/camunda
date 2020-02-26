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
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.testcontainers.containers.Network;

class ContainerStateRule extends ExternalResource {

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
  public void after() {
    if (broker != null) {
      log("Broker", broker.getLogs());
    }

    if (gateway != null) {
      log("Gateway", gateway.getLogs());
    }

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
            .withNetwork(network)
            .withEmbeddedGateway(gatewayVersion == null)
            .withLogLevel(Level.DEBUG);

    broker.start();
    String contactPoint = broker.getExternalAddress(ZeebePort.GATEWAY);

    if (gatewayVersion != null) {
      gateway =
          new ZeebeStandaloneGatewayContainer(gatewayVersion)
              .withContactPoint(broker.getContactPoint())
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
              type, log.replaceAll("\n\n", "\n")));
    }
  }

  /**
   * @return true if a record was found the element with the specified intent. Otherwise, returns
   *     false
   */
  public boolean hasElementInState(final String elementId, final String intent) {
    final String[] lines = broker.getLogs().split("\n");

    for (int i = lines.length - 1; i >= 0; --i) {
      if (lines[i].contains(String.format("\"elementId\":\"%s\"", elementId))
          && lines[i].contains(String.format("\"intent\":\"%s\"", intent))) {
        return true;
      }
    }

    return false;
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
