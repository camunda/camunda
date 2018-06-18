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

import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.Function;

/**
 * Maintains the cluster topology.
 *
 * <p>Three main interactions are possible:
 *
 * <ul>
 *   <li>async querying the topology (see {@link #query(Function)})
 *   <li>async requesting a snapshot (See {@link #getTopologyDto()}
 *   <li>observer: registering a listener and getting updated about node and partition events
 * </ul>
 */
public interface TopologyManager {
  /** Can be used to query the topology. */
  <R> ActorFuture<R> query(Function<ReadableTopology, R> query);

  ActorFuture<TopologyDto> getTopologyDto();

  void removeTopologyMemberListener(TopologyMemberListener listener);

  void addTopologyMemberListener(TopologyMemberListener listener);

  void removeTopologyPartitionListener(TopologyPartitionListener listener);

  void addTopologyPartitionListener(TopologyPartitionListener listener);
}
