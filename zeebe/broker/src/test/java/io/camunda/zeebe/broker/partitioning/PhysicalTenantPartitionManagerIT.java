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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.AtomixCluster;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.client.api.BrokerClient;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

  @Test
  void shouldFailToBootWhenNonDefaultPhysicalTenantHasInvalidConfiguration(
      @TempDir final Path tmp) {
    // given — a non-default physical tenant with its own BrokerCfg carrying an invalid snapshot
    // period, which SystemContext.validateConfiguration() must catch during construction, before
    // the broker ever starts
    final var rootCfg = new BrokerCfg();
    assignSocketAddresses(rootCfg);
    rootCfg.getCluster().setClusterSize(1);
    rootCfg.getCluster().setNodeId(0);
    rootCfg.getCluster().setPartitionsCount(1);
    rootCfg.getCluster().setReplicationFactor(1);
    rootCfg.init(tmp.toAbsolutePath().toString());

    final var tenant2Cfg = new BrokerCfg();
    assignSocketAddresses(tenant2Cfg);
    tenant2Cfg.getCluster().setClusterSize(1);
    tenant2Cfg.getCluster().setNodeId(0);
    tenant2Cfg.getCluster().setPartitionsCount(1);
    tenant2Cfg.getCluster().setReplicationFactor(1);
    tenant2Cfg.getData().setSnapshotPeriod(Duration.ofSeconds(1));
    tenant2Cfg.init(tmp.toAbsolutePath().toString());

    // when / then — the guardrail must hold through the same construction sequence the real boot
    // path uses (SystemContext followed by the Broker that wraps it and would otherwise start()),
    // not merely the SystemContext seam in isolation.
    assertThatThrownBy(
            () -> {
              final var actorScheduler = TestActorSchedulerFactory.ofBrokerConfig(rootCfg);
              AtomixCluster atomixCluster = null;
              BrokerClient brokerClient = null;
              try {
                atomixCluster = TestClusterFactory.createAtomixCluster(rootCfg, METER_REGISTRY);
                brokerClient =
                    TestBrokerClientFactory.createBrokerClient(atomixCluster, actorScheduler);
                final var systemContext =
                    SystemContextTestFactory.multiTenant(
                        SystemContext.DEFAULT_SHUTDOWN_TIMEOUT,
                        rootCfg,
                        Map.of(
                            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                            rootCfg,
                            "tenant2",
                            tenant2Cfg),
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
                        NodeIdProvider.staticProvider(rootCfg.getCluster().getNodeId()),
                        () -> Set.of(PartitionManagerImpl.DEFAULT_GROUP_NAME, "tenant2"));
                try (final var broker =
                    new Broker(systemContext, new SpringBrokerBridge(), List.of())) {
                  broker.start().join();
                }
              } finally {
                if (brokerClient != null) {
                  brokerClient.close();
                }
                if (atomixCluster != null) {
                  atomixCluster.stop().join();
                }
                actorScheduler.close();
              }
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Snapshot period PT1S needs to be larger")
        .hasMessageContaining("physical tenant 'tenant2'");
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
