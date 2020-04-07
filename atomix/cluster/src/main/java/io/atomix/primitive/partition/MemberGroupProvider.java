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
import java.util.Collection;

/**
 * Member group provider.
 *
 * <p>The member group provider defines how to translate a collection of {@link Member}s into a
 * collection of {@link MemberGroup}s.
 */
public interface MemberGroupProvider {

  /**
   * Creates member groups from the given list of nodes.
   *
   * <p>The returned groups must not contain duplicate {@link MemberGroupId} or duplicate
   * membership. Not all {@link Member}s must be assigned to a group, but all groups must contain a
   * unique set of nodes.
   *
   * @param members the nodes from which to create member groups
   * @return a collection of member groups
   */
  Collection<MemberGroup> getMemberGroups(Collection<Member> members);
}
