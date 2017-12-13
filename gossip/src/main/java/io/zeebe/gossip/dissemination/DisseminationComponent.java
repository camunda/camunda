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

import java.util.Iterator;

import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.gossip.GossipMath;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.*;

public class DisseminationComponent implements MembershipEventSupplier, MembershipEventConsumer
{
    private final GossipConfiguration configuration;
    private final MembershipList memberList;

    private final MembershipEventBuffer membershipEventBuffer;

    private final MembershipEventIterator viewIterator = new MembershipEventIterator(false);
    private final MembershipEventIterator incrementSpreadCountIterator = new MembershipEventIterator(true);

    public DisseminationComponent(GossipConfiguration configuration, MembershipList memberList)
    {
        this.configuration = configuration;
        this.memberList = memberList;

        this.membershipEventBuffer = new MembershipEventBuffer(32);
    }

    public BufferedMembershipEvent addMembershipEvent()
    {
        return membershipEventBuffer.add();
    }

    @Override
    public int membershipEventSize()
    {
        return membershipEventBuffer.size();
    }

    @Override
    public Iterator<MembershipEvent> membershipEventsView(int limit)
    {
        viewIterator.wrap(membershipEventBuffer.iterator(limit));

        return viewIterator;
    }

    @Override
    public Iterator<MembershipEvent> drainMembershipEvents(int limit)
    {
        incrementSpreadCountIterator.wrap(membershipEventBuffer.iterator(limit));

        return incrementSpreadCountIterator;
    }

    @Override
    public boolean consumeMembershipEvent(MembershipEvent event)
    {
        addMembershipEvent()
            .address(event.getAddress())
            .type(event.getType())
            .gossipTerm(event.getGossipTerm());

        return true;
    }

    public void clearSpreadEvents()
    {
        final int clusterSize = 1 + memberList.size();
        final int multiplier = configuration.getRetransmissionMultiplier();

        final int spreadLimit = GossipMath.gossipPeriodsToSpread(multiplier, clusterSize);

        membershipEventBuffer.removeEventsWithSpreadCountGreaterThan(spreadLimit);
    }

    private class MembershipEventIterator implements Iterator<MembershipEvent>
    {
        private final boolean incrementSpreadCount;

        private Iterator<BufferedMembershipEvent> iterator;

        MembershipEventIterator(boolean incrementSpreadCount)
        {
            this.incrementSpreadCount = incrementSpreadCount;
        }

        public void wrap(Iterator<BufferedMembershipEvent> iterator)
        {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext()
        {
            return iterator.hasNext();
        }

        @Override
        public MembershipEvent next()
        {
            final BufferedMembershipEvent event = iterator.next();

            if (incrementSpreadCount)
            {
                event.incrementSpreadCount();
            }

            return event;
        }
    }

}
