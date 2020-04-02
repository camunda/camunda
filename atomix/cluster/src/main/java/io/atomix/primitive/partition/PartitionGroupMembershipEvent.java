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

import io.atomix.utils.event.AbstractEvent;

/** Partition group membership event. */
public class PartitionGroupMembershipEvent
    extends AbstractEvent<PartitionGroupMembershipEvent.Type, PartitionGroupMembership> {

  public PartitionGroupMembershipEvent(final Type type, final PartitionGroupMembership membership) {
    super(type, membership);
  }

  public PartitionGroupMembershipEvent(
      final Type type, final PartitionGroupMembership membership, final long time) {
    super(type, membership, time);
  }

  /**
   * Returns the partition group membership.
   *
   * @return the partition group membership
   */
  public PartitionGroupMembership membership() {
    return subject();
  }

  /** Partition group membership event type. */
  public enum Type {
    MEMBERS_CHANGED
  }
}
