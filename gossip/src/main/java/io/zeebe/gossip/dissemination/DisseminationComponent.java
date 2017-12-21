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

public class DisseminationComponent implements MembershipEventSupplier, MembershipEventConsumer, CustomEventSupplier, CustomEventConsumer
{
    private final GossipConfiguration configuration;
    private final MembershipList memberList;

    private final EventBuffer<MembershipEventImpl> membershipEventBuffer;
    private final EventBuffer<CustomEventImpl> customEventBuffer;

    private final EventIterator<MembershipEvent, MembershipEventImpl> membershipEventViewIterator = new EventIterator<>(false);
    private final EventIterator<MembershipEvent, MembershipEventImpl> membershipEventDrainIterator = new EventIterator<>(true);

    private final EventIterator<CustomEvent, CustomEventImpl> customEventViewIterator = new EventIterator<>(false);
    private final EventIterator<CustomEvent, CustomEventImpl> customEventDrainIterator = new EventIterator<>(true);

    public DisseminationComponent(GossipConfiguration configuration, MembershipList memberList)
    {
        this.configuration = configuration;
        this.memberList = memberList;

        // TODO event buffer should be expandable or use back pressure
        this.membershipEventBuffer = new EventBuffer<>(() -> new BufferedEvent<>(new MembershipEventImpl()), 32);
        this.customEventBuffer = new EventBuffer<>(() -> new BufferedEvent<>(new CustomEventImpl()), 32);
    }

    public MembershipEventImpl addMembershipEvent()
    {
        return membershipEventBuffer.add().getEvent();
    }

    public CustomEventImpl addCustomEvent()
    {
        return customEventBuffer.add().getEvent();
    }

    @Override
    public int membershipEventSize()
    {
        return membershipEventBuffer.size();
    }

    @Override
    public Iterator<MembershipEvent> membershipEventViewIterator(int limit)
    {
        membershipEventViewIterator.wrap(membershipEventBuffer.iterator(limit));

        return membershipEventViewIterator;
    }

    @Override
    public Iterator<MembershipEvent> membershipEventDrainIterator(int limit)
    {
        membershipEventDrainIterator.wrap(membershipEventBuffer.iterator(limit));

        return membershipEventDrainIterator;
    }


    @Override
    public int customEventSize()
    {
        return customEventBuffer.size();
    }

    @Override
    public Iterator<CustomEvent> customEventViewIterator(int max)
    {
        customEventViewIterator.wrap(customEventBuffer.iterator(max));

        return customEventViewIterator;
    }

    @Override
    public Iterator<CustomEvent> customEventDrainIterator(int max)
    {
        customEventDrainIterator.wrap(customEventBuffer.iterator(max));

        return customEventDrainIterator;
    }

    @Override
    public boolean consumeMembershipEvent(MembershipEvent event)
    {
        addMembershipEvent()
            .type(event.getType())
            .address(event.getAddress())
            .gossipTerm(event.getGossipTerm());

        return true;
    }

    @Override
    public boolean consumeCustomEvent(CustomEvent event)
    {
        addCustomEvent()
            .senderAddress(event.getSenderAddress())
            .senderGossipTerm(event.getSenderGossipTerm())
            .type(event.getType())
            .payload(event.getPayload());

        return true;
    }

    public void clearSpreadEvents()
    {
        final int clusterSize = 1 + memberList.size();
        final int multiplier = configuration.getRetransmissionMultiplier();

        final int spreadLimit = GossipMath.gossipPeriodsToSpread(multiplier, clusterSize);

        membershipEventBuffer.removeEventsWithSpreadCountGreaterThan(spreadLimit);
        customEventBuffer.removeEventsWithSpreadCountGreaterThan(spreadLimit);
    }

    private class EventIterator<T, U extends T> implements Iterator<T>
    {
        private final boolean incrementSpreadCount;

        private Iterator<BufferedEvent<U>> iterator;

        EventIterator(boolean incrementSpreadCount)
        {
            this.incrementSpreadCount = incrementSpreadCount;
        }

        public void wrap(Iterator<BufferedEvent<U>> iterator)
        {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext()
        {
            return iterator.hasNext();
        }

        @Override
        public T next()
        {
            final BufferedEvent<U> event = iterator.next();

            if (incrementSpreadCount)
            {
                event.incrementSpreadCount();
            }

            return event.getEvent();
        }
    }

}
