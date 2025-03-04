/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static io.camunda.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.security.configuration.SecurityConfigurations;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.test.TestActorSchedulerFactory;
import io.camunda.zeebe.broker.test.TestBrokerClientFactory;
import io.camunda.zeebe.broker.test.TestClusterFactory;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PartitionLeaveTest {
  private static final MeterRegistry METER_REGISTRY = new SimpleMeterRegistry();

  @Test
  void canStillProcessAfterLeaving(@TempDir final Path tmp) {
    // given -- two brokers replicating partition 1
    final var broker0 =
        buildBroker(
            tmp.resolve("broker-0"),
            brokerCfg -> {
              final var clusterCfg = brokerCfg.getCluster();
              clusterCfg.setClusterSize(2);
              clusterCfg.setNodeId(0);
              clusterCfg.setPartitionsCount(1);
              clusterCfg.setReplicationFactor(2);
            });
    final var initialContactPoint =
        broker0.getConfig().getNetwork().getInternalApi().getAdvertisedAddress();

    final var broker1 =
        buildBroker(
            tmp.resolve("broker-1"),
            brokerCfg -> {
              final var clusterCfg = brokerCfg.getCluster();
              clusterCfg.setInitialContactPoints(
                  List.of(initialContactPoint.getHostName() + ":" + initialContactPoint.getPort()));

              clusterCfg.setClusterSize(2);
              clusterCfg.setNodeId(1);
              clusterCfg.setPartitionsCount(1);
              clusterCfg.setReplicationFactor(2);
            });
    CompletableFuture.allOf(
            CompletableFuture.runAsync(broker0::start), CompletableFuture.runAsync(broker1::start))
        .join();

    try (final var client =
        CamundaClient.newClientBuilder()
            .usePlaintext()
            .gatewayAddress("localhost:" + broker0.getConfig().getGateway().getNetwork().getPort())
            .build()) {
      Awaitility.await()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .isComplete(2, 1, 2));

      // when -- one broker leaves partition 1
      ((PartitionManagerImpl) broker1.getBrokerContext().getPartitionManager()).leave(1).join();

      // then -- request can still be processed because quorum is still available
      client.newPublishMessageCommand().messageName("msg").correlationKey("key").send().join();
    } finally {
      broker0.close();
      broker1.close();
    }
  }

  @Test
  void shouldRemoveDataAfterLeaving(@TempDir final Path tmp) {
    // given -- two brokers replicating partition 1
    final var broker0 =
        buildBroker(
            tmp.resolve("broker-0"),
            brokerCfg -> {
              final var clusterCfg = brokerCfg.getCluster();
              clusterCfg.setClusterSize(2);
              clusterCfg.setNodeId(0);
              clusterCfg.setPartitionsCount(2);
              clusterCfg.setReplicationFactor(2);
            });
    final var initialContactPoint =
        broker0.getConfig().getNetwork().getInternalApi().getAdvertisedAddress();

    final var broker1 =
        buildBroker(
            tmp.resolve("broker-1"),
            brokerCfg -> {
              final var clusterCfg = brokerCfg.getCluster();
              clusterCfg.setInitialContactPoints(
                  List.of(initialContactPoint.getHostName() + ":" + initialContactPoint.getPort()));

              clusterCfg.setClusterSize(2);
              clusterCfg.setNodeId(1);
              clusterCfg.setPartitionsCount(2);
              clusterCfg.setReplicationFactor(2);
            });
    CompletableFuture.allOf(
            CompletableFuture.runAsync(broker0::start), CompletableFuture.runAsync(broker1::start))
        .join();

    try (final var client =
        CamundaClient.newClientBuilder()
            .usePlaintext()
            .gatewayAddress("localhost:" + broker0.getConfig().getGateway().getNetwork().getPort())
            .build()) {
      Awaitility.await()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .isComplete(2, 2, 2));

      // when -- one broker leaves partition 1
      ((PartitionManagerImpl) broker1.getBrokerContext().getPartitionManager()).leave(1).join();

      // then -- partition-1's data is removed
      assertThat(tmp.resolve("broker-1/data/raft-partition/partitions/1")).doesNotExist();
      // then -- all other data is still there
      assertThat(tmp.resolve("broker-1/data/raft-partition/partitions/2")).isNotEmptyDirectory();
      assertThat(tmp.resolve("broker-0/data/raft-partition/partitions/1")).isNotEmptyDirectory();
      assertThat(tmp.resolve("broker-0/data/raft-partition/partitions/2")).isNotEmptyDirectory();
    } finally {
      broker0.close();
      broker1.close();
    }
  }

  @Test
  void shouldNotRemoveDataIfLeavingFails(@TempDir final Path tmp) {
    // given -- two brokers replicating partition 1
    final var broker0 =
        buildBroker(
            tmp.resolve("broker-0"),
            brokerCfg -> {
              final var clusterCfg = brokerCfg.getCluster();
              clusterCfg.setClusterSize(2);
              clusterCfg.setNodeId(0);
              clusterCfg.setPartitionsCount(2);
              clusterCfg.setReplicationFactor(2);
            });
    final var initialContactPoint =
        broker0.getConfig().getNetwork().getInternalApi().getAdvertisedAddress();

    final var broker1 =
        buildBroker(
            tmp.resolve("broker-1"),
            brokerCfg -> {
              final var clusterCfg = brokerCfg.getCluster();
              clusterCfg.setInitialContactPoints(
                  List.of(initialContactPoint.getHostName() + ":" + initialContactPoint.getPort()));

              clusterCfg.setClusterSize(2);
              clusterCfg.setNodeId(1);
              clusterCfg.setPartitionsCount(2);
              clusterCfg.setReplicationFactor(2);
            });
    CompletableFuture.allOf(
            CompletableFuture.runAsync(broker0::start), CompletableFuture.runAsync(broker1::start))
        .join();

    try (final var client =
        CamundaClient.newClientBuilder()
            .usePlaintext()
            .gatewayAddress("localhost:" + broker0.getConfig().getGateway().getNetwork().getPort())
            .build()) {
      Awaitility.await()
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .isComplete(2, 2, 2));

      // when -- broker 0 stops and thus broker 1 fails to leave
      broker0.close();
      Assertions.assertThatThrownBy(
          () ->
              ((PartitionManagerImpl) broker1.getBrokerContext().getPartitionManager())
                  .leave(1)
                  .join());

      // then -- all data remains
      assertThat(tmp.resolve("broker-1/data/raft-partition/partitions/1")).isNotEmptyDirectory();
      assertThat(tmp.resolve("broker-1/data/raft-partition/partitions/2")).isNotEmptyDirectory();
      assertThat(tmp.resolve("broker-0/data/raft-partition/partitions/1")).isNotEmptyDirectory();
      assertThat(tmp.resolve("broker-0/data/raft-partition/partitions/2")).isNotEmptyDirectory();
    } finally {
      broker0.close();
      broker1.close();
    }
  }

  private static Broker buildBroker(final Path tmp, final Consumer<BrokerCfg> configure) {
    final var brokerCfg = new BrokerCfg();
    assignSocketAddresses(brokerCfg);
    brokerCfg.init(tmp.toAbsolutePath().toString());
    configure.accept(brokerCfg);
    final var actorScheduler = TestActorSchedulerFactory.ofBrokerConfig(brokerCfg);
    final var atomixCluster = TestClusterFactory.createAtomixCluster(brokerCfg, METER_REGISTRY);
    final var brokerClient =
        TestBrokerClientFactory.createBrokerClient(atomixCluster, actorScheduler);
    final var systemContext =
        new SystemContext(
            brokerCfg,
            actorScheduler,
            atomixCluster,
            brokerClient,
            SecurityConfigurations.unauthenticated(),
            null,
            null,
            null);

    return new Broker(systemContext, new SpringBrokerBridge(), List.of());
  }
}
