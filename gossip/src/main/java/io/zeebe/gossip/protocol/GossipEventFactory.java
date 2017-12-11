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

import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.*;
import org.slf4j.Logger;

public class GossipEventFactory
{
    private final MembershipList memberList;
    private final DisseminationComponent disseminationComponent;

    public GossipEventFactory(MembershipList memberList, DisseminationComponent disseminationComponent)
    {
        this.memberList = memberList;
        this.disseminationComponent = disseminationComponent;
    }

    public GossipEvent createFailureDetectionEvent()
    {
        // send the events from the dissemination component as payload
        // - update membership list with response and add events to the dissemination component
        return new GossipEvent(disseminationComponent, new MembershipEventLogger().andThen(new MembershipUpdater(memberList, disseminationComponent)).andThen(disseminationComponent));
    }

    public GossipEvent createSyncEvent()
    {
        // send the complete membership list as payload
        // - update membership list with response
        return new GossipEvent(new MembershipEventListSupplier(memberList), new MembershipEventLogger().andThen(new MembershipUpdater(memberList, disseminationComponent)));
    }

    private static final class MembershipEventLogger implements MembershipEventConsumer
    {
        private static final Logger LOG = Loggers.GOSSIP_LOGGER;

        @Override
        public boolean consumeMembershipEvent(MembershipEvent event)
        {
            if (LOG.isTraceEnabled())
            {
                LOG.trace("Receive membership event with member-id: {}, type: {}, gossip-term: {}",
                          event.getMemberId(), event.getType(), event.getGossipTerm());
            }

            return true;
        }

    }

}
