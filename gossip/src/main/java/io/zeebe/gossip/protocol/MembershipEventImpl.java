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
package io.zeebe.gossip.protocol;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.transport.SocketAddress;

public class MembershipEventImpl implements MembershipEvent
{
    private final GossipTerm gossipTerm = new GossipTerm();
    private final SocketAddress address = new SocketAddress();

    private MembershipEventType type = MembershipEventType.NULL_VAL;

    public MembershipEventImpl type(MembershipEventType type)
    {
        this.type = type;
        return this;
    }

    public MembershipEventImpl address(SocketAddress address)
    {
        this.address.host(address.getHostBuffer(), 0, address.hostLength());
        this.address.port(address.port());
        return this;
    }

    public MembershipEventImpl gossipTerm(GossipTerm term)
    {
        this.gossipTerm.epoch(term.getEpoch()).heartbeat(term.getHeartbeat());

        return this;
    }

    @Override
    public MembershipEventType getType()
    {
        return type;
    }

    @Override
    public GossipTerm getGossipTerm()
    {
        return gossipTerm;
    }

    @Override
    public SocketAddress getAddress()
    {
        return address;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("MembershipEvent [address=");
        builder.append(address);
        builder.append(", type=");
        builder.append(type);
        builder.append(", gossipTerm=");
        builder.append(gossipTerm);
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((address == null) ? 0 : address.hashCode());
        result = prime * result + ((gossipTerm == null) ? 0 : gossipTerm.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
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
        final MembershipEventImpl other = (MembershipEventImpl) obj;
        if (address == null)
        {
            if (other.address != null)
            {
                return false;
            }
        }
        else if (!address.equals(other.address))
        {
            return false;
        }
        if (gossipTerm == null)
        {
            if (other.gossipTerm != null)
            {
                return false;
            }
        }
        else if (!gossipTerm.equals(other.gossipTerm))
        {
            return false;
        }
        if (type != other.type)
        {
            return false;
        }
        return true;
    }

}
