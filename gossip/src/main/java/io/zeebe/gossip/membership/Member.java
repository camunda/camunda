/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gossip.membership;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.Tuple;
import org.agrona.DirectBuffer;

public class Member
{
    private final String id;
    private final SocketAddress address;

    private final GossipTerm term = new GossipTerm();
    private MembershipStatus status = MembershipStatus.ALIVE;

    private long suspicionTimeout = -1L;

    private final List<Tuple<DirectBuffer, GossipTerm>> gossipTermByEventType = new ArrayList<>();

    public Member(SocketAddress address)
    {
        this.address = new SocketAddress(address);
        this.id = address.toString();
    }

    public SocketAddress getAddress()
    {
        return address;
    }

    public String getId()
    {
        return id;
    }

    public MembershipStatus getStatus()
    {
        return status;
    }

    public GossipTerm getTerm()
    {
        return term;
    }

    public long getSuspicionTimeout()
    {
        return suspicionTimeout;
    }

    public Member setStatus(MembershipStatus status)
    {
        this.status = status;
        return this;
    }

    public Member setGossipTerm(GossipTerm term)
    {
        this.term.wrap(term);
        return this;
    }

    public Member setSuspicionTimeout(long suspicionTimeout)
    {
        this.suspicionTimeout = suspicionTimeout;
        return this;
    }

    public GossipTerm getTermForEventType(DirectBuffer eventType)
    {
        for (Tuple<DirectBuffer, GossipTerm> tuple : gossipTermByEventType)
        {
            if (BufferUtil.equals(eventType, tuple.getRight()))
            {
                return tuple.getLeft();
            }
        }
        return null;
    }

    public void addTermForEventType(DirectBuffer type, GossipTerm gossipTerm)
    {
        final GossipTerm term = new GossipTerm().wrap(gossipTerm);
        final Tuple<DirectBuffer, GossipTerm> tuple = new Tuple<>(BufferUtil.cloneBuffer(type), term);
        gossipTermByEventType.add(tuple);
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("Member [id=");
        builder.append(id);
        builder.append(", status=");
        builder.append(status);
        builder.append(", term=");
        builder.append(term);
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final Member other = (Member) obj;
        if (id == null)
        {
            if (other.id != null)
            {
                return false;
            }
        }
        else if (!id.equals(other.id))
        {
            return false;
        }
        return true;
    }
}
