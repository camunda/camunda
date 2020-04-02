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
import io.atomix.utils.event.ListenerService;
import java.util.Collection;

/**
 * Member group service.
 *
 * <p>The member group service provides member group info within the context of a {@link
 * PartitionGroup}. Each partition group may be assigned a different {@link MemberGroupProvider} and
 * thus can define member groups differently.
 */
public interface MemberGroupService
    extends ListenerService<MemberGroupEvent, MemberGroupEventListener> {

  /**
   * Returns the collection of member groups.
   *
   * @return the collection of member groups
   */
  Collection<MemberGroup> getMemberGroups();

  /**
   * Returns the group for the given node.
   *
   * @param member the node for which to return the group
   * @return the group for the given node
   */
  default MemberGroup getMemberGroup(final Member member) {
    return getMemberGroups().stream()
        .filter(group -> group.isMember(member))
        .findAny()
        .orElse(null);
  }
}
