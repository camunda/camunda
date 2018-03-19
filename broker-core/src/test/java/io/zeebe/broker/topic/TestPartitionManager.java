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
package io.zeebe.broker.topic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.clustering.member.Member;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.IntIterator;
import io.zeebe.util.collection.IntListIterator;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class TestPartitionManager implements PartitionManager
{

    protected List<PartitionRequest> partitionRequests = new CopyOnWriteArrayList<>();
    protected List<Member> currentMembers = new CopyOnWriteArrayList<>();
    protected Map<SocketAddress, List<Integer>> partitionsByMember = new HashMap<>();


    public void addMember(SocketAddress socketAddress)
    {
        this.currentMembers.add(new Member()
        {
            @Override
            public SocketAddress getManagementAddress()
            {
                return socketAddress;
            }

            @Override
            public IntIterator getLeadingPartitions()
            {
                return new IntListIterator(partitionsByMember.getOrDefault(socketAddress, Collections.emptyList()));
            }
        });
    }

    public void removeMember(SocketAddress socketAddress)
    {
        this.currentMembers.removeIf(m -> socketAddress.equals(m.getManagementAddress()));
    }

    public void declarePartitionLeader(SocketAddress memberAddress, int partitionId)
    {
        if (!this.partitionsByMember.containsKey(memberAddress))
        {
            this.partitionsByMember.put(memberAddress, new ArrayList<>());
        }

        this.partitionsByMember.get(memberAddress).add(partitionId);
    }

    @Override
    public ActorFuture<ClientResponse> createPartitionRemote(SocketAddress remote, DirectBuffer topicName, int partitionId)
    {
        partitionRequests.add(new PartitionRequest(remote, partitionId));
        final ClientResponse request = mock(ClientResponse.class);
        try
        {
            when(request.getResponseBuffer()).thenReturn(BufferUtil.wrapString("responseContent"));
        }
        catch (Exception e)
        {
            // make compile
        }

        return CompletableActorFuture.completed(request);
    }

    public List<PartitionRequest> getPartitionRequests()
    {
        return partitionRequests;
    }

    @Override
    public Iterator<Member> getKnownMembers()
    {
        return currentMembers.iterator();
    }


    public static class PartitionRequest
    {
        protected final SocketAddress endpoint = new SocketAddress();
        protected final int partitionId;

        public PartitionRequest(SocketAddress endpoint, int partitionId)
        {
            this.endpoint.wrap(endpoint);
            this.partitionId = partitionId;
        }

        public int getPartitionId()
        {
            return partitionId;
        }
    }

}

