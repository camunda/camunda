/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class PhysicalTenantProvisioningInitializerTest {

  private static final MemberId LOCAL_MEMBER_ID = MemberId.from("0");

  private final DynamicPartitionConfig partitionConfig =
      new DynamicPartitionConfig(
          new ExportingConfig(
              ExportingState.EXPORTING,
              Map.of("expA", new ExporterState(1, ExporterState.State.ENABLED, Optional.empty()))));

  @Test
  void shouldProvisionANewPhysicalTenantNotYetInTheConfiguration() {
    // given — tenantA already exists; the static configuration additionally lists tenantB, which
    // has no partition group yet
    final var existingTenantA =
        Set.of(
            partition("tenantA", 1, Set.of(member(0), member(1)), member(0)),
            partition("tenantA", 2, Set.of(member(0), member(1)), member(1)));
    final var configuration = configurationWith(existingTenantA);
    final int tenantBPartitionCount = 2;
    final var staticConfiguration =
        staticConfigWith(
            List.of(
                tenantPartitionIds("tenantA", 2),
                tenantPartitionIds("tenantB", tenantBPartitionCount)),
            2);
    final var initializer = new PhysicalTenantProvisioningInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then — tenantB now has an empty-but-provisioned group with a pending change to place its
    // partitions, while tenantA is completely untouched
    assertThat(result.partitionGroups()).containsKey("tenantB");
    assertThat(result.partitionGroup("tenantB").routingState())
        .hasValueSatisfying(
            routingState -> {
              assertThat(routingState.messageCorrelation())
                  .isEqualTo(new MessageCorrelation.HashMod(tenantBPartitionCount));
              assertThat(routingState.requestHandling())
                  .isEqualTo(new AllPartitions(tenantBPartitionCount));
            });
    assertThat(result.partitionGroup("tenantB").hasPendingChanges()).isTrue();
    assertThat(result.partitionGroups().get("tenantA"))
        .isEqualTo(configuration.partitionGroups().get("tenantA"));
  }

  @Test
  void shouldNotChangeAnythingWhenNoNewPhysicalTenantExists() {
    // given — static configuration matches the current configuration exactly
    final var existingTenantA =
        Set.of(partition("tenantA", 1, Set.of(member(0), member(1)), member(0)));
    final var configuration = configurationWith(existingTenantA);
    final var staticConfiguration = staticConfigWith(List.of(tenantPartitionIds("tenantA", 1)));
    final var initializer = new PhysicalTenantProvisioningInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldNotTouchAGroupWhosePhysicalTenantWasRemovedFromStaticConfiguration() {
    // given — tenantA exists in the configuration, but the static configuration no longer lists it
    final var existingTenantA =
        Set.of(partition("tenantA", 1, Set.of(member(0), member(1)), member(0)));
    final var configuration = configurationWith(existingTenantA);
    final var staticConfiguration = staticConfigWith(List.of());
    final var initializer = new PhysicalTenantProvisioningInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then — tenantA is left exactly as-is, no removal attempted
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldProvisionMultipleNewPhysicalTenantsInOnePass() {
    // given — no existing groups at all; static configuration lists two brand-new tenants
    final var configuration = configurationWith(Set.of());
    final var staticConfiguration =
        staticConfigWith(
            List.of(tenantPartitionIds("tenantA", 2), tenantPartitionIds("tenantB", 2)));
    final var initializer = new PhysicalTenantProvisioningInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then
    assertThat(result.partitionGroups()).containsKeys("tenantA", "tenantB");
    assertThat(result.partitionGroup("tenantA").hasPendingChanges()).isTrue();
    assertThat(result.partitionGroup("tenantB").hasPendingChanges()).isTrue();
  }

  @Test
  void shouldBalanceNewTenantsAgainstEachOtherNotJustAgainstExistingLoad() {
    // given — no existing groups at all, so every member starts equally (un)loaded; two brand-new
    // single-partition tenants are provisioned in the same pass
    final var configuration = configurationWith(Set.of());
    final var staticConfiguration =
        staticConfigWith(
            List.of(tenantPartitionIds("tenantA", 1), tenantPartitionIds("tenantB", 1)));
    final var initializer = new PhysicalTenantProvisioningInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then — tenantA's and tenantB's sole partitions land on different members. If each tenant's
    // placement were computed in its own separate reassignPartitions call (rather than one joint
    // call covering both), neither call would see the other's still-JOINING partition as load, and
    // both would independently pick the very same least-loaded member — piling both brand-new
    // tenants onto the same broker instead of spreading them out.
    final var tenantAOperations =
        result.partitionGroup("tenantA").pendingChanges().orElseThrow().pendingOperations();
    final var tenantBOperations =
        result.partitionGroup("tenantB").pendingChanges().orElseThrow().pendingOperations();
    final var tenantATarget = bootstrapTargetMember(tenantAOperations);
    final var tenantBTarget = bootstrapTargetMember(tenantBOperations);
    assertThat(tenantATarget).isNotEqualTo(tenantBTarget);
  }

  @Test
  void shouldNotPlaceNewTenantPartitionsOnLeftOrUninitializedMembers() {
    // given — members 0-2 are live (ACTIVE) but already loaded with tenantX's partitions; member
    // 8 has never joined (UNINITIALIZED) and member 9 has left the cluster (LEFT) — both are still
    // present in globalConfiguration().members(), just not live, and both are otherwise completely
    // unloaded. If they weren't excluded from the candidate set, the reassigner's least-loaded
    // tie-break would pick one of them over the more-loaded but live members 0-2.
    final var existingTenantX =
        Set.of(
            partition("tenantX", 1, Set.of(member(0)), member(0)),
            partition("tenantX", 2, Set.of(member(1)), member(1)),
            partition("tenantX", 3, Set.of(member(2)), member(2)));
    final var uninitializedMember = member(8);
    final var leftMember = member(9);
    final var baseConfiguration = configurationWith(existingTenantX);
    final var configuration =
        baseConfiguration.updateGlobalConfiguration(
            global ->
                global
                    .addMember(uninitializedMember, BrokerState.uninitialized())
                    .addMember(
                        leftMember,
                        BrokerState.initializeAsActive().setState(BrokerState.State.LEFT)));
    final var staticConfiguration = staticConfigWith(List.of(tenantPartitionIds("tenantA", 1)), 1);
    final var initializer = new PhysicalTenantProvisioningInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then — the new tenant's sole partition still lands on one of the loaded-but-live members
    // 0-2, never on the unloaded-but-not-live members 8/9
    final var operations =
        result.partitionGroup("tenantA").pendingChanges().orElseThrow().pendingOperations();
    final var targetedMembers =
        operations.stream()
            .map(ClusterConfigurationChangeOperation::memberId)
            .collect(Collectors.toSet());
    assertThat(targetedMembers).doesNotContain(uninitializedMember, leftMember);
  }

  @Test
  void shouldSkipTheWholeBatchWhenReassignmentFailsAndRetryLater() {
    // given — replication factor 5, but the live cluster only has 2 members, so no valid
    // placement exists for tenantA at all
    final var configuration = configurationWith(Set.of());
    final var staticConfiguration =
        new StaticConfiguration(
            new RoundRobinPartitionDistributor(),
            Set.of(member(0), member(1)),
            LOCAL_MEMBER_ID,
            List.of(new PartitionId("tenantA", 1), new PartitionId("tenantA", 2)),
            5,
            Map.of("tenantA", partitionConfig),
            "clusterId");
    final var initializer = new PhysicalTenantProvisioningInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then — the joint reassignment computation throws (only 2 live members, RF 5), so the whole
    // batch is skipped and the configuration is returned unchanged instead of propagating the
    // exception out of modify()
    assertThat(result).isEqualTo(configuration);
  }

  @Test
  void shouldPlaceANewTenantOnAZoneAwareClusterByCurrentLoadNotByRoundRobinIndex() {
    // given — a zone-aware cluster (zone-a: 1 replica/priority 100, zone-b: 1 replica/priority 50,
    // RF 2). tenantA already has 2 partitions, both replicated onto zone-a_0, so zone-a_0 carries
    // twice the load of zone-a_1/zone-a_2 there — a skew that only the actual current distribution
    // shows, not the partition count or ordering. tenantZ (sorted AFTER tenantA, so it lands at
    // list index 2) is the new tenant being provisioned.
    //
    // The previous implementation delegated to the full ZoneAwarePartitionDistributor, which
    // recomputes purely from (partition's position in the sorted target list) modulo (zone broker
    // count) — completely blind to zone-a_0's actual extra load. For tenantZ at index 2 with 3
    // zone-a brokers, that formula picks zone-a_2. The reassigner under test instead picks
    // zone-a's actual least-loaded member, zone-a_1.
    final var zoneA0 = MemberId.from("zone-a", 0);
    final var zoneA1 = MemberId.from("zone-a", 1);
    final var zoneB0 = MemberId.from("zone-b", 0);
    final var zoneB1 = MemberId.from("zone-b", 1);
    final var existingTenantA =
        Set.of(
            partition("tenantA", 1, Set.of(zoneA0, zoneB0), zoneA0),
            partition("tenantA", 2, Set.of(zoneA0, zoneB1), zoneA0));
    final var zoneSpecs = List.of(new ZoneSpec("zone-a", 1, 100), new ZoneSpec("zone-b", 1, 50));
    final var configuration =
        configurationWithMembers(
                existingTenantA, Set.of(zoneA0, zoneA1, MemberId.from("zone-a", 2), zoneB0, zoneB1))
            .updateGlobalConfiguration(
                global -> global.setPartitionDistributorConfig(new ZoneAwareConfig(zoneSpecs)));
    final var staticConfiguration =
        staticConfigWith(
            List.of(tenantPartitionIds("tenantA", 2), tenantPartitionIds("tenantZ", 1)), 2);
    final var initializer = new PhysicalTenantProvisioningInitializer(staticConfiguration);

    // when
    final var result = initializer.modify(configuration).join();

    // then — tenantZ's zone-a replica lands on the actually-least-loaded zone-a_1, not on
    // zone-a_2 (which a from-scratch, index-based recomputation would have chosen instead)
    final var tenantZOperations =
        result.partitionGroup("tenantZ").pendingChanges().orElseThrow().pendingOperations();
    final var tenantZTargetMembers =
        tenantZOperations.stream().map(ClusterConfigurationChangeOperation::memberId).toList();
    final var tenantZZoneAMember =
        tenantZTargetMembers.stream().filter(m -> m.isInZone("zone-a")).findFirst().orElseThrow();
    assertThat(tenantZZoneAMember).isEqualTo(zoneA1);
  }

  private StaticConfiguration staticConfigWith(final List<List<PartitionId>> tenantPartitionIds) {
    return staticConfigWith(tenantPartitionIds, 1);
  }

  private StaticConfiguration staticConfigWith(
      final List<List<PartitionId>> tenantPartitionIds, final int replicationFactor) {
    final List<PartitionId> allPartitionIds =
        tenantPartitionIds.stream().flatMap(List::stream).toList();
    final var tenantConfigs =
        tenantPartitionIds.stream()
            .map(list -> list.get(0).group())
            .collect(Collectors.toMap(Function.identity(), list -> partitionConfig));
    return new StaticConfiguration(
        new RoundRobinPartitionDistributor(),
        Set.of(member(0), member(1), member(2)),
        LOCAL_MEMBER_ID,
        allPartitionIds,
        replicationFactor,
        tenantConfigs,
        "clusterId");
  }

  private MemberId bootstrapTargetMember(
      final List<ClusterConfigurationChangeOperation> operations) {
    return operations.stream()
        .filter(op -> op instanceof PartitionBootstrapOperation)
        .map(op -> ((PartitionBootstrapOperation) op).memberId())
        .findFirst()
        .orElseThrow();
  }

  private List<PartitionId> tenantPartitionIds(final String tenantId, final int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(number -> new PartitionId(tenantId, number))
        .toList();
  }

  private CurrentClusterConfiguration configurationWith(final Set<PartitionMetadata> existing) {
    final Set<MemberId> members = new HashSet<>();
    existing.forEach(metadata -> members.addAll(metadata.members()));
    // ensure the configuration always has members 0-2 available as live cluster members
    for (int i = 0; i < 3; i++) {
      members.add(member(i));
    }
    return configurationWithMembers(existing, members);
  }

  private CurrentClusterConfiguration configurationWithMembers(
      final Set<PartitionMetadata> existing, final Set<MemberId> members) {
    final var tenantConfigs =
        existing.stream()
            .map(p -> p.id().group())
            .distinct()
            .collect(Collectors.toMap(Function.identity(), group -> partitionConfig));
    return ConfigurationUtil.getCurrentClusterConfigurationFrom(
        members, existing, tenantConfigs, "clusterId");
  }

  /**
   * Builds a partition with a real, non-tied priority ladder (primary gets {@code members.size()},
   * every other member gets a strictly lower, distinct priority) so round-tripping through {@link
   * ConfigurationUtil} preserves the given primary exactly.
   */
  private PartitionMetadata partition(
      final String group, final int number, final Set<MemberId> members, final MemberId primary) {
    final Map<MemberId, Integer> priorities = new HashMap<>();
    priorities.put(primary, members.size());
    int nextPriority = members.size() - 1;
    for (final var member : members.stream().sorted().toList()) {
      if (!member.equals(primary)) {
        priorities.put(member, nextPriority--);
      }
    }
    return new PartitionMetadata(
        new PartitionId(group, number), members, priorities, priorities.get(primary), primary);
  }

  private MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
