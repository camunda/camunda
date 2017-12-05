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

}
