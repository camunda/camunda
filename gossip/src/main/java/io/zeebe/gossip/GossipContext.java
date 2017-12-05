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
package io.zeebe.gossip;

import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.GossipEventFactory;
import io.zeebe.gossip.protocol.GossipEventSender;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import org.slf4j.Logger;

public class GossipContext
{
    private final Logger logger;

    private final GossipConfiguration configuration;

    private final MembershipList memberList;

    private final DisseminationComponent disseminationComponent;

    private final GossipEventFactory gossipEventFactory;
    private final GossipEventSender gossipEventSender;

    public GossipContext(
            Logger logger,
            GossipConfiguration configuration,
            MembershipList memberList,
            DisseminationComponent disseminationComponent,
            BufferingServerTransport serverTransport,
            ClientTransport clientTransport,
            GossipEventSender gossipEventSender,
            GossipEventFactory gossipEventFactory)
    {
        this.logger = logger;
        this.configuration = configuration;
        this.memberList = memberList;
        this.disseminationComponent = disseminationComponent;
        this.gossipEventSender = gossipEventSender;
        this.gossipEventFactory = gossipEventFactory;
    }

    public Logger getLogger()
    {
        return logger;
    }

    public GossipConfiguration getConfiguration()
    {
        return configuration;
    }

    public MembershipList getMemberList()
    {
        return memberList;
    }

    public DisseminationComponent getDisseminationComponent()
    {
        return disseminationComponent;
    }

    public GossipEventSender getGossipEventSender()
    {
        return gossipEventSender;
    }

    public GossipEventFactory getGossipEventFactory()
    {
        return gossipEventFactory;
    }

}
