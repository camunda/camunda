/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive.partition;

import io.atomix.cluster.MemberId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A partition or shard is a group of controller nodes that are work together to maintain state. A
 * ONOS cluster is typically made of of one or partitions over which the the data is partitioned.
 */
public class PartitionMetadata {
  private final PartitionId id;
  private final List<MemberId> members;
  private final Map<MemberId, Integer> priority;
  private final int targetPriority;

  public PartitionMetadata(
      final PartitionId id,
      final List<MemberId> members,
      final Map<MemberId, Integer> priority,
      final int targetPriority) {
    this.id = id;
    this.members = members;
    this.priority = priority;
    this.targetPriority = targetPriority;
  }

  /**
   * Returns the partition identifier.
   *
   * @return partition identifier
   */
  public PartitionId id() {
    return id;
  }

  /**
   * Returns the controller nodes that are members of this partition.
   *
   * @return collection of controller node identifiers
   */
  public Collection<MemberId> members() {
    return members;
  }

  /**
   * Return the priority of the node if the node is a member of the replication group for this
   * partition. Otherwise return -1.
   *
   * @param member
   * @return the priority of the member
   */
  public int getPriority(final MemberId member) {
    return priority.getOrDefault(member, -1);
  }

  public int getTargetPriority() {
    return targetPriority;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, members);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof PartitionMetadata) {
      final PartitionMetadata partition = (PartitionMetadata) object;
      return partition.id.equals(id) && partition.members.equals(members);
    }
    return false;
  }

  @Override
  public String toString() {
    return "PartitionMetadata{"
        + "id="
        + id
        + ", members="
        + members
        + ", priority="
        + priority
        + ", targetPriority="
        + targetPriority
        + '}';
  }
}
