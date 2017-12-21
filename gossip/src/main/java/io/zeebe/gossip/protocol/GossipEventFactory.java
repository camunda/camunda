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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.gossip.*;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.*;
import org.slf4j.Logger;

public class GossipEventFactory
{
    private final GossipConfiguration configuration;
    private final MembershipList memberList;
    private final DisseminationComponent disseminationComponent;

    private final List<GossipCustomEventListener> customEventListeners = new ArrayList<>();
    private final CustomEventListenersConsumer customEventListenersConsumer = new CustomEventListenersConsumer(customEventListeners);

    public GossipEventFactory(GossipConfiguration configuration, MembershipList memberList, DisseminationComponent disseminationComponent)
    {
        this.configuration = configuration;
        this.memberList = memberList;
        this.disseminationComponent = disseminationComponent;
    }

    public GossipEvent createFailureDetectionEvent()
    {
        // send the events from the dissemination component as payload
        // - update membership list with response and add events to the dissemination component
        return new GossipEvent(disseminationComponent,
                               new MembershipEventLogger()
                                   .andThen(new MembershipUpdater(memberList, disseminationComponent))
                                   .andThen(disseminationComponent),
                               disseminationComponent,
                               new CustomEventLogger()
                                   .andThen(new CustomEventUpdateChecker(memberList))
                                   .andThen(customEventListenersConsumer)
                                   .andThen(disseminationComponent),
                               configuration.getMaxMembershipEventsPerMessage());
    }

    public GossipEvent createSyncEvent()
    {
        // send the complete membership list as payload
        // - update membership list with response
        return new GossipEvent(new MembershipEventListSupplier(memberList),
                               new MembershipEventLogger()
                                   .andThen(new MembershipUpdater(memberList, disseminationComponent)),
                               disseminationComponent,
                               new CustomEventLogger(),
                               configuration.getMaxMembershipEventsPerMessage());
    }

    public void addCustomEventListener(GossipCustomEventListener listener)
    {
        this.customEventListeners.add(listener);
    }

    public void removeCustomEventListener(GossipCustomEventListener listener)
    {
        this.customEventListeners.remove(listener);
    }

    private static final class CustomEventListenersConsumer implements CustomEventConsumer
    {
        private static final Logger LOG = Loggers.GOSSIP_LOGGER;

        private final List<GossipCustomEventListener> customEventListeners;

        CustomEventListenersConsumer(List<GossipCustomEventListener> customEventListeners)
        {
            this.customEventListeners = customEventListeners;
        }

        @Override
        public boolean consumeCustomEvent(CustomEvent event)
        {
            // TODO copy the event because the listeners may / should work async
            for (GossipCustomEventListener listener : customEventListeners)
            {
                try
                {
                    listener.onEvent(event.getType(), event.getSenderAddress(), event.getPayload());
                }
                catch (Throwable t)
                {
                    LOG.warn("Custom event listener '{}' failed", listener.getClass(), t);
                }
            }

            return true;
        }
    }

    private static final class MembershipEventLogger implements MembershipEventConsumer
    {
        private static final Logger LOG = Loggers.GOSSIP_LOGGER;

        @Override
        public boolean consumeMembershipEvent(MembershipEvent event)
        {
            if (LOG.isTraceEnabled())
            {
                LOG.trace("Received membership event with address: '{}', type: {}, gossip-term: {}",
                          event.getAddress(), event.getType(), event.getGossipTerm());
            }

            return true;
        }
    }

    private static final class CustomEventLogger implements CustomEventConsumer
    {
        private static final Logger LOG = Loggers.GOSSIP_LOGGER;

        @Override
        public boolean consumeCustomEvent(CustomEvent event)
        {
            if (LOG.isTraceEnabled())
            {
                LOG.trace("Received custom event of type '{}', sender-address: '{}', gossip-term: {}",
                          bufferAsString(event.getType()), event.getSenderAddress(), event.getSenderGossipTerm());
            }

            return true;
        }
    }

}
