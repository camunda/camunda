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
package io.zeebe.gossip.dissemination;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.gossip.protocol.MembershipEvent;

public class BufferedMembershipEvent implements Comparable<BufferedMembershipEvent>, MembershipEvent
{
    private boolean isSet = false;
    private int spreadCount = 0;

    private MembershipEventType type = MembershipEventType.NULL_VAL;
    private GossipTerm gossipTerm = new GossipTerm();
    private String memberId = null;

    public int getSpreadCount()
    {
        return spreadCount;
    }

    public void incrementSpreadCount()
    {
        this.spreadCount += 1;
    }

    @Override
    public MembershipEventType getType()
    {
        return type;
    }

    public BufferedMembershipEvent type(MembershipEventType type)
    {
        this.type = type;
        return this;
    }

    @Override
    public String getMemberId()
    {
        return memberId;
    }

    public BufferedMembershipEvent memberId(String memberId)
    {
        this.memberId = memberId;
        return this;
    }

    public long getGossipTermEpoch()
    {
        return gossipTerm.getEpoch();
    }

    public BufferedMembershipEvent gossipTermEpoch(long gossipTermEpoch)
    {
        this.gossipTerm.epoch(gossipTermEpoch);
        return this;
    }

    public long getGossipTermHeartbeat()
    {
        return gossipTerm.getHeartbeat();
    }

    public BufferedMembershipEvent gossipTermHeartbeat(long gossipTermHeartbeat)
    {
        this.gossipTerm.heartbeat(gossipTermHeartbeat);
        return this;
    }

    @Override
    public GossipTerm getGossipTerm()
    {
        return gossipTerm;
    }

    public BufferedMembershipEvent gossipTerm(GossipTerm term)
    {
        return gossipTermEpoch(term.getEpoch()).gossipTermHeartbeat(term.getHeartbeat());
    }

    public boolean isSet()
    {
        return isSet;
    }

    public void clear()
    {
        this.isSet = false;
        this.spreadCount = 0;
    }

    public void recycle()
    {
        this.isSet = true;
    }

    @Override
    public int compareTo(BufferedMembershipEvent o)
    {
        if (isSet && !o.isSet)
        {
            return -1;
        }
        else if (!isSet && o.isSet)
        {
            return 1;
        }
        else
        {
            return spreadCount - o.spreadCount;
        }
    }

}
