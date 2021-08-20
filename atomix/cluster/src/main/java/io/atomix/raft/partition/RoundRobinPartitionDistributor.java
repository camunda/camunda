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

import com.google.common.collect.Sets;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This implementation of {@link PartitionDistributor} distributes the partitions in a round robin
 * fashion over the set of members in the cluster.
 *
 * <p>Example distribution with 4 members, 5 partitions, and a replication factor of 3
 *
 * <pre>
 * +------------------+----+----+----+---+
 * | Partition \ Node | 0  | 1  | 2  | 3 |
 * +------------------+----+----+----+---+
 * |                1 | 3  | 2  | 1  |   |
 * |                2 |    | 3  | 2  | 1 |
 * |                3 | 1  |    | 3  | 2 |
 * |                4 | 2  | 1  |    | 3 |
 * |                5 | 3  | 1  | 2  |   |
 * +------------------+----+----+----+---+
 * </pre>
 */
public final class RoundRobinPartitionDistributor implements PartitionDistributor {

  @Override
  public Set<PartitionMetadata> distributePartitions(
      final Set<MemberId> clusterMembers,
      final List<PartitionId> sortedPartitionIds,
      final int replicationFactor) {
    final List<MemberId> sorted = new ArrayList<>(clusterMembers);
    Collections.sort(sorted);

    final int length = sorted.size();
    final int count = Math.min(replicationFactor, length);

    final Set<PartitionMetadata> metadata = Sets.newHashSet();
    for (int i = 0; i < sortedPartitionIds.size(); i++) {
      final PartitionId partitionId = sortedPartitionIds.get(i);
      final List<MemberId> membersForPartition = new ArrayList<>(count);
      for (int j = 0; j < count; j++) {
        membersForPartition.add(sorted.get((i + j) % length));
      }
      final var primary = sorted.get(i % length);
      final var priorities =
          getPriorities(
              partitionId, membersForPartition, primary, sorted.size(), replicationFactor);
      metadata.add(
          new PartitionMetadata(
              partitionId, membersForPartition, priorities, priorities.get(primary)));
    }
    return metadata;
  }

  private Map<MemberId, Integer> getPriorities(
      final PartitionId partitionId,
      final List<MemberId> membersForPartition,
      final MemberId primary,
      final int clusterSize,
      final int replicationFactor) {
    final Map<MemberId, Integer> priority = new HashMap<>();
    final int lowestPriority = 1;

    priority.put(primary, replicationFactor);
    // To ensure that secondary priorities are distributed evenly, we alternate the nodes for which
    // second priority is assigned. Example, clusterSize = 3 partitionCount = 12. Node 0 has highest
    // priority (=3) for partition 1,4,7 and 10. For partition 1 and 7, node 1 gets priority 2. For
    // partition 4 and 10, node 2 gets priority 2. This is done so that if node 0 dies, the
    // leadership is evenly distributed on the rest of the followers.
    if ((partitionId.id() - 1) / clusterSize % 2 == 0) {
      int nextPriority = replicationFactor - 1;
      for (final MemberId member : membersForPartition) {
        if (!member.equals(primary)) {
          priority.put(member, nextPriority);
          nextPriority--;
        }
      }
    } else {
      int nextPriority = lowestPriority;
      for (final MemberId member : membersForPartition) {
        if (!member.equals(primary)) {
          priority.put(member, nextPriority);
          nextPriority++;
        }
      }
    }
    return priority;
  }
}
