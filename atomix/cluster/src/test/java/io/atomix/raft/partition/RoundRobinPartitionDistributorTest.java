/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.partition;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class RoundRobinPartitionDistributorTest {

  @ParameterizedTest(name = "[{index}] {0} {1} {2}")
  @MethodSource("clusterConfig")
  void shouldAssignPrioritiesCorrectly(
      final int clusterSize,
      final int partitionCount,
      final int replicationFactor,
      final int[][] expected) {
    // when
    final var partitionMetadata =
        new RoundRobinPartitionDistributor()
            .distributePartitions(
                getMembers(clusterSize), getSortedPartitionIds(partitionCount), replicationFactor);

    // then
    assertThat(partitionMetadata).isNotEmpty();
    partitionMetadata.forEach(
        metadata -> {
          final int partitionId = metadata.id().id();
          metadata
              .members()
              .forEach(
                  member -> {
                    final int nodeId = Integer.parseInt(member.id());
                    assertThat(metadata.getPriority(member))
                        .describedAs(
                            "Priority calculated wrong for nodeId %d, partition %d. Observed metadata is %s",
                            nodeId, partitionId, metadata)
                        .isEqualTo(expected[nodeId][partitionId - 1]);
                  });
        });
  }

  static Stream<Arguments> clusterConfig() {
    return Stream.of(
        Arguments.of(
            3, // clusterSize
            3, // partitionCount
            3, // replicationFactor
            // Expected partitionDistribution matrix row = nodes, column = partitions
            new int[][] {
              {3, 1, 2},
              {2, 3, 1},
              {1, 2, 3},
            }),
        Arguments.of(
            3,
            6,
            3,
            new int[][] {
              {3, 1, 2, 3, 2, 1},
              {2, 3, 1, 1, 3, 2},
              {1, 2, 3, 2, 1, 3},
            }),
        Arguments.of(
            6,
            3,
            3,
            new int[][] {
              {3, 0, 0},
              {2, 3, 0},
              {1, 2, 3},
              {0, 1, 2},
              {0, 0, 1},
              {0, 0, 0},
            }),
        Arguments.of(
            4,
            12,
            3,
            new int[][] {
              {3, 0, 1, 2, 3, 0, 2, 1, 3, 0, 1, 2},
              {2, 3, 0, 1, 1, 3, 0, 2, 2, 3, 0, 1},
              {1, 2, 3, 0, 2, 1, 3, 0, 1, 2, 3, 0},
              {0, 1, 2, 3, 0, 2, 1, 3, 0, 1, 2, 3},
            }));
  }

  private Set<MemberId> getMembers(final int nodeCount) {
    final Set<MemberId> members = new HashSet<>();
    for (int i = 0; i < nodeCount; i++) {
      members.add(MemberId.from(String.valueOf(i)));
    }
    return members;
  }

  private List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    final List<PartitionId> partitionIds = new ArrayList<>(partitionCount);
    for (int i = 1; i <= partitionCount; i++) {
      partitionIds.add(PartitionId.from("test", i));
    }
    return partitionIds;
  }
}
