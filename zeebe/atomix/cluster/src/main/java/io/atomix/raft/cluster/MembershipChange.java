/*
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
package io.atomix.raft.cluster;

import java.util.Collection;
import java.util.List;

/**
 * A change of the members that the leader makes on its own (design 5.5, R3): the full target
 * configuration with the types and incarnations of the members. See {@link
 * io.atomix.raft.RaftServer#changeMembers(MembershipChange)}.
 *
 * @param members the members of the target configuration with their types and incarnations
 * @param fenced fence entries to add; the fence list only grows
 * @param membershipVersion the membership version of the target configuration (R19)
 * @param expectedIndex the index of the configuration that the target was calculated from; the
 *     leader rejects the change if its configuration has a different index
 */
public record MembershipChange(
    Collection<RaftMember> members,
    Collection<FenceEntry> fenced,
    long membershipVersion,
    long expectedIndex) {

  public MembershipChange {
    members = List.copyOf(members);
    fenced = List.copyOf(fenced);
  }
}
