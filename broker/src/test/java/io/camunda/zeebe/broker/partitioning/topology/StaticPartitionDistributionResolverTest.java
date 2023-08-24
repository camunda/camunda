/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.PartitioningCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StaticPartitionDistributionResolverTest {

  @Test
  void shouldGenerateRoundRobinDistribution() {
    // given
    final var expectedDistribution =
        Map.of(
            1,
            Set.of(0, 1, 2),
            2,
            Set.of(1, 2, 3),
            3,
            Set.of(2, 3, 4),
            4,
            Set.of(3, 4, 5),
            5,
            Set.of(4, 5, 0),
            6,
            Set.of(5, 0, 1));

    final PartitioningCfg partitioningCfg = new PartitioningCfg();
    partitioningCfg.setScheme(Scheme.ROUND_ROBIN);
    final ClusterCfg clusterCfg = new ClusterCfg();
    clusterCfg.setClusterSize(6);
    clusterCfg.setPartitionsCount(6);
    clusterCfg.setReplicationFactor(3);

    // when
    final var topology =
        new PartitionDistributionResolver()
            .resolvePartitionDistribution(partitioningCfg, clusterCfg);
    final Set<PartitionMetadata> partitionDistribution = topology.partitions();

    // then
    // RoundRobinPartitionDistributorTest verifies more cases.
    final var actualDistribution = getDistribution(partitionDistribution);
    assertThat(actualDistribution).containsExactlyInAnyOrderEntriesOf(expectedDistribution);
  }

  private Map<Integer, Set<Integer>> getDistribution(
      final Set<PartitionMetadata> partitionDistribution) {
    return partitionDistribution.stream()
        .collect(
            Collectors.toMap(
                p -> p.id().id(),
                p ->
                    p.members().stream()
                        .map(m -> Integer.valueOf(m.id()))
                        .collect(Collectors.toSet())));
  }

  @Test
  void shouldGenerateFixedDistribution() throws IOException {
    final var expectedDistribution =
        Map.of(1, Set.of(0, 1, 2), 2, Set.of(1, 2, 0), 3, Set.of(0, 1, 2));
    final String config =
        """
fixed:
   - partitionId: 1
     nodes:
       - nodeId: 0
         priority: 1
       - nodeId: 1
         priority: 2
       - nodeId: 2
         priority: 3
   - partitionId: 2
     nodes:
       - nodeId: 0
         priority: 3
       - nodeId: 1
         priority: 2
       - nodeId: 2
         priority: 1
   - partitionId: 3
     nodes:
       - nodeId: 0
         priority: 2
       - nodeId: 1
         priority: 3
       - nodeId: 2
         priority: 2
""";

    final var partitioningCfg =
        new ObjectMapper(new YAMLFactory()).readValue(config, PartitioningCfg.class);
    final var clusterCfg = new ClusterCfg();
    clusterCfg.setClusterSize(3);
    clusterCfg.setPartitionsCount(3);
    clusterCfg.setReplicationFactor(3);

    // when
    final var topology =
        new PartitionDistributionResolver()
            .resolvePartitionDistribution(partitioningCfg, clusterCfg);
    final Set<PartitionMetadata> partitionDistribution = topology.partitions();

    // then
    // FixedPartitionDistributorTest verifies more cases.
    final var actualDistribution = getDistribution(partitionDistribution);
    assertThat(actualDistribution).containsExactlyInAnyOrderEntriesOf(expectedDistribution);
  }

  @Test
  void shouldGeneratePartitionDistributionFromTopology() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            PartitionId.from(PartitionManagerImpl.GROUP_NAME, 1),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(0), 1, member(1), 2, member(2), 3),
            3,
            member(2));
    final PartitionMetadata partitionTwo =
        new PartitionMetadata(
            PartitionId.from(PartitionManagerImpl.GROUP_NAME, 2),
            Set.of(member(1), member(2), member(0)),
            Map.of(member(2), 1, member(1), 2, member(0), 3),
            3,
            member(0));

    final var expected = Set.of(partitionTwo, partitionOne);

    final ClusterTopology topology =
        ClusterTopology.init()
            .addMember(
                member(0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1), 2, PartitionState.active(3))))
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2), 2, PartitionState.active(2))))
            .addMember(
                member(2),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3), 2, PartitionState.active(1))));

    // when
    final var partitionDistribution =
        new PartitionDistributionResolver().resolvePartitionDistribution(topology);

    // then
    assertThat(partitionDistribution.partitions()).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void shouldGeneratePartitionDistributionFromTopologyWithMemberWithNoPartitions() {
    // given
    final PartitionMetadata partitionOne =
        new PartitionMetadata(
            PartitionId.from(PartitionManagerImpl.GROUP_NAME, 1),
            Set.of(member(1), member(0)),
            Map.of(member(0), 1, member(1), 2),
            2,
            member(1));

    final PartitionMetadata partitionTwo =
        new PartitionMetadata(
            PartitionId.from(PartitionManagerImpl.GROUP_NAME, 2),
            Set.of(member(1), member(0)),
            Map.of(member(1), 2, member(0), 3),
            3,
            member(0));

    final var expected = Set.of(partitionTwo, partitionOne);

    final ClusterTopology topology =
        ClusterTopology.init()
            .addMember(
                member(0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1), 2, PartitionState.active(3))))
            .addMember(
                member(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2), 2, PartitionState.active(2))))
            .addMember(member(2), MemberState.initializeAsActive(Map.of()).toLeaving());

    // when
    final var partitionDistribution =
        new PartitionDistributionResolver().resolvePartitionDistribution(topology);

    // then
    assertThat(partitionDistribution.partitions()).containsExactlyInAnyOrderElementsOf(expected);
  }

  private static MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
