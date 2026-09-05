/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration.DEFAULT_GROUP;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.BARE_0;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.BARE_1;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.BARE_2;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_A;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_A_0;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_B;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_B_0;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ForceRemoveBrokersRequestTransformer;
import io.camunda.zeebe.dynamic.config.api.ForceRemoveZoneTransformer;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinatorImpl;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor.NoopRestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A forced removal is what an operator reaches for once brokers or a whole zone are already gone,
 * so it has to reconfigure every physical tenant's partitions and not only the default tenant's.
 * Force-reconfiguring the default tenant alone left the departing brokers replicating the other
 * tenants' partitions, and the member removal that follows — which checks every partition group —
 * then refused the whole plan during validation, leaving the configuration untouched. A cluster
 * with more than one tenant could not use its recovery paths at all.
 *
 * <p>Driven through the coordinator rather than the transformers, because the refusal came from the
 * appliers rather than from the plan: the coordinator runs the whole plan through the real ones —
 * the member leave among them — and hands back the configuration the request would leave behind.
 */
final class PhysicalTenantForcedRemovalTest {

  private static final String TENANT_A = "tenanta";
  private static final int PARTITION_ID = 1;
  private static final ZoneAwareConfig DUAL_ZONE =
      new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000), new ZoneSpec(ZONE_B, 1, 500)));

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @TempDir private Path tmp;

  private ClusterConfigurationManagerImpl manager;
  private ConfigurationChangeCoordinatorImpl coordinator;

  /**
   * The two tenants are placed differently on purpose: broker 2 holds nothing of the default tenant
   * and only tenant A's partition, which is the case a plan that stops at the default group has
   * nothing to say about.
   */
  @Test
  void shouldForceRemoveABrokerFromEveryPhysicalTenant() {
    // given — three brokers running two tenants; broker 2 holds only tenant A's partition
    wire(
        BARE_0,
        twoTenants(
            cluster(Set.of(BARE_0, BARE_1, BARE_2), Set.of(BARE_0, BARE_1)),
            cluster(Set.of(BARE_0, BARE_1, BARE_2), Set.of(BARE_1, BARE_2))));

    // when — broker 2 is gone
    final var configuration =
        forceRemove(new ForceRemoveBrokersRequestTransformer(Set.of(BARE_2), BARE_0));

    // then — tenant A keeps its partition on the broker that survives, the default tenant is
    // untouched, and broker 2 has left
    assertThat(replicasOf(configuration, DEFAULT_GROUP))
        .describedAs("replicas of the default tenant's partition")
        .containsExactlyInAnyOrder(BARE_0, BARE_1);
    assertThat(replicasOf(configuration, TENANT_A))
        .describedAs("replicas of tenant '%s' partition", TENANT_A)
        .containsExactly(BARE_1);
    assertThat(configuration.globalConfiguration().members())
        .describedAs("brokers left in the cluster")
        .containsOnlyKeys(BARE_0, BARE_1);
  }

  @Test
  void shouldForceRemoveAZoneFromEveryPhysicalTenant() {
    // given — two zones of one broker each, both tenants replicated across both
    final var members = Set.of(ZONE_A_0, ZONE_B_0);
    wire(
        ZONE_A_0,
        twoTenants(
            cluster(members, members).setPartitionDistributorConfig(DUAL_ZONE),
            cluster(members, members)));

    // when — zone-b fails over
    final var configuration = forceRemove(new ForceRemoveZoneTransformer(ZONE_B));

    // then — every tenant is left on the surviving zone, which is also the only one the persisted
    // layout still names
    assertThat(replicasOf(configuration, DEFAULT_GROUP))
        .describedAs("replicas of the default tenant's partition")
        .containsExactly(ZONE_A_0);
    assertThat(replicasOf(configuration, TENANT_A))
        .describedAs("replicas of tenant '%s' partition", TENANT_A)
        .containsExactly(ZONE_A_0);
    assertThat(configuration.globalConfiguration().members())
        .describedAs("brokers left in the cluster")
        .containsOnlyKeys(ZONE_A_0);
    assertThat(configuration.globalConfiguration().partitionDistributorConfig())
        .contains(new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000))));
  }

  /**
   * Runs the request and answers with the configuration it produces.
   *
   * <p>A dry run rather than a real apply: applying would drive each operation on the broker that
   * is named by it, and the brokers a forced removal is aimed at are by definition not running. The
   * dry run still puts the whole plan through the real appliers — the member leave that used to
   * refuse it among them — so a plan that misses a tenant fails here exactly as it does in a real
   * cluster.
   */
  private CurrentClusterConfiguration forceRemove(final ConfigurationChangeRequest request) {
    return coordinator.simulateOperations(request).join().finalMultiConfiguration();
  }

  /** The brokers replicating the given physical tenant's only partition. */
  private Set<MemberId> replicasOf(
      final CurrentClusterConfiguration configuration, final String physicalTenantId) {
    return configuration.partitionGroup(physicalTenantId).members().entrySet().stream()
        .filter(member -> member.getValue().partitions().containsKey(PARTITION_ID))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  /** A cluster of {@code members} in which {@code replicas} replicate partition 1. */
  private ClusterConfiguration cluster(final Set<MemberId> members, final Set<MemberId> replicas) {
    var topology = ClusterConfiguration.init();
    for (final var member : members) {
      topology = topology.addMember(member, MemberState.initializeAsActive(Map.of()));
    }
    var priority = replicas.size();
    for (final var replica : replicas) {
      final var state = PartitionState.active(priority--, partitionConfig);
      topology = topology.updateMember(replica, member -> member.addPartition(PARTITION_ID, state));
    }
    return topology;
  }

  /**
   * The two topologies as one configuration running two physical tenants. Both have to describe the
   * same brokers — the member set is cluster-wide, and only the partition assignment differs per
   * tenant.
   */
  private CurrentClusterConfiguration twoTenants(
      final ClusterConfiguration defaultTenant, final ClusterConfiguration otherTenant) {
    final var base = CurrentClusterConfiguration.fromLegacy(defaultTenant);
    return new CurrentClusterConfiguration(
        base.version(),
        base.globalConfiguration(),
        Map.of(
            DEFAULT_GROUP,
            base.partitionGroup(DEFAULT_GROUP),
            TENANT_A,
            CurrentClusterConfiguration.fromLegacy(otherTenant).partitionGroup(DEFAULT_GROUP)),
        base.phasedChangeState());
  }

  private void wire(final MemberId localMemberId, final CurrentClusterConfiguration seed) {
    manager =
        new ClusterConfigurationManagerImpl(
            executor,
            localMemberId,
            PersistedCurrentClusterConfiguration.ofFile(
                tmp.resolve("config.meta"), new ProtoBufSerializer()),
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));
    seed.partitionGroups()
        .keySet()
        .forEach(
            groupId ->
                manager.registerPartitionGroupChangeAppliers(
                    groupId,
                    new PartitionGroupConfigurationChangeAppliersImpl(
                        new NoopPartitionChangeExecutor(),
                        new NoopPartitionScalingChangeExecutor(),
                        new NoopModeChangeExecutor(),
                        new NoopRestoreChangeExecutor())));
    coordinator = new ConfigurationChangeCoordinatorImpl(manager, localMemberId, executor);
    manager.updateMultiConfiguration(ignored -> seed).join();
  }
}
