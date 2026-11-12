/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

final class RocksDbMemoryChangeValidatorTest {

  private static final MemberId BROKER_0 = MemberId.from("0");
  private static final MemberId BROKER_1 = MemberId.from("1");
  private static final String DEFAULT = CurrentClusterConfiguration.DEFAULT_GROUP;

  // 64 MiB shared by the broker leaves exactly the 32 MiB minimum for each of 2 partitions
  private final RocksDbMemoryChangeValidator validator =
      new RocksDbMemoryChangeValidator(brokerWideRocksDbMemory(DataSize.ofMegabytes(64)));

  @Test
  void shouldRejectChangeThatLeavesBrokerBelowMinimumMemoryPerPartition() {
    // given
    final var current = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2))));
    final var proposed = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2, 3))));

    // when
    final var rejection = validator.validate(current, proposed);

    // then
    assertThat(rejection)
        .hasValueSatisfying(
            reason ->
                assertThat(reason)
                    .contains("broker 0")
                    .contains("3 partitions")
                    .contains("at least 33554432 bytes"));
  }

  @Test
  void shouldAcceptChangeThatKeepsMinimumMemoryPerPartition() {
    // given
    final var current = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1))));
    final var proposed = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2))));

    // when
    final var rejection = validator.validate(current, proposed);

    // then
    assertThat(rejection).isEmpty();
  }

  @Test
  void shouldCountPartitionsOfAllPhysicalTenants() {
    // given
    final var current = configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2))));
    final var proposed =
        configuration(
            Map.of(
                DEFAULT, Map.of(BROKER_0, Set.of(1, 2)), "tenanta", Map.of(BROKER_0, Set.of(1))));

    // when
    final var rejection = validator.validate(current, proposed);

    // then
    assertThat(rejection).hasValueSatisfying(reason -> assertThat(reason).contains("broker 0"));
  }

  @Test
  void shouldNotRejectChangeThatAddsNoPartitionsToAnOverAllocatedBroker() {
    // given
    final var current =
        configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2, 3), BROKER_1, Set.of(1))));
    final var proposed =
        configuration(Map.of(DEFAULT, Map.of(BROKER_0, Set.of(1, 2), BROKER_1, Set.of(1, 3))));

    // when
    final var rejection = validator.validate(current, proposed);

    // then
    assertThat(rejection).isEmpty();
  }

  private static RocksdbCfg brokerWideRocksDbMemory(final DataSize memoryLimit) {
    final var cfg = new RocksdbCfg();
    cfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);
    cfg.setMemoryLimit(memoryLimit);
    return cfg;
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
