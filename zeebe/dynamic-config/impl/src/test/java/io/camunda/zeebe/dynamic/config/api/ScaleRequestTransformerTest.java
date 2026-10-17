/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.api.TestChangePlan.plannedOperations;
import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.withMirroredTenant;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.EdgeCasesMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScaleRequestTransformerTest {

  private static final String TENANT_A = "tenant-a";

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  private static Set<MemberId> members(final int count) {
    return IntStream.range(0, count)
        .mapToObj(Integer::toString)
        .map(MemberId::from)
        .collect(Collectors.toSet());
  }

  private static List<PartitionId> sortedPartitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(id -> new PartitionId(CurrentClusterConfiguration.DEFAULT_GROUP, id))
        .toList();
  }

  @Property(tries = 10)
  void shouldScaleAndReassignWithReplicationFactor1(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 1, max = 100) final int newClusterSize) {
    shouldScaleAndReassign(partitionCount, 1, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10)
  void shouldScaleAndReassignWithReplicationFactor2(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 2, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 2, max = 100) final int newClusterSize) {
    shouldScaleAndReassign(partitionCount, 2, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF, edgeCases = EdgeCasesMode.NONE)
  void shouldScaleAndReassignWithReplicationFactor3(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 3, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 3, max = 100) final int newClusterSize) {
    shouldScaleAndReassign(partitionCount, 3, oldClusterSize, newClusterSize);
  }

  @Property(tries = 10)
  void shouldScaleAndReassignWithReplicationFactor4(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 4, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 4, max = 100) final int newClusterSize) {
    shouldScaleAndReassign(partitionCount, 4, oldClusterSize, newClusterSize);
  }

  @Property
  void shouldFailIfClusterSizeLessThanReplicationFactor3(
      @ForAll @IntRange(min = 0, max = 2) final int newClusterSize) {
    shouldFailIfClusterSizeLessThanReplicationFactor(3, 3, 3, newClusterSize);
  }

  @Property
  void shouldFailIfClusterSizeLessThanReplicationFactor4(
      @ForAll @IntRange(min = 0, max = 3) final int newClusterSize) {
    shouldFailIfClusterSizeLessThanReplicationFactor(12, 4, 6, newClusterSize);
  }

  @Property
  void shouldFailIfDesiredPartitionCountIsLessThanNewPartitions(
      @ForAll @IntRange(min = 1, max = 100) final int currentPartitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int desiredPartitionCount) {
    scaleUpWithValidation(currentPartitionCount, desiredPartitionCount, null);
  }

  @Property
  void shouldGenerateScaleUpOperationForAllPartition1Members(
      @ForAll @IntRange(min = 1, max = 100) final int currentPartitionCount,
      @ForAll @IntRange(min = 1, max = 100) final int newPartitionCount) {
    final var desiredPartitionCount = currentPartitionCount + newPartitionCount;
    scaleUpWithValidation(
        currentPartitionCount,
        desiredPartitionCount,
        operations -> {
          final var lowestMemberId = MemberId.from("1");
          final var newPartitions =
              partitionsInRange(currentPartitionCount + 1, desiredPartitionCount + 1);
          AssertionsForInterfaceTypes.assertThat(
                  operations.stream().filter(ScaleUpOperation.class::isInstance))
              .isEqualTo(
                  List.of(
                      new StartPartitionScaleUp(lowestMemberId, desiredPartitionCount),
                      new AwaitRedistributionCompletion(
                          lowestMemberId, desiredPartitionCount, newPartitions),
                      new AwaitRelocationCompletion(
                          lowestMemberId, desiredPartitionCount, newPartitions)));
        });
  }

  void shouldFailIfClusterSizeLessThanReplicationFactor(
      final int partitionCount,
      final int replicationFactor,
      final int oldClusterSize,
      final int newClusterSize) {
    shouldFailIfClusterSizeLessThanReplicationFactor(
        partitionCount,
        replicationFactor,
        null,
        getClusterMembers(oldClusterSize),
        getClusterMembers(newClusterSize));
  }

  void shouldFailIfClusterSizeLessThanReplicationFactor(
      final int partitionCount,
      final int replicationFactor,
      final PartitionDistributorConfig config,
      final Set<MemberId> oldMembers,
      final Set<MemberId> newMembers) {
    // given
    final PartitionDistributor distributor =
        config != null ? config.toDistributor() : new RoundRobinPartitionDistributor();
    final var oldDistribution =
        distributor.distributePartitions(
            oldMembers, getSortedPartitionIds(partitionCount), replicationFactor);
    var oldClusterTopology =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            oldMembers,
            oldDistribution,
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, partitionConfig),
            "clusterId");
    if (config != null) {
      oldClusterTopology =
          oldClusterTopology.updateGlobalConfiguration(
              globalConfiguration -> globalConfiguration.setPartitionDistributorConfig(config));
    }

    // when
    final var operationsEither =
        plannedOperations(new ScaleRequestTransformer(newMembers), oldClusterTopology);

    // then
    EitherAssert.assertThat(operationsEither)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }

  void shouldScaleAndReassign(
      final int partitionCount,
      final int replicationFactor,
      final int oldClusterSize,
      final int newClusterSize) {
    shouldScaleAndReassign(
        partitionCount,
        replicationFactor,
        null,
        getClusterMembers(oldClusterSize),
        getClusterMembers(newClusterSize));
  }

  void shouldScaleAndReassign(
      final int partitionCount,
      final int replicationFactor,
      final PartitionDistributorConfig config,
      final Set<MemberId> oldMembers,
      final Set<MemberId> newMembers) {
    // given
    final PartitionDistributor distributor =
        config != null ? config.toDistributor() : new RoundRobinPartitionDistributor();
    final var expectedNewDistribution =
        distributor.distributePartitions(
            newMembers, getSortedPartitionIds(partitionCount), replicationFactor);

    final var oldDistribution =
        distributor.distributePartitions(
            oldMembers, getSortedPartitionIds(partitionCount), replicationFactor);
    var oldClusterTopology =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            oldMembers,
            oldDistribution,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionConfig),
            "clusterId");
    if (config != null) {
      oldClusterTopology =
          oldClusterTopology.updateGlobalConfiguration(
              globalConfiguration -> globalConfiguration.setPartitionDistributorConfig(config));
    }

    // when
    final var phases = new ScaleRequestTransformer(newMembers).phases(oldClusterTopology).get();

    // apply phases to generate new topology
    final var newTopology = TestTopologyChangeSimulator.apply(oldClusterTopology, phases);

    // then
    final var newDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(
            newTopology, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(newDistribution).isEqualTo(expectedNewDistribution);
    assertThat(newTopology.getMembers()).containsExactlyInAnyOrderElementsOf(newMembers);
  }

  @Test
  void shouldScaleOutZoneAwareCluster() {
    // given: 2 zones, RF=3 (zone-a contributes 2 replicas, zone-b contributes 1)
    final var config =
        new PartitionDistributorConfig.ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500)));

    // old cluster: 2 zone-a brokers + 1 zone-b broker
    final var oldMembers =
        Set.of(MemberId.from("zone-a", 0), MemberId.from("zone-a", 1), MemberId.from("zone-b", 0));

    // new cluster: 4 zone-a brokers + 2 zone-b brokers
    final var newMembers =
        Set.of(
            MemberId.from("zone-a", 0),
            MemberId.from("zone-a", 1),
            MemberId.from("zone-a", 2),
            MemberId.from("zone-a", 3),
            MemberId.from("zone-b", 0),
            MemberId.from("zone-b", 1));

    shouldScaleAndReassign(3, 3, config, oldMembers, newMembers);
  }

  @Test
  void shouldScaleInZoneAwareCluster() {
    // given: 2 zones, RF=3 (zone-a contributes 2 replicas, zone-b contributes 1)
    final var config =
        new PartitionDistributorConfig.ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500)));

    // old cluster: 4 zone-a brokers + 2 zone-b brokers
    final var oldMembers =
        Set.of(
            MemberId.from("zone-a", 0),
            MemberId.from("zone-a", 1),
            MemberId.from("zone-a", 2),
            MemberId.from("zone-a", 3),
            MemberId.from("zone-b", 0),
            MemberId.from("zone-b", 1));

    // new cluster: 2 zone-a brokers + 1 zone-b broker (minimal)
    final var newMembers =
        Set.of(MemberId.from("zone-a", 0), MemberId.from("zone-a", 1), MemberId.from("zone-b", 0));

    shouldScaleAndReassign(3, 3, config, oldMembers, newMembers);
  }

  private List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(id -> new PartitionId(CurrentClusterConfiguration.DEFAULT_GROUP, id))
        .collect(Collectors.toList());
  }

  private Set<MemberId> getClusterMembers(final int newClusterSize) {
    return IntStream.range(0, newClusterSize)
        .mapToObj(Integer::toString)
        .map(MemberId::from)
        .collect(Collectors.toSet());
  }

  public void scaleUpWithValidation(
      final int currentPartitionCount,
      final int desiredPartitionCount,
      final Consumer<List<ClusterConfigurationChangeOperation>> whenRight) {
    final var clusterSize = 3;
    final var replicationFactor = 3;
    final var members =
        IntStream.range(1, clusterSize)
            .mapToObj(id -> MemberId.from(Integer.toString(id)))
            .collect(Collectors.toSet());
    final var distribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                members,
                partitionsInRange(1, 1 + currentPartitionCount).stream()
                    .map(i -> new PartitionId(CurrentClusterConfiguration.DEFAULT_GROUP, i))
                    .toList(),
                replicationFactor);
    final var config =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            members,
            distribution,
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, partitionConfig),
            "clusterId");
    final var transformer =
        new ScaleRequestTransformer(
            getClusterMembers(clusterSize), Optional.of(3), Optional.of(desiredPartitionCount));
    final var operations = plannedOperations(transformer, config);
    if (desiredPartitionCount < currentPartitionCount) {
      EitherAssert.assertThat(operations).isLeft();
    } else {
      EitherAssert.assertThat(operations).right();
      if (whenRight != null) {
        whenRight.accept(operations.get());
      }
    }
  }

  SortedSet<Integer> partitionsInRange(final int from, final int to) {
    return new TreeSet<>(IntStream.range(from, to).boxed().toList());
  }

  @Nested
  class PhasesAcrossPhysicalTenants {

    /**
     * Two brokers, replication factor 1, and two tenants each running partitions 1-3 spread over
     * both brokers. Six joint partitions over three brokers give the third broker one partition of
     * each tenant, so a plan that only ever saw the default group is plainly distinguishable from
     * one that saw both.
     */
    private CurrentClusterConfiguration twoTenantCluster() {
      return twoTenantCluster(Set.of(MemberId.from("0"), MemberId.from("1")));
    }

    private CurrentClusterConfiguration twoTenantCluster(final Set<MemberId> members) {
      return withMirroredTenant(
          ConfigurationUtil.getCurrentClusterConfigurationFrom(
              members,
              new RoundRobinPartitionDistributor()
                  .distributePartitions(members, sortedPartitionIds(3), 1),
              Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, DynamicPartitionConfig.init()),
              "clusterId"));
    }

    @Test
    void shouldPlacePartitionsOfEveryTenantOnAnAddedBroker() {
      // given
      final var configuration = twoTenantCluster();

      // when — broker 2 joins
      final var phases =
          new ScaleRequestTransformer(
                  Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")))
              .phases(configuration);

      // then — the joining broker receives a partition of both tenants
      final var partitionPhase = partitionPhase(phases);
      assertThat(joinedPartitions(partitionPhase, CurrentClusterConfiguration.DEFAULT_GROUP))
          .describedAs("the default tenant places a partition on broker 2")
          .isNotEmpty()
          .allSatisfy(join -> assertThat(join.memberId()).isEqualTo(MemberId.from("2")));
      assertThat(joinedPartitions(partitionPhase, TENANT_A))
          .describedAs("tenant-a places a partition on broker 2")
          .isNotEmpty()
          .allSatisfy(join -> assertThat(join.memberId()).isEqualTo(MemberId.from("2")));
    }

    @Test
    void shouldMoveEveryTenantsPartitionsOffARemovedBroker() {
      // given
      final var configuration =
          twoTenantCluster(Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")));

      // when — broker 2 leaves
      final var phases =
          new ScaleRequestTransformer(Set.of(MemberId.from("0"), MemberId.from("1")))
              .phases(configuration);

      // then — both tenants give up the partitions broker 2 held, and it receives nothing. The
      // distributor recomputes the whole placement, so partitions also move between the two
      // surviving brokers; what matters is that neither tenant leaves anything behind on the
      // departing one.
      final var partitionPhase = partitionPhase(phases);
      for (final var groupId : List.of(CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_A)) {
        assertThat(leftPartitions(partitionPhase, groupId))
            .describedAs("tenant '%s' moves its partitions off broker 2", groupId)
            .extracting(PartitionLeaveOperation::memberId)
            .contains(MemberId.from("2"));
        assertThat(joinedPartitions(partitionPhase, groupId))
            .describedAs("tenant '%s' places nothing on the departing broker 2", groupId)
            .extracting(PartitionJoinOperation::memberId)
            .doesNotContain(MemberId.from("2"));
      }
      assertThat(phases.get())
          .describedAs("the member leaves only after the partition work")
          .last()
          .isInstanceOf(GlobalPhase.class);
      assertThat(((GlobalPhase) phases.get().getLast()).operations())
          .contains(new MemberLeaveOperation(MemberId.from("2")));
    }

    @Test
    void shouldOrderMemberJoinsBeforeAndMemberLeavesAfterThePartitionWork() {
      // given
      final var configuration =
          twoTenantCluster(Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")));

      // when — broker 3 joins as broker 2 leaves
      final var phases =
          new ScaleRequestTransformer(
                  Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("3")))
              .phases(configuration);

      // then
      EitherAssert.assertThat(phases).isRight();
      assertThat(phases.get())
          .hasSize(3)
          .satisfies(
              list -> {
                assertThat(((GlobalPhase) list.get(0)).operations())
                    .contains(new MemberJoinOperation(MemberId.from("3")))
                    .doesNotContain(new MemberLeaveOperation(MemberId.from("2")));
                assertThat(list.get(1)).isInstanceOf(PartitionGroupPhase.class);
                assertThat(((GlobalPhase) list.get(2)).operations())
                    .contains(new MemberLeaveOperation(MemberId.from("2")))
                    .doesNotContain(new MemberJoinOperation(MemberId.from("3")));
              });
    }

    @Test
    void shouldApplyAReplicationFactorChangeToEveryTenantWhileScaling() {
      // given
      final var configuration = twoTenantCluster();

      // when — broker 2 joins and the replication factor is raised at the same time
      final var phases =
          new ScaleRequestTransformer(
                  Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")),
                  Optional.of(2))
              .phases(configuration);

      // then — every partition of every tenant gains a replica
      final var partitionPhase = partitionPhase(phases);
      assertThat(partitionPhase.groupOperations())
          .containsOnlyKeys(CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_A);
      assertThat(joinedPartitions(partitionPhase, CurrentClusterConfiguration.DEFAULT_GROUP))
          .hasSize(3);
      assertThat(joinedPartitions(partitionPhase, TENANT_A)).hasSize(3);
    }

    @Test
    void shouldPlanASingleGlobalPhaseWhenThereIsNoPartitionWork() {
      // given — one partition per tenant, already sitting where round robin over three brokers puts
      // it, so adding the third broker has nothing to move for either tenant
      final var configuration =
          cluster(
              Set.of(MemberId.from("0"), MemberId.from("1")),
              Map.of(
                  CurrentClusterConfiguration.DEFAULT_GROUP,
                  Map.of(MemberId.from("0"), 1),
                  TENANT_A,
                  Map.of(MemberId.from("1"), 1)));

      // when
      final var phases =
          new ScaleRequestTransformer(
                  Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2")))
              .phases(configuration);

      // then — nothing separates the member join from the scaling callbacks, so one uninterrupted
      // run of global operations is one phase, exactly as toPhases would produce
      EitherAssert.assertThat(phases).isRight();
      assertThat(phases.get()).singleElement().isInstanceOf(GlobalPhase.class);
    }

    /** A configuration with explicit per-tenant placement: group id to member to partition id. */
    private CurrentClusterConfiguration cluster(
        final Set<MemberId> members, final Map<String, Map<MemberId, Integer>> placement) {
      final var brokers =
          members.stream()
              .collect(
                  Collectors.toMap(
                      member -> member,
                      member -> new BrokerState(1, Instant.EPOCH, BrokerState.State.ACTIVE)));
      final var groups =
          placement.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      group ->
                          new PartitionGroupConfiguration(
                              1,
                              0,
                              group.getValue().entrySet().stream()
                                  .collect(
                                      Collectors.toMap(
                                          Map.Entry::getKey,
                                          member ->
                                              BrokerPartitionState.initialize(
                                                  Map.of(
                                                      member.getValue(),
                                                      PartitionState.active(
                                                          1, DynamicPartitionConfig.init()))))),
                              Optional.empty(),
                              Optional.empty(),
                              Optional.empty())));
      return new CurrentClusterConfiguration(
          CurrentClusterConfiguration.INITIAL_VERSION,
          new GlobalConfiguration(
              1, Optional.empty(), brokers, Optional.empty(), Optional.empty(), Optional.empty()),
          groups,
          PhasedChangeState.empty());
    }

    @Test
    void shouldRejectARequestWithoutAnyBroker() {
      // when
      final var phases = new ScaleRequestTransformer(Set.of()).phases(twoTenantCluster());

      // then
      EitherAssert.assertThat(phases)
          .isLeft()
          .left()
          .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
    }

    private PartitionGroupPhase partitionPhase(final Either<Exception, List<Phase>> phases) {
      EitherAssert.assertThat(phases).isRight();
      return (PartitionGroupPhase)
          phases.get().stream()
              .filter(PartitionGroupPhase.class::isInstance)
              .findFirst()
              .orElseThrow(() -> new AssertionError("no partition group phase in " + phases.get()));
    }

    private List<PartitionJoinOperation> joinedPartitions(
        final PartitionGroupPhase phase, final String groupId) {
      return phase.groupOperations().getOrDefault(groupId, List.of()).stream()
          .filter(PartitionJoinOperation.class::isInstance)
          .map(PartitionJoinOperation.class::cast)
          .toList();
    }

    private List<PartitionLeaveOperation> leftPartitions(
        final PartitionGroupPhase phase, final String groupId) {
      return phase.groupOperations().getOrDefault(groupId, List.of()).stream()
          .filter(PartitionLeaveOperation.class::isInstance)
          .map(PartitionLeaveOperation.class::cast)
          .toList();
    }
  }

  @Nested
  class ZoneAwareScaling {
    static final Set<MemberId> SCALED_MEMBERS =
        Set.of(
            MemberId.from("zone-a", 0),
            MemberId.from("zone-a", 1),
            MemberId.from("zone-a", 2),
            MemberId.from("zone-a", 3),
            MemberId.from("zone-b", 0),
            MemberId.from("zone-b", 1));

    static final Set<MemberId> UNSCALED_MEMBERS =
        Set.of(MemberId.from("zone-a", 0), MemberId.from("zone-a", 1), MemberId.from("zone-b", 0));

    static final ZoneAwareConfig ZONE_AWARE_CONFIG =
        new PartitionDistributorConfig.ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500)));

    @Test
    void shouldScaleOutZoneAwareCluster() {
      shouldScaleAndReassign(3, 3, ZONE_AWARE_CONFIG, UNSCALED_MEMBERS, SCALED_MEMBERS);
    }

    @Test
    void shouldScaleInZoneAwareCluster() {
      shouldScaleAndReassign(3, 3, ZONE_AWARE_CONFIG, SCALED_MEMBERS, UNSCALED_MEMBERS);
    }

    @Test
    void shouldUseLowestMemberOfScalingZoneAsCoordinator() {
      // given: cluster with zone-a and zone-b, scaling only zone-b from 1 to 2 brokers.
      // The default coordinator would be zone-a_0 (lowest overall), but the pre/post scaling
      // callbacks must run on a broker in zone-b so they operate on zone-b's node-id state.
      final Set<MemberId> newMembers = new HashSet<>(UNSCALED_MEMBERS);
      newMembers.add(MemberId.from("zone-b", 1));
      final var oldTopology =
          ConfigurationUtil.getCurrentClusterConfigurationFrom(
                  UNSCALED_MEMBERS,
                  ZONE_AWARE_CONFIG
                      .toDistributor()
                      .distributePartitions(
                          UNSCALED_MEMBERS,
                          getSortedPartitionIds(3),
                          ZONE_AWARE_CONFIG.replicationFactor()),
                  Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, partitionConfig),
                  "clusterId")
              .updateGlobalConfiguration(
                  globalConfiguration ->
                      globalConfiguration.setPartitionDistributorConfig(ZONE_AWARE_CONFIG));

      // when
      final var operations =
          plannedOperations(
                  new ScaleRequestTransformer(
                      newMembers, Optional.empty(), Optional.empty(), Optional.of("zone-b")),
                  oldTopology)
              .get();

      // then
      final var expectedCoordinator = MemberId.from("zone-b", 0);
      AssertionsForInterfaceTypes.assertThat(operations)
          .filteredOn(PreScalingOperation.class::isInstance)
          .extracting(op -> ((PreScalingOperation) op).memberId())
          .containsExactly(expectedCoordinator);
      AssertionsForInterfaceTypes.assertThat(operations)
          .filteredOn(PostScalingOperation.class::isInstance)
          .extracting(op -> ((PostScalingOperation) op).memberId())
          .containsExactly(expectedCoordinator);
    }

    /**
     * A zone-aware cluster reaches the same planner: it resolves the distributor from the persisted
     * {@link ZoneAwareConfig}, so zone-awareness needs no separate path to cover every tenant.
     */
    @Test
    void shouldScaleAZoneAcrossEveryPhysicalTenant() {
      // given — two tenants on a zone-aware cluster
      final var configuration = twoTenantZoneAwareCluster(UNSCALED_MEMBERS);
      final Set<MemberId> newMembers = new HashSet<>(UNSCALED_MEMBERS);
      newMembers.add(MemberId.from("zone-b", 1));

      // when — zone-b grows from one broker to two
      final var phases =
          new ScaleRequestTransformer(
                  newMembers, Optional.empty(), Optional.empty(), Optional.of("zone-b"))
              .phases(configuration);

      // then — both tenants' partitions are replanned, and the callbacks still run in zone-b
      EitherAssert.assertThat(phases).isRight();
      final var partitionPhase =
          phases.get().stream()
              .filter(PartitionGroupPhase.class::isInstance)
              .map(PartitionGroupPhase.class::cast)
              .findFirst()
              .orElseThrow();
      assertThat(partitionPhase.groupOperations())
          .containsOnlyKeys(CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_A);
      assertThat(((GlobalPhase) phases.get().getFirst()).operations())
          .filteredOn(PreScalingOperation.class::isInstance)
          .extracting(op -> ((PreScalingOperation) op).memberId())
          .containsExactly(MemberId.from("zone-b", 0));
    }

    @Test
    void shouldRejectABareMemberJoiningAZoneAwareCluster() {
      // given
      final var configuration = twoTenantZoneAwareCluster(UNSCALED_MEMBERS);
      final Set<MemberId> newMembers = new HashSet<>(UNSCALED_MEMBERS);
      newMembers.add(MemberId.from("3"));

      // when
      final var phases = new ScaleRequestTransformer(newMembers).phases(configuration);

      // then
      EitherAssert.assertThat(phases)
          .isLeft()
          .left()
          .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
    }

    private CurrentClusterConfiguration twoTenantZoneAwareCluster(final Set<MemberId> members) {
      return withMirroredTenant(
          ConfigurationUtil.getCurrentClusterConfigurationFrom(
                  members,
                  ZONE_AWARE_CONFIG
                      .toDistributor()
                      .distributePartitions(
                          members, sortedPartitionIds(3), ZONE_AWARE_CONFIG.replicationFactor()),
                  Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, DynamicPartitionConfig.init()),
                  "clusterId")
              .updateGlobalConfiguration(
                  globalConfiguration ->
                      globalConfiguration.setPartitionDistributorConfig(ZONE_AWARE_CONFIG)));
    }

    @Test
    void shouldSkipScalingCallbacksWhenAddingNewZone() {
      // given: existing zone-a and zone-b, adding a broker in a brand-new zone-c that has no
      // existing broker
      final Set<MemberId> newMembers = new HashSet<>(UNSCALED_MEMBERS);
      newMembers.add(MemberId.from("zone-c", 0));
      final var oldTopology =
          ConfigurationUtil.getCurrentClusterConfigurationFrom(
                  UNSCALED_MEMBERS,
                  ZONE_AWARE_CONFIG
                      .toDistributor()
                      .distributePartitions(
                          UNSCALED_MEMBERS,
                          getSortedPartitionIds(3),
                          ZONE_AWARE_CONFIG.replicationFactor()),
                  Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, partitionConfig),
                  "clusterId")
              .updateGlobalConfiguration(
                  globalConfiguration ->
                      globalConfiguration.setPartitionDistributorConfig(ZONE_AWARE_CONFIG));

      // when
      final var operations =
          plannedOperations(
                  new ScaleRequestTransformer(
                      newMembers, Optional.empty(), Optional.empty(), Optional.of("zone-c")),
                  oldTopology)
              .get();

      // then: no pre/post scaling callbacks, the new zone's brokers create their leases on startup
      AssertionsForInterfaceTypes.assertThat(operations)
          .noneMatch(PreScalingOperation.class::isInstance)
          .noneMatch(PostScalingOperation.class::isInstance);
    }
  }
}
