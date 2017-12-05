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

import io.zeebe.transport.SocketAddress;

public class Member
{
    private final String id;

    private final SocketAddress address;

    private final GossipTerm term = new GossipTerm();

    private MembershipStatus status = MembershipStatus.ALIVE;

    private long suspictionTimeout = -1L;

    public Member(SocketAddress address)
    {
        this.id = String.format("%s:%d", address.host(), address.port());
        this.address = address;
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

    public long getSuspectTimeout()
    {
        return suspictionTimeout;
    }

    public Member setStatus(MembershipStatus status)
    {
        this.status = status;
        return this;
    }

    public Member setGossipTerm(GossipTerm term)
    {
        this.term.epoch(term.getEpoch());
        this.term.heartbeat(term.getHeartbeat());
        return this;
    }

    public Member setSuspictionTimeout(long suspictionTimeout)
    {
        this.suspictionTimeout = suspictionTimeout;
        return this;
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
