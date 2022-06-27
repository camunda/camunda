/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.asserts;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.client.api.response.PartitionBrokerHealth;
import io.camunda.zeebe.client.api.response.PartitionBrokerRole;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.test.util.asserts.TestTopology.TestBroker;
import io.camunda.zeebe.test.util.asserts.TestTopology.TestPartition;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class TopologyAssertTest {
  @Nested
  final class IsCompleteTest {
    @Test
    void shouldFailWithWrongBrokersCount() {
      // given
      final Topology topology = new TestTopology(1, 1, 1, List.of());

      // when
      final var topologyAssert = TopologyAssert.assertThat(topology);

      // then
      assertThatCode(() -> topologyAssert.isComplete(1, 1, 1)).isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailWithWrongReplicasCount() {
      // given
      final var partition =
          new TestPartition(1, PartitionBrokerRole.LEADER, PartitionBrokerHealth.HEALTHY);
      final var broker = new TestBroker(1, List.of(partition));
      final Topology topology = new TestTopology(1, 1, 1, List.of(broker));

      // when
      final var topologyAssert = TopologyAssert.assertThat(topology);

      // then
      assertThatCode(() -> topologyAssert.isComplete(1, 1, 2)).isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailIfPartitionHasNoLeader() {
      // given
      final var partition =
          new TestPartition(1, PartitionBrokerRole.FOLLOWER, PartitionBrokerHealth.HEALTHY);
      final var broker = new TestBroker(1, List.of(partition));
      final Topology topology = new TestTopology(1, 1, 1, List.of(broker));

      // when
      final var topologyAssert = TopologyAssert.assertThat(topology);

      // then
      assertThatCode(() -> topologyAssert.isComplete(1, 1, 1)).isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailIfPartitionIsUnhealthy() {
      // given
      final var partition =
          new TestPartition(1, PartitionBrokerRole.LEADER, PartitionBrokerHealth.UNHEALTHY);
      final var broker = new TestBroker(1, List.of(partition));
      final Topology topology = new TestTopology(1, 1, 1, List.of(broker));

      // when
      final var topologyAssert = TopologyAssert.assertThat(topology);

      // then
      assertThatCode(() -> topologyAssert.isComplete(1, 1, 1)).isInstanceOf(AssertionError.class);
    }
  }

  @Nested
  final class HasLeaderForEachPartitionTest {
    @Test
    void shouldFailWhenNotEnoughPartitionsWithLeader() {
      // given
      final var partition =
          new TestPartition(1, PartitionBrokerRole.LEADER, PartitionBrokerHealth.HEALTHY);
      final var broker = new TestBroker(1, List.of(partition));
      final Topology topology = new TestTopology(1, 1, 1, List.of(broker));

      // when
      final var topologyAssert = TopologyAssert.assertThat(topology);

      // then
      assertThatCode(() -> topologyAssert.hasLeaderForEachPartition(2))
          .isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailWhenPartitionHasTwoLeaders() {
      // given
      final var partition =
          new TestPartition(1, PartitionBrokerRole.LEADER, PartitionBrokerHealth.HEALTHY);
      final Topology topology =
          new TestTopology(
              2,
              1,
              2,
              List.of(
                  new TestBroker(2, List.of(partition)), new TestBroker(2, List.of(partition))));

      // when
      final var topologyAssert = TopologyAssert.assertThat(topology);

      // then
      assertThatCode(() -> topologyAssert.hasLeaderForEachPartition(1))
          .isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailWhenPartitionHasNoLeader() {
      // given
      final var partition =
          new TestPartition(1, PartitionBrokerRole.FOLLOWER, PartitionBrokerHealth.HEALTHY);
      final var broker = new TestBroker(1, List.of(partition));
      final Topology topology = new TestTopology(1, 1, 1, List.of(broker));

      // when
      final var topologyAssert = TopologyAssert.assertThat(topology);

      // then
      assertThatCode(() -> topologyAssert.hasLeaderForEachPartition(1))
          .isInstanceOf(AssertionError.class);
    }
  }

  @Nested
  final class HasExpectedReplicasCountTest {
    @Test
    void shouldFailWhenNotEnoughReplicas() {
      // given
      final var partition =
          new TestPartition(1, PartitionBrokerRole.FOLLOWER, PartitionBrokerHealth.HEALTHY);
      final var broker = new TestBroker(1, List.of(partition));
      final Topology topology = new TestTopology(1, 1, 1, List.of(broker));

      // when
      final var topologyAssert = TopologyAssert.assertThat(topology);

      // then
      assertThatCode(() -> topologyAssert.hasExpectedReplicasCount(1, 2))
          .isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldFailWhenTooManyReplicas() {
      // given
      final var partition =
          new TestPartition(1, PartitionBrokerRole.FOLLOWER, PartitionBrokerHealth.HEALTHY);
      final Topology topology =
          new TestTopology(
              1,
              1,
              2,
              List.of(
                  new TestBroker(1, List.of(partition)), new TestBroker(2, List.of(partition))));

      // when
      final var topologyAssert = TopologyAssert.assertThat(topology);

      // then
      assertThatCode(() -> topologyAssert.hasExpectedReplicasCount(1, 1))
          .isInstanceOf(AssertionError.class);
    }
  }
}
