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

public class MembershipEventImpl implements MembershipEvent
{
    private MembershipEventType type = MembershipEventType.NULL_VAL;
    private GossipTerm gossipTerm = new GossipTerm();
    private String memberId = null;

    public void wrap(String memberId, MembershipEventType type, long gossipTermEpoch, long gossipTermHeartbeat)
    {
        this.memberId = memberId;
        this.type = type;
        this.gossipTerm.epoch(gossipTermEpoch).heartbeat(gossipTermHeartbeat);
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
    public String getMemberId()
    {
        return memberId;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((gossipTerm == null) ? 0 : gossipTerm.hashCode());
        result = prime * result + ((memberId == null) ? 0 : memberId.hashCode());
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
        if (memberId == null)
        {
            if (other.memberId != null)
            {
                return false;
            }
        }
        else if (!memberId.equals(other.memberId))
        {
            return false;
        }
        if (type != other.type)
        {
            return false;
        }
        return true;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("MembershipEvent [memberId=");
        builder.append(memberId);
        builder.append(", type=");
        builder.append(type);
        builder.append(", gossipTerm=");
        builder.append(gossipTerm);
        builder.append("]");
        return builder.toString();
    }

}
