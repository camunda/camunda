/*
 * Copyright 2018-present Open Networking Foundation
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

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.cluster.MemberId;
import java.util.Set;

/** Partition group membership information. */
public final class PartitionGroupMembership {
  private final String group;
  private final PartitionGroupConfig config;
  private final Set<MemberId> members;
  private final boolean system;

  public PartitionGroupMembership(
      final String group,
      final PartitionGroupConfig config,
      final Set<MemberId> members,
      final boolean system) {
    this.group = group;
    this.config = config;
    this.members = members;
    this.system = system;
  }

  /**
   * Returns the partition group name.
   *
   * @return the partition group name
   */
  public String group() {
    return group;
  }

  /**
   * Returns the partition group configuration.
   *
   * @return the partition group configuration
   */
  public PartitionGroupConfig<?> config() {
    return config;
  }

  /**
   * Returns the partition group members.
   *
   * @return the partition group members
   */
  public Set<MemberId> members() {
    return members;
  }

  /**
   * Returns whether this partition group is the system partition group.
   *
   * @return whether this group is the system partition group
   */
  public boolean system() {
    return system;
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("group", group()).add("members", members()).toString();
  }
}
