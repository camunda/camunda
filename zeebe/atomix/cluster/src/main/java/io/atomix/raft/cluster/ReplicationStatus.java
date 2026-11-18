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

import io.atomix.cluster.MemberId;
import java.util.List;
import java.util.Optional;

/**
 * What the leader knows about the replication to each member (design R10), see {@link
 * io.atomix.raft.RaftServer#getReplicationStatus()}.
 *
 * @param term the term of the leader
 * @param commitIndex the commit index of the leader
 * @param members the remote members of the configuration
 */
public record ReplicationStatus(long term, long commitIndex, List<MemberStatus> members) {

  public ReplicationStatus {
    members = List.copyOf(members);
  }

  public Optional<MemberStatus> member(final MemberId memberId) {
    return members.stream().filter(m -> m.memberId().equals(memberId)).findFirst();
  }

  /**
   * @param memberId the member
   * @param incarnation the incarnation that the leader replicates to; only answers of this
   *     incarnation count (R17)
   * @param type the type of the member
   * @param matchIndex the match index of the member
   * @param lastResponseTime the time (epoch millis) of the last answer of this incarnation, 0 if
   *     none
   * @param replicationLagBytes the bytes that the leader must still send to the member
   */
  public record MemberStatus(
      MemberId memberId,
      long incarnation,
      RaftMember.Type type,
      long matchIndex,
      long lastResponseTime,
      long replicationLagBytes) {}
}
