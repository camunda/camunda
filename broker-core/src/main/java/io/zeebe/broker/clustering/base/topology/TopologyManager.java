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

/**
 * Maintains the cluster topology.
 *
 * <p>Three main interactions are possible:
 *
 * <ul>
 *   <li>observer: registering a listener and getting updated about node and partition events
 * </ul>
 */
public interface TopologyManager {

  void removeTopologyMemberListener(TopologyMemberListener listener);

  void addTopologyMemberListener(TopologyMemberListener listener);

  void removeTopologyPartitionListener(TopologyPartitionListener listener);

  void addTopologyPartitionListener(TopologyPartitionListener listener);
}
