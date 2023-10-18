/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.topology.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.topology.changes.NoopTopologyMembershipChangeExecutor;
import io.camunda.zeebe.topology.changes.TopologyChangeAppliersImpl;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.topology.util.TopologyUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TopologyManagementRequestsHandlerTest {

  private final RecordingChangeCoordinator coordinator = new RecordingChangeCoordinator();
  private final TopologyManagementRequestsHandler handler =
      new TopologyManagementRequestsHandler(coordinator, new TestConcurrencyControl());

  @ParameterizedTest
  @MethodSource("provideReassignmentParameters")
  void shouldReassignPartitionsRoundRobin(
      final int partitionCount,
      final int replicationFactor,
      final int oldClusterSize,
      final int newClusterSize) {
    // given
    final var expectedNewDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                getClusterMembers(newClusterSize),
                getSortedPartitionIds(partitionCount),
                replicationFactor);

    final var oldDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                getClusterMembers(oldClusterSize),
                getSortedPartitionIds(partitionCount),
                replicationFactor);
    var oldClusterTopology = TopologyUtil.getClusterTopologyFrom(oldDistribution);
    for (int i = oldClusterSize; i < newClusterSize; i++) {
      oldClusterTopology =
          oldClusterTopology.addMember(
              MemberId.from(Integer.toString(i)), MemberState.initializeAsActive(Map.of()));
    }
    coordinator.setCurrentTopology(oldClusterTopology);

    // when
    handler
        .reassignPartitions(new ReassignPartitionsRequest(getClusterMembers(newClusterSize)))
        .join();

    // apply operations to generate new topology
    final var topologyChangeSimulator =
        new TopologyChangeAppliersImpl(
            new NoopPartitionChangeExecutor(), new NoopTopologyMembershipChangeExecutor());
    ClusterTopology newTopology = oldClusterTopology;
    if (!coordinator.getLastAppliedOperation().isEmpty()) {
      newTopology = oldClusterTopology.startTopologyChange(coordinator.getLastAppliedOperation());
    }
    while (newTopology.hasPendingChanges()) {
      final var operation = newTopology.changes().pendingOperations().get(0);
      final var applier = topologyChangeSimulator.getApplier(operation);
      newTopology = newTopology.updateMember(operation.memberId(), applier.init(newTopology).get());
      newTopology = newTopology.advanceTopologyChange(operation.memberId(), applier.apply().join());
    }

    // then
    final var newDistribution = TopologyUtil.getPartitionDistributionFrom(newTopology, "temp");
    assertThat(newDistribution).isEqualTo(expectedNewDistribution);
  }

  private List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(id -> PartitionId.from("temp", id))
        .collect(Collectors.toList());
  }

  private Set<MemberId> getClusterMembers(final int newClusterSize) {
    return IntStream.range(0, newClusterSize)
        .mapToObj(Integer::toString)
        .map(MemberId::from)
        .collect(Collectors.toSet());
  }

  public static Stream<Arguments> provideReassignmentParameters() {
    return Stream.of(
        // scaling up
        Arguments.of(6, 3, 3, 4),
        Arguments.of(6, 3, 4, 5),
        Arguments.of(6, 3, 3, 5),
        Arguments.of(6, 3, 3, 6),
        Arguments.of(7, 1, 3, 4),
        Arguments.of(24, 4, 8, 12),
        // scaling down
        Arguments.of(6, 3, 6, 3),
        Arguments.of(6, 3, 6, 4),
        Arguments.of(9, 1, 9, 3),
        Arguments.of(8, 4, 8, 4),
        // no change
        Arguments.of(1, 1, 1, 3),
        Arguments.of(1, 1, 3, 1));
  }
}
