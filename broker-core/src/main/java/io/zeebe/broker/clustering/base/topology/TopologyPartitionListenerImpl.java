/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.topology;

import io.zeebe.util.sched.ActorControl;
import org.agrona.collections.Int2ObjectHashMap;

public class TopologyPartitionListenerImpl implements TopologyPartitionListener {

  private final Int2ObjectHashMap<NodeInfo> partitionLeaders = new Int2ObjectHashMap<>();
  private final ActorControl actor;

  public TopologyPartitionListenerImpl(final ActorControl actor) {
    this.actor = actor;
  }

  @Override
  public void onPartitionUpdated(final int partitionId, final NodeInfo member) {
    if (member.getLeaders().contains(partitionId)) {
      actor.submit(() -> updatePartitionLeader(partitionId, member));
    }
  }

  private void updatePartitionLeader(final int partitionId, final NodeInfo member) {
    final NodeInfo currentLeader = partitionLeaders.get(partitionId);

    if (currentLeader == null || !currentLeader.equals(member)) {
      partitionLeaders.put(partitionId, member);
    }
  }

  public Int2ObjectHashMap<NodeInfo> getPartitionLeaders() {
    return partitionLeaders;
  }
}
