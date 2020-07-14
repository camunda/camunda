/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebePort;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.VersionUtil;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;

public class RollingUpdateTest {
  private static final Logger LOG = LoggerFactory.getLogger(ContainerStateRule.class);
  private static final String OLD_VERSION = VersionUtil.getPreviousVersion();
  private static final String CURRENT_VERSION = "current-test";

  @Rule public AutoCloseableRule autoCloseable = new AutoCloseableRule();

  private List<ZeebeBrokerContainer> containers;
  private String initialContactPoints;
  private Network network;

  @Before
  public void setup() {
    initialContactPoints =
        IntStream.range(0, 3)
            .mapToObj(id -> "broker-" + id + ":" + ZeebePort.INTERNAL_API.getPort())
            .collect(Collectors.joining(","));

    network = Network.newNetwork();

    containers =
        Arrays.asList(
            manageClosable(new ZeebeBrokerContainer(OLD_VERSION)),
            manageClosable(new ZeebeBrokerContainer(OLD_VERSION)),
            manageClosable(new ZeebeBrokerContainer(OLD_VERSION)));

    configureBrokerContainer(0, containers);
    configureBrokerContainer(1, containers);
    configureBrokerContainer(2, containers);
  }

  @Test
  public void shouldBeAbleToRestartContainerWithSameVersion() {
    // given
    final var index = 0;
    final var sameVersion = OLD_VERSION;
    Startables.deepStart(containers).join();
    containers.get(index).shutdownGracefully(Duration.ofSeconds(30));

    // when
    final var zeebeBrokerContainer = replaceBrokerContainer(index, sameVersion);

    // then
    zeebeBrokerContainer.start();
  }

  @Test
  public void shouldBeAbleToRestartContainerWithNewVersion() {
    // given
    final var index = 0;
    final var newVersion = CURRENT_VERSION;
    Startables.deepStart(containers).join();
    containers.get(index).shutdownGracefully(Duration.ofSeconds(30));

    // when
    final var zeebeBrokerContainer = replaceBrokerContainer(index, newVersion);

    // then
    zeebeBrokerContainer.start();
  }

  private ZeebeBrokerContainer replaceBrokerContainer(final int index, final String newVersion) {
    final var broker = new ZeebeBrokerContainer(newVersion);
    containers.set(index, broker);
    return configureBrokerContainer(index, containers);
  }

  private ZeebeBrokerContainer configureBrokerContainer(
      final int index, final List<ZeebeBrokerContainer> brokers) {
    final int clusterSize = brokers.size();
    final var broker = brokers.get(index);
    final var hostName = "broker-" + index;
    broker.withNetworkAliases(hostName);

    return broker
        .withNetwork(network)
        .withEnv("ZEEBE_BROKER_NETWORK_HOST", "0.0.0.0")
        .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISED_HOST", hostName)
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERNAME", "zeebe-cluster")
        .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m")
        .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "1MB")
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "1MB")
        .withEnv("ZEEBE_BROKER_CLUSTER_NODEID", String.valueOf(index))
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERSIZE", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS", initialContactPoints)
        .withLogLevel(Level.DEBUG)
        .withDebug(false);
  }

  private <T extends AutoCloseable> T manageClosable(final T closeable) {
    autoCloseable.manage(closeable);
    return closeable;
  }
}
