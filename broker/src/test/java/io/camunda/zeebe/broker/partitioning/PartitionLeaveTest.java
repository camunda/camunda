/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static io.camunda.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.test.TestActorSchedulerFactory;
import io.camunda.zeebe.broker.test.TestClusterFactory;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PartitionLeaveTest {

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
    CompletableFuture.allOf(broker0.start(), broker1.start()).join();

    try (final var client =
        ZeebeClient.newClientBuilder()
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

      // then -- process instance can still be completed because quorum is still available
      client.newPublishMessageCommand().messageName("msg").correlationKey("key").send().join();
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
    final var systemContext =
        new SystemContext(
            brokerCfg,
            TestActorSchedulerFactory.ofBrokerConfig(brokerCfg),
            TestClusterFactory.createAtomixCluster(brokerCfg));
    systemContext.getScheduler().start();

    return new Broker(systemContext, new SpringBrokerBridge(), List.of());
  }
}
