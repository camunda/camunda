/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.BARE_0;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.BARE_2;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.DUAL_REGION;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_A;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_A_0;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_A_1;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_B;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_B_0;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.ZONE_B_1;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.api.ZoneMigrationRequestTransformer;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A zone migration stage replaces every broker of one zone, so it has to move every physical
 * tenant's partitions and not only the default tenant's. Planning the default group alone left the
 * other tenants' partitions on the very brokers the stage removes, and the coordinator then refused
 * the whole change while validating the member leave — a cluster with more than one tenant could
 * not become zone-aware at all.
 *
 * <p>Driven through the coordinator rather than the transformer, because that is what makes the two
 * stages real: the coordinator runs each stage's plan through the actual appliers — including the
 * member leave that used to refuse it — and hands back the configuration the stage would leave
 * behind, which is what the next stage is then planned against.
 */
final class PhysicalTenantZoneMigrationTest {

  private static final String TENANT_A = "tenanta";
  private static final int PARTITIONS_PER_TENANT = 2;
  private static final int REPLICATION_FACTOR = 4;

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @TempDir private Path tmp;

  private ClusterConfigurationManagerImpl manager;
  private ConfigurationChangeCoordinatorImpl coordinator;

  @Test
  void shouldMigrateEveryPhysicalTenantZoneByZone() {
    // given — four bare brokers running two tenants, with the dual-region plan persisted. Brokers 1
    // and 3 belong to zone-b under that plan, brokers 0 and 2 to zone-a.
    wire(BARE_0, twoTenantBareCluster());

    // when — zone-b migrates first, as the plan requires
    migrate(ZONE_B);

    // then — both tenants run on zone-b's new brokers and on the brokers zone-a has yet to replace
    assertEveryTenantRunsOn(Set.of(BARE_0, BARE_2, ZONE_B_0, ZONE_B_1));

    // when — zone-a follows
    migrate(ZONE_A);

    // then — no bare broker is left holding a partition of any tenant
    assertEveryTenantRunsOn(Set.of(ZONE_A_0, ZONE_A_1, ZONE_B_0, ZONE_B_1));
  }

  /**
   * Runs one stage and leaves the cluster in the configuration it produced, so the next stage is
   * planned against it.
   *
   * <p>A dry run rather than a real apply: applying would need every member of the plan to be
   * running, since each operation is executed by the broker it names, and a stage introduces
   * brokers that do not exist yet. The dry run still puts the whole plan through the real appliers
   * — the member leave among them — and returns the configuration it produced.
   */
  private void migrate(final String zone) {
    final var result =
        coordinator.simulateOperations(new ZoneMigrationRequestTransformer(zone)).join();
    manager.updateMultiConfiguration(ignored -> result.finalMultiConfiguration()).join();
  }

  /**
   * Asserts that every tenant's partitions are replicated over exactly {@code expectedMembers}.
   * With the replication factor equal to the cluster size every broker holds every partition, so a
   * tenant that was left out of a stage shows up as a broker that should have been replaced still
   * holding partitions.
   */
  private void assertEveryTenantRunsOn(final Set<MemberId> expectedMembers) {
    final var configuration = manager.getMultiConfiguration().join();
    assertThat(configuration.globalConfiguration().members())
        .describedAs("brokers left in the cluster")
        .containsOnlyKeys(expectedMembers);
    assertThat(configuration.partitionGroups())
        .containsOnlyKeys(CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_A)
        .allSatisfy(
            (groupId, group) ->
                assertThat(
                        group.members().entrySet().stream()
                            .filter(member -> !member.getValue().partitions().isEmpty())
                            .collect(
                                Collectors.toMap(
                                    Map.Entry::getKey,
                                    member -> member.getValue().partitions().keySet())))
                    .describedAs("partitions of physical tenant '%s' per broker", groupId)
                    .hasSize(REPLICATION_FACTOR)
                    .containsOnlyKeys(expectedMembers)
                    .allSatisfy(
                        (member, partitions) ->
                            assertThat(partitions).hasSize(PARTITIONS_PER_TENANT)));
  }

  /**
   * Both tenants deliberately run the same partitions over the same brokers: what distinguishes a
   * plan that covered every tenant from one that only saw the default group is then which brokers
   * are left holding partitions, not how the two tenants differ.
   */
  private CurrentClusterConfiguration twoTenantBareCluster() {
    final var legacy =
        unzonedTopology(4, PARTITIONS_PER_TENANT, REPLICATION_FACTOR)
            .setPartitionDistributorConfig(new ZoneAwareConfig(DUAL_REGION));
    final var single = CurrentClusterConfiguration.fromLegacy(legacy);
    return new CurrentClusterConfiguration(
        single.version(),
        single.globalConfiguration(),
        Map.of(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            single.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP),
            TENANT_A,
            single.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)),
        single.phasedChangeState());
  }

  private ClusterConfiguration unzonedTopology(
      final int clusterSize, final int partitionCount, final int replicationFactor) {
    final Set<MemberId> members =
        IntStream.range(0, clusterSize).mapToObj(MemberId::from).collect(Collectors.toSet());
    final var distribution =
        new RoundRobinConfig()
            .toDistributor()
            .distributePartitions(
                members,
                IntStream.rangeClosed(1, partitionCount)
                    .mapToObj(
                        number ->
                            new PartitionId(CurrentClusterConfiguration.DEFAULT_GROUP, number))
                    .toList(),
                replicationFactor);
    var topology =
        ConfigurationUtil.getClusterConfigFrom(distribution, partitionConfig, "clusterId");
    for (final MemberId member : members) {
      if (!topology.hasMember(member)) {
        topology = topology.addMember(member, MemberState.initializeAsActive(Map.of()));
      }
    }
    return topology;
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
