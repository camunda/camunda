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
 * limitations under the License
 */
package io.atomix.raft.session;

import io.atomix.cluster.MemberId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Strategy for selecting nodes to which to connect and submit operations.
 *
 * <p>Selection strategies prioritize communication with certain servers over others. When the
 * client loses its connection or cluster membership changes, the client will request a list of
 * servers to which the client can connect. The address list should be prioritized.
 */
public enum CommunicationStrategy {

  /**
   * The {@code ANY} selection strategy allows the client to connect to any server in the cluster.
   * Clients will attempt to connect to a random server, and the client will persist its connection
   * with the first server through which it is able to communicate. If the client becomes
   * disconnected from a server, it will attempt to connect to the next random server again.
   */
  ANY {
    @Override
    public List<MemberId> selectConnections(final MemberId leader, final List<MemberId> members) {
      Collections.shuffle(members);
      return members;
    }
  },

  /**
   * The {@code LEADER} selection strategy forces the client to attempt to connect to the cluster's
   * leader. Connecting to the leader means the client's operations are always handled by the first
   * server to receive them. However, clients connected to the leader will not significantly benefit
   * from operations with lower consistency levels, and more clients connected to the leader could
   * mean more load on a single point in the cluster.
   *
   * <p>If the client is unable to find a leader in the cluster, the client will connect to a random
   * server.
   */
  LEADER {
    @Override
    public List<MemberId> selectConnections(final MemberId leader, final List<MemberId> members) {
      if (leader != null) {
        return Collections.singletonList(leader);
      }
      Collections.shuffle(members);
      return members;
    }
  },

  /**
   * The {@code FOLLOWERS} selection strategy forces the client to connect only to followers.
   * Connecting to followers ensures that the leader is not overloaded with direct client requests.
   * This strategy should be used when clients frequently submit operations with lower consistency
   * levels that don't need to be forwarded to the cluster leader. For clients that frequently
   * submit commands or queries with linearizable consistency, the {@link #LEADER}
   * ConnectionStrategy may be more performant.
   */
  FOLLOWERS {
    @Override
    public List<MemberId> selectConnections(final MemberId leader, final List<MemberId> members) {
      Collections.shuffle(members);
      if (leader != null && members.size() > 1) {
        final List<MemberId> results = new ArrayList<>(members.size());
        for (final MemberId memberId : members) {
          if (!memberId.equals(leader)) {
            results.add(memberId);
          }
        }
        return results;
      }
      return members;
    }
  };

  /**
   * Returns a prioritized list of servers to which the client can connect and submit operations.
   *
   * <p>The client will iterate the provided {@link MemberId} list in order, attempting to connect
   * to each listed server until all servers have been exhausted. Implementations should provide a
   * complete list of servers with which the client can communicate. Limiting the server list only
   * to a single server such as the {@code leader} may result in the client failing, such as in the
   * event that no leader exists or the client is partitioned from the leader.
   *
   * @param leader The current cluster leader. The {@code leader} may be {@code null} if no current
   *     leader exists.
   * @param members The full list of available servers. The provided server list is representative
   *     of the most recent membership update received by the client. The server list may evolve
   *     over time as the structure of the cluster changes.
   * @return A collection of servers to which the client can connect.
   */
  public abstract List<MemberId> selectConnections(MemberId leader, List<MemberId> members);
}
