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

import java.util.Iterator;

import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.clustering.member.Member;
import io.zeebe.transport.SocketAddress;

public class RoundRobinSelectionStrategy implements PartitionCreatorSelectionStrategy
{

    protected final PartitionManager partitionManager;
    protected final SocketAddress lastSelectedBroker = new SocketAddress();

    public RoundRobinSelectionStrategy(PartitionManager partitionManager)
    {
        this.partitionManager = partitionManager;
    }

    @Override
    public SocketAddress selectBrokerForNewPartition()
    {
        final Iterator<Member> knownMembers = partitionManager.getKnownMembers();

        moveToLastSelectedBroker(knownMembers);
        final Member nextBroker = chooseNextBroker(knownMembers);

        if (nextBroker != null)
        {
            lastSelectedBroker.wrap(nextBroker.getManagementAddress());
            return lastSelectedBroker;
        }
        else
        {
            lastSelectedBroker.reset();
            return null;
        }
    }

    private Member chooseNextBroker(Iterator<Member> knownMembers)
    {
        Member nextBroker = findNextMemberWithManagementAddress(knownMembers);

        if (nextBroker == null)
        {
            // reset iterator and try again to find a broker
            knownMembers = partitionManager.getKnownMembers();
            nextBroker = findNextMemberWithManagementAddress(knownMembers);
        }

        return nextBroker;
    }

    private Member findNextMemberWithManagementAddress(Iterator<Member> knownMembers)
    {
        while (knownMembers.hasNext())
        {
            final Member next = knownMembers.next();
            if (next.getManagementAddress() != null)
            {
                return next;
            }
        }

        return null;
    }

    private void moveToLastSelectedBroker(Iterator<Member> knownMembers)
    {
        while (knownMembers.hasNext())
        {
            final Member candidate = knownMembers.next();
            if (lastSelectedBroker.equals(candidate.getManagementAddress()))
            {
                break;
            }
        }
    }
}
