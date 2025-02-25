/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static io.camunda.zeebe.broker.test.EmbeddedBrokerRule.assignSocketAddresses;

import io.atomix.cluster.MemberId;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.test.TestActorSchedulerFactory;
import io.camunda.zeebe.broker.test.TestBrokerClientFactory;
import io.camunda.zeebe.broker.test.TestClusterFactory;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PartitionJoinTest {
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  void canJoinPartition(@TempDir final Path tmp) {
    // given
    @SuppressWarnings("resource")
    final var existingBroker =
        buildBroker(
            tmp.resolve("broker-0"),
            brokerCfg -> {
              final var clusterCfg = brokerCfg.getCluster();

              clusterCfg.setClusterSize(1);
              clusterCfg.setNodeId(0);
              clusterCfg.setPartitionsCount(1);
              clusterCfg.setReplicationFactor(1);
            });

    final var initialContactPoint =
        existingBroker.getConfig().getNetwork().getInternalApi().getAdvertisedAddress();

    @SuppressWarnings("resource")
    final var joiningBroker =
        buildBroker(
            tmp.resolve("broker-1"),
            brokerCfg -> {
              final var clusterCfg = brokerCfg.getCluster();

              // Joining broker knows about existing broker
              clusterCfg.setInitialContactPoints(
                  List.of(initialContactPoint.getHostName() + ":" + initialContactPoint.getPort()));

              // Static configuration initially results in a broker without any partitions
              clusterCfg.setClusterSize(1);
              clusterCfg.setNodeId(1);
              clusterCfg.setPartitionsCount(0);
              clusterCfg.setReplicationFactor(0);
            });

    // when
    try (final var existing = existingBroker.start().join();
        final var joining = joiningBroker.start().join()) {
      Awaitility.await("Joining broker knows about existing broker")
          .pollDelay(Duration.ofSeconds(1))
          .pollInterval(Duration.ofMillis(500))
          .until(
              () ->
                  joining
                      .getBrokerContext()
                      .getClusterServices()
                      .getMembershipService()
                      .getMembers(),
              members -> members.size() == 2);

      final var partitionManager =
          (PartitionManagerImpl) joining.getBrokerContext().getPartitionManager();

      // then
      Assertions.assertThat(
              partitionManager.join(
                  1,
                  Map.of(MemberId.from("0"), 2, MemberId.from("1"), 1),
                  DynamicPartitionConfig.init()))
          .succeedsWithin(Duration.ofSeconds(10));
    }
  }

  Broker buildBroker(final Path tmp, final Consumer<BrokerCfg> configure) {
    final var brokerCfg = new BrokerCfg();
    assignSocketAddresses(brokerCfg);
    brokerCfg.init(tmp.toAbsolutePath().toString());
    configure.accept(brokerCfg);
    final var atomixCluster = TestClusterFactory.createAtomixCluster(brokerCfg, meterRegistry);
    final var actorScheduler = TestActorSchedulerFactory.ofBrokerConfig(brokerCfg);
    final var brokerClient =
        TestBrokerClientFactory.createBrokerClient(atomixCluster, actorScheduler);
    final var systemContext =
        new SystemContext(
            brokerCfg,
            actorScheduler,
            atomixCluster,
            brokerClient,
            new SecurityConfiguration(),
            null,
            null,
            null);

    return new Broker(systemContext, new SpringBrokerBridge(), List.of());
  }
}
