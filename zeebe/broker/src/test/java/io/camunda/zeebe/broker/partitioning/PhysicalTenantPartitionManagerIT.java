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

import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.SystemContextTestFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.test.TestActorSchedulerFactory;
import io.camunda.zeebe.broker.test.TestBrokerClientFactory;
import io.camunda.zeebe.broker.test.TestClusterFactory;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PhysicalTenantPartitionManagerIT {

  private static final MeterRegistry METER_REGISTRY = new SimpleMeterRegistry();

  @Test
  void shouldStartOnePartitionManagerPerConfiguredPhysicalTenant(@TempDir final Path tmp) {
    // given — single-node broker configured with an extra physical tenant
    try (final var broker =
        buildBroker(
            tmp,
            () -> Set.of(PartitionManagerImpl.DEFAULT_GROUP_NAME, "tenant2"),
            brokerCfg -> {
              final var clusterCfg = brokerCfg.getCluster();
              clusterCfg.setClusterSize(1);
              clusterCfg.setNodeId(0);
              clusterCfg.setPartitionsCount(1);
              clusterCfg.setReplicationFactor(1);
            })) {
      // when — the broker starts up
      broker.start().join();

      // then — both physical tenants laid down their partition directories on disk
      final var defaultPartitionDir = tmp.resolve("data/default/partitions/1");
      final var secondaryPartitionDir = tmp.resolve("data/tenant2/partitions/1");
      assertThat(defaultPartitionDir)
          .as("default physical tenant's partition 1 directory")
          .isNotEmptyDirectory();
      assertThat(secondaryPartitionDir).as("tenant2's partition 1 directory").isNotEmptyDirectory();

      // and — the default tenant's partition manager is reachable via the single-manager shortcut
      assertThat(broker.getBrokerContext().getPartitionManager())
          .as("default partition manager exposed on BrokerContext")
          .isNotNull();
    }
  }

  private static Broker buildBroker(
      final Path tmp,
      final PhysicalTenantIds physicalTenantIds,
      final Consumer<BrokerCfg> configure) {
    final var brokerCfg = new BrokerCfg();
    assignSocketAddresses(brokerCfg);
    configure.accept(brokerCfg);
    brokerCfg.init(tmp.toAbsolutePath().toString());
    final var actorScheduler = TestActorSchedulerFactory.ofBrokerConfig(brokerCfg);
    final var atomixCluster = TestClusterFactory.createAtomixCluster(brokerCfg, METER_REGISTRY);
    final var brokerClient =
        TestBrokerClientFactory.createBrokerClient(atomixCluster, actorScheduler);
    final var systemContext =
        SystemContextTestFactory.singleTenant(
            SystemContext.DEFAULT_SHUTDOWN_TIMEOUT,
            brokerCfg,
            null,
            actorScheduler,
            atomixCluster,
            brokerClient,
            new SimpleMeterRegistry(),
            EngineSecurityConfigurations.unauthenticatedAndUnauthorized(),
            null,
            null,
            null,
            (OidcClaimsProvider) (jwtClaims, tokenValue) -> jwtClaims,
            null,
            null,
            NodeIdProvider.staticProvider(brokerCfg.getCluster().getNodeId()),
            physicalTenantIds);

    return new Broker(systemContext, new SpringBrokerBridge(), List.of());
  }
}
