/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

final class RocksDbMemoryChangeValidatorTest {

  private static final MemberId BROKER_0 = MemberId.from("0");
  private static final MemberId BROKER_1 = MemberId.from("1");
  private static final String DEFAULT = CurrentClusterConfiguration.DEFAULT_GROUP;
  private static final String TENANT_A = "tenanta";

  private final ClusterMembershipService membershipService = mock(ClusterMembershipService.class);
  private final RocksDbMemoryChangeValidator validator =
      new RocksDbMemoryChangeValidator(membershipService);

  @BeforeEach
  void setUp() {
    // 64 MiB shared by the broker leaves exactly the 32 MiB minimum for each of 2 partitions
    advertise(BROKER_0, brokerWideRocksDbMemory(DataSize.ofMegabytes(64)));
    advertise(BROKER_1, brokerWideRocksDbMemory(DataSize.ofMegabytes(64)));
  }

  @Test
  void shouldRejectChangeThatLeavesBrokerBelowMinimumMemoryPerPartition() {
    // given
    final var configuration = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2))));
    final var phases = phases(DEFAULT, join(BROKER_0, 3));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection)
        .hasValueSatisfying(
            reason ->
                assertThat(reason)
                    .contains("broker 0")
                    .contains("3 partitions")
                    .contains("at most 2 partitions"));
  }

  @Test
  void shouldAcceptChangeThatKeepsMinimumMemoryPerPartition() {
    // given
    final var configuration = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1))));
    final var phases = phases(DEFAULT, join(BROKER_0, 2));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection).isEmpty();
  }

  @Test
  void shouldCountPartitionsOfAllPhysicalTenants() {
    // given
    final var configuration =
        configuration(
            Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2)), TENANT_A, Map.of(BROKER_1, Set.of(1))));
    final var phases = phases(TENANT_A, join(BROKER_0, 1));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection).hasValueSatisfying(reason -> assertThat(reason).contains("broker 0"));
  }

  @Test
  void shouldRejectSwapThatBrieflyExceedsLimit() {
    // given
    final var configuration = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2))));
    final var phases = phases(DEFAULT, join(BROKER_0, 3), leave(BROKER_0, 1));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection)
        .hasValueSatisfying(
            reason -> assertThat(reason).contains("broker 0").contains("3 partitions"));
  }

  @Test
  void shouldAcceptSwapThatFreesPartitionFirst() {
    // given
    final var configuration = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2))));
    final var phases = phases(DEFAULT, leave(BROKER_0, 1), join(BROKER_0, 3));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection).isEmpty();
  }

  @Test
  void shouldCountPartitionsAddedByPendingChanges() {
    // given
    final var configuration =
        configuration(
                Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1)), TENANT_A, Map.of(BROKER_1, Set.of(1))))
            .initPlan(phases(TENANT_A, join(BROKER_0, 1)));
    final var phases = phases(DEFAULT, join(BROKER_0, 2));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection)
        .hasValueSatisfying(
            reason -> assertThat(reason).contains("broker 0").contains("3 partitions"));
  }

  @Test
  void shouldNotCheckOverAllocatedBrokerTheChangeAddsNothingTo() {
    // given
    final var configuration =
        configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2, 3), BROKER_1, Set.of(1))));
    final var phases = phases(DEFAULT, join(BROKER_1, 2));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection).isEmpty();
  }

  @Test
  void shouldNotCheckOverAllocatedBrokerTheChangeRemovesFrom() {
    // given
    final var configuration =
        configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2, 3), BROKER_1, Set.of(1))));
    final var phases = phases(DEFAULT, leave(BROKER_0, 3), join(BROKER_1, 3));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection).isEmpty();
  }

  @Test
  void shouldCheckEachBrokerAgainstItsOwnLimit() {
    // given
    advertise(BROKER_1, brokerWideRocksDbMemory(DataSize.ofMegabytes(128)));
    final var configuration =
        configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1), BROKER_1, Set.of(1))));
    final var phases =
        phases(DEFAULT, join(BROKER_0, 2), join(BROKER_0, 3), join(BROKER_1, 2), join(BROKER_1, 3));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection)
        .hasValueSatisfying(
            reason -> assertThat(reason).contains("broker 0").doesNotContain("broker 1"));
  }

  @Test
  void shouldReplaceLimitWhenBrokerAdvertisesItHasNone() {
    // given
    final var advertised = member(BROKER_0, true);
    final var received = member(BROKER_0, true);
    when(membershipService.getMember(BROKER_0)).thenReturn(received);
    RocksDbMemoryChangeValidator.advertiseMaxPartitions(
        advertised, brokerWideRocksDbMemory(DataSize.ofMegabytes(64)));
    received.properties().putAll(advertised.properties());
    final var unbounded = new RocksdbCfg();
    unbounded.setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
    final var configuration = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2))));

    // when
    RocksDbMemoryChangeValidator.advertiseMaxPartitions(advertised, unbounded);
    received.properties().putAll(advertised.properties());
    final var rejection = validator.validate(configuration, phases(DEFAULT, join(BROKER_0, 3)));

    // then
    assertThat(rejection).isEmpty();
  }

  @Test
  void shouldNotCheckBrokerThatAdvertisesNoLimit() {
    // given
    final var member = member(BROKER_0, true);
    when(membershipService.getMember(BROKER_0)).thenReturn(member);
    final var configuration = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1))));
    final var phases = phases(DEFAULT, join(BROKER_0, 2), join(BROKER_0, 3));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection).isEmpty();
  }

  @Test
  void shouldCheckBrokerSuspectedDownAgainstItsLastAdvertisedLimit() {
    // given
    advertise(member(BROKER_0, false), brokerWideRocksDbMemory(DataSize.ofMegabytes(64)));
    final var configuration = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2))));
    final var phases = phases(DEFAULT, join(BROKER_0, 3));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection).hasValueSatisfying(reason -> assertThat(reason).contains("broker 0"));
  }

  @Test
  void shouldRejectChangeAddingPartitionsToBrokerMissingFromMembership() {
    // given
    when(membershipService.getMember(BROKER_0)).thenReturn(null);
    final var configuration = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1))));
    final var phases = phases(DEFAULT, join(BROKER_0, 2));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection)
        .hasValueSatisfying(
            reason -> assertThat(reason).contains("broker 0").contains("not a known member"));
  }

  @Test
  void shouldNotCheckBrokerMissingFromMembershipThatChangeRemovesPartitionsFrom() {
    // given
    when(membershipService.getMember(BROKER_0)).thenReturn(null);
    final var configuration =
        configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1), BROKER_1, Set.of(2))));
    final var phases = phases(DEFAULT, join(BROKER_1, 1), leave(BROKER_0, 1));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection).isEmpty();
  }

  @Test
  void shouldNotCheckNewBrokerMissingFromMembership() {
    // given
    when(membershipService.getMember(BROKER_1)).thenReturn(null);
    final var configuration = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2))));
    final var phases = phases(DEFAULT, join(BROKER_1, 1), join(BROKER_1, 2), join(BROKER_1, 3));

    // when
    final var rejection = validator.validate(configuration, phases);

    // then
    assertThat(rejection).isEmpty();
  }

  private void advertise(final MemberId memberId, final RocksdbCfg rocksdbCfg) {
    advertise(member(memberId, true), rocksdbCfg);
  }

  private void advertise(final Member member, final RocksdbCfg rocksdbCfg) {
    RocksDbMemoryChangeValidator.advertiseMaxPartitions(member, rocksdbCfg);
    when(membershipService.getMember(member.id())).thenReturn(member);
  }

  private static Member member(final MemberId memberId, final boolean reachable) {
    final var member = spy(Member.member(memberId, Address.from("localhost", 26502)));
    when(member.isReachable()).thenReturn(reachable);
    return member;
  }

  private static RocksdbCfg brokerWideRocksDbMemory(final DataSize memoryLimit) {
    final var cfg = new RocksdbCfg();
    cfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);
    cfg.setMemoryLimit(memoryLimit);
    return cfg;
  }

  private static List<Phase> phases(
      final String groupId, final PartitionGroupOperation... operations) {
    return List.of(PartitionGroupPhase.sequential(groupId, List.of(operations)));
  }

  private static PartitionJoinOperation join(final MemberId memberId, final int partitionId) {
    return new PartitionJoinOperation(memberId, partitionId, 1, true);
  }

  private static PartitionLeaveOperation leave(final MemberId memberId, final int partitionId) {
    return new PartitionLeaveOperation(memberId, partitionId, 1);
  }

  private static CurrentClusterConfiguration configuration(
      final Map<String, Map<MemberId, Set<Integer>>> partitionsPerMemberPerGroup) {
    final var groups =
        partitionsPerMemberPerGroup.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> partitionGroup(e.getValue())));
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        GlobalConfiguration.uninitialized(),
        groups,
        PhasedChangeState.empty());
  }

  private static PartitionGroupConfiguration partitionGroup(
      final Map<MemberId, Set<Integer>> partitionsPerMember) {
    final var partitionConfig = DynamicPartitionConfig.init();
    final var members =
        partitionsPerMember.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e ->
                        BrokerPartitionState.initialize(
                            e.getValue().stream()
                                .collect(
                                    Collectors.toMap(
                                        Function.identity(),
                                        id -> PartitionState.active(1, partitionConfig))))));
    return new PartitionGroupConfiguration(
        1, 0, members, Optional.empty(), Optional.empty(), Optional.empty());
  }
}
