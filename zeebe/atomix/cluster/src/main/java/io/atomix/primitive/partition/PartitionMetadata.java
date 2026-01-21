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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Contains metadata about a partition. The metadata can be used to query for members of the
 * partition and to get member priorities.
 */
public class PartitionMetadata {
  public final Map<MemberId, Integer> priority;
  private final PartitionId id;
  private final Set<MemberId> members;
  private final int targetPriority;
  private final MemberId primary;

  public PartitionMetadata(
      final PartitionId id,
      final Set<MemberId> members,
      final Map<MemberId, Integer> priority,
      final int targetPriority,
      final MemberId primary) {
    this.id = id;
    this.members = members;
    this.priority = priority;
    this.targetPriority = targetPriority;
    this.primary = primary;
  }

  public PartitionMetadata withGroupName(final String groupName) {
    return new PartitionMetadata(
        new PartitionId(groupName, id.id()), members, priority, targetPriority, primary);
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
   * @return the priority of the member
   */
  public int getPriority(final MemberId member) {
    return priority.getOrDefault(member, -1);
  }

  /**
   * Returns the primary member of the partition or null if there is no primary
   *
   * @return member id or null
   */
  public Optional<MemberId> getPrimary() {
    return Optional.ofNullable(primary);
  }

  public int getTargetPriority() {
    return targetPriority;
  }

  @Override
  public int hashCode() {
    int result = id.hashCode();
    result = 31 * result + (members != null ? members.hashCode() : 0);
    result = 31 * result + (priority != null ? priority.hashCode() : 0);
    result = 31 * result + targetPriority;
    result = 31 * result + (primary != null ? primary.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final PartitionMetadata that = (PartitionMetadata) o;

    if (targetPriority != that.targetPriority) {
      return false;
    }
    if (!id.equals(that.id)) {
      return false;
    }
    if (!Objects.equals(members, that.members)) {
      return false;
    }
    if (!Objects.equals(priority, that.priority)) {
      return false;
    }
    return Objects.equals(primary, that.primary);
  }

  @Override
  public String toString() {
    return "PartitionMetadata{"
        + "id="
        + id
        + ", primary="
        + primary
        + ", members="
        + members
        + ", priority="
        + priority
        + ", targetPriority="
        + targetPriority
        + '}';
  }
}
