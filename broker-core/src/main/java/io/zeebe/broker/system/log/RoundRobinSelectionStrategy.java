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
package io.zeebe.broker.system.log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.zeebe.broker.clustering.base.topology.Topology;
import io.zeebe.broker.clustering.base.topology.TopologyMemberListener;
import io.zeebe.broker.clustering.base.topology.Topology.NodeInfo;
import io.zeebe.transport.SocketAddress;

public class RoundRobinSelectionStrategy implements PartitionCreatorSelectionStrategy, TopologyMemberListener
{
    protected final ReentrantLock lock = new ReentrantLock();
    protected final List<SocketAddress> availableNodes = new ArrayList<>();
    protected long currentIndex = 0;

    @Override
    public SocketAddress selectBrokerForNewPartition()
    {
        lock.lock();
        try
        {
            if (!availableNodes.isEmpty())
            {
                final int nextNode = (int) (++currentIndex % availableNodes.size());
                return availableNodes.get(nextNode);
            }
            return null;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void onMemberAdded(NodeInfo memberInfo, Topology topology)
    {
        final SocketAddress managementPort = memberInfo.getManagementPort();

        lock.lock();
        try
        {
            availableNodes.add(new SocketAddress(managementPort));
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void onMemberRemoved(NodeInfo memberInfo, Topology topology)
    {
        lock.lock();
        try
        {
            availableNodes.remove(memberInfo.getManagementPort());
        }
        finally
        {
            lock.unlock();
        }
    }
}

