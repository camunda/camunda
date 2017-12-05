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

import java.util.Iterator;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.protocol.*;

public class MembershipEventListSupplier implements MembershipEventSupplier
{
    private final MembershipList memberList;
    private final MembershipEventIterator iterator;

    public MembershipEventListSupplier(MembershipList memberList)
    {
        this.memberList = memberList;
        this.iterator = new MembershipEventIterator(memberList);
    }

    @Override
    public int membershipEventSize()
    {
        return 1 + memberList.size();
    }

    @Override
    public Iterator<MembershipEvent> membershipEventsView(int max)
    {
        iterator.reset();

        return iterator;
    }

    @Override
    public Iterator<MembershipEvent> drainMembershipEvents(int max)
    {
        return membershipEventsView(max);
    }

    private class MembershipEventIterator implements Iterator<MembershipEvent>
    {
        private final MembershipEventImpl membershipEvent = new MembershipEventImpl();

        private final Member self;

        private Iterator<Member> iterator;
        private int index = 0;

        MembershipEventIterator(MembershipList memberList)
        {
            this.self = memberList.self();
        }

        public void reset()
        {
            iterator = memberList.iterator();
            index = 0;
        }

        @Override
        public boolean hasNext()
        {
            return index == 0 || iterator.hasNext();
        }

        @Override
        public MembershipEvent next()
        {
            Member member = null;
            if (index == 0)
            {
                member = self;
            }
            else
            {
                member = iterator.next();
            }

            final MembershipEventType eventType = resolveType(member.getStatus());
            if (eventType != null)
            {
                membershipEvent.wrap(member.getId(), eventType, member.getTerm().getEpoch(), member.getTerm().getHeartbeat());
            }

            index += 1;

            return membershipEvent;
        }

        private MembershipEventType resolveType(MembershipStatus status)
        {
            switch (status)
            {
                case SUSPECT:
                    return MembershipEventType.SUSPECT;
                case ALIVE:
                    return MembershipEventType.ALIVE;
                default:
                    return null;
            }
        }

    }
}
