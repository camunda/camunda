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

import io.atomix.cluster.Member;

/**
 * Partition member group.
 *
 * <p>The member group represents a group of nodes that can own a single replica for a single
 * partition. Replication is performed in a manner that avoids assigning multiple replicas to the
 * same member group.
 */
public interface MemberGroup {

  /**
   * Returns the group identifier.
   *
   * @return the group identifier
   */
  MemberGroupId id();

  /**
   * Returns a boolean indicating whether the given node is a member of the group.
   *
   * @param member the node to check
   * @return indicates whether the given node is a member of the group
   */
  boolean isMember(Member member);
}
