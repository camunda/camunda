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

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import java.util.List;
import java.util.Set;

/**
 * Maps a list of partitions to a set of members, based on the given replication factor.
 * Implementations of this class must guarantee that the distribution is complete, that is:
 *
 * <ul>
 *   <li>The number of members a partition belongs to is equal to the replication factor
 *   <li>All partitions are replicated
 * </ul>
 *
 * It's perfectly valid for an implementation to ignore some members, as long as the above
 * guarantees are met.
 */
public interface PartitionDistributor {

  /**
   * Provides the partition distribution based on the given list of partition IDs, cluster members,
   * and the replication factor. The set of partitions returned is guaranteed to be correctly
   * replicated.
   *
   * @param clusterMembers the set of members that can own partitions
   * @param sortedPartitionIds a sorted list of partition IDs
   * @param replicationFactor the replication factor for each partition
   * @return a set of distributed partitions, each specifying which members they belong to
   */
  Set<PartitionMetadata> distributePartitions(
      Set<MemberId> clusterMembers, List<PartitionId> sortedPartitionIds, int replicationFactor);
}
