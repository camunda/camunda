/*
 * Copyright 2014-present Open Networking Foundation
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
package io.atomix.cluster;

import io.atomix.utils.event.ListenerService;
import io.atomix.utils.net.Address;
import java.util.Set;
import java.util.stream.Collectors;

/** Service for obtaining information about the individual members within the cluster. */
public interface ClusterMembershipService
    extends ListenerService<ClusterMembershipEvent, ClusterMembershipEventListener> {

  /**
   * Returns the local member.
   *
   * @return local member
   */
  Member getLocalMember();

  /**
   * Returns the set of current cluster members.
   *
   * @return set of cluster members
   */
  Set<Member> getMembers();

  /**
   * Returns the set of active reachable members.
   *
   * @return the set of active reachable members
   */
  default Set<Member> getReachableMembers() {
    return getMembers().stream().filter(member -> member.isReachable()).collect(Collectors.toSet());
  }

  /**
   * Returns the specified member node.
   *
   * <p>This is a convenience method that wraps the given {@link String} in a {@link MemberId}. To
   * avoid unnecessary object allocation, repeated invocations of this method should instead use
   * {@link #getMember(MemberId)}.
   *
   * @param memberId the member identifier
   * @return the member or {@code null} if no node with the given identifier exists
   */
  default Member getMember(final String memberId) {
    return getMember(MemberId.from(memberId));
  }

  /**
   * Returns the specified member.
   *
   * @param memberId the member identifier
   * @return the member or {@code null} if no node with the given identifier exists
   */
  Member getMember(MemberId memberId);

  /**
   * Returns a member by address.
   *
   * @param address the member address
   * @return the member or {@code null} if no member with the given address could be found
   */
  default Member getMember(final Address address) {
    return getMembers().stream()
        .filter(member -> member.address().equals(address))
        .findFirst()
        .orElse(null);
  }
}
