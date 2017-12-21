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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.failuredetection.*;
import io.zeebe.gossip.membership.*;
import io.zeebe.gossip.protocol.*;
import io.zeebe.transport.*;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class Gossip implements Actor, GossipController, GossipPublisher
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private final SubscriptionController subscriptionController;
    private final PingController pingController;
    private final MembershipList memberList;
    private final DisseminationComponent disseminationComponent;

    private final PingReqEventHandler pingReqController;
    private final JoinController joinController;
    private final SuspictionController suspictionController;

    private final DeferredCommandContext deferredCommands = new DeferredCommandContext();

    private GossipEventFactory gossipEventFactory;

    public Gossip(
            final SocketAddress socketAddress,
            final BufferingServerTransport serverTransport,
            final ClientTransport clientTransport,
            final GossipConfiguration configuration)
    {
        memberList = new MembershipList(socketAddress, configuration);
        disseminationComponent = new DisseminationComponent(configuration, memberList);

        gossipEventFactory = new GossipEventFactory(configuration, memberList, disseminationComponent);

        final GossipEventSender gossipEventSender = new GossipEventSender(clientTransport, serverTransport, memberList, disseminationComponent, gossipEventFactory);

        final GossipContext context = new GossipContext(configuration, memberList, disseminationComponent, gossipEventSender, gossipEventFactory);

        pingController = new PingController(context);

        final PingEventHandler pingMessageHandler = new PingEventHandler(context);
        pingReqController = new PingReqEventHandler(context);
        final SyncRequestEventHandler syncRequestHandler = new SyncRequestEventHandler(context);

        final GossipRequestHandler requestHandler = new GossipRequestHandler(context, gossipEventFactory);
        requestHandler.registerGossipEventConsumer(GossipEventType.PING, pingMessageHandler);
        requestHandler.registerGossipEventConsumer(GossipEventType.PING_REQ, pingReqController);
        requestHandler.registerGossipEventConsumer(GossipEventType.SYNC_REQUEST, syncRequestHandler);

        joinController = new JoinController(context);
        suspictionController = new SuspictionController(context);

        subscriptionController = new SubscriptionController(serverTransport, requestHandler, configuration.getSubscriptionPollLimit());
    }

    @Override
    public CompletableFuture<Void> join(List<SocketAddress> contactPoints)
    {
        return deferredCommands.runAsync(future -> joinController.join(contactPoints, future));
    }

    @Override
    public CompletableFuture<Void> leave()
    {
        return deferredCommands.runAsync(future -> joinController.leave(future));
    }

    @Override
    public void publishEvent(DirectBuffer type, DirectBuffer payload, int offset, int length)
    {
        // TODO maybe, we should copy the payload and type because of the async behavior
        deferredCommands.runAsync(() ->
        {
            if (LOG.isTraceEnabled())
            {
                LOG.trace("Spread custom event of type '{}'", bufferAsString(type));
            }

            final Member self = memberList.self();

            GossipTerm currentTerm = self.getTermForEventType(type);
            if (currentTerm == null)
            {
                currentTerm = new GossipTerm()
                        .epoch(self.getTerm().getEpoch())
                        .heartbeat(0);

                self.addTermForEventType(type, currentTerm);
            }
            else
            {
                currentTerm.increment();
            }

            disseminationComponent.addCustomEvent()
                .senderAddress(self.getAddress())
                .senderGossipTerm(currentTerm)
                .type(type)
                .payload(payload, offset, length);
        });
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += deferredCommands.doWork();
        workCount += joinController.doWork();

        workCount += subscriptionController.doWork();
        workCount += pingController.doWork();
        workCount += pingReqController.doWork();
        workCount += suspictionController.doWork();

        return workCount;
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_HIGH;
    }

    @Override
    public void addMembershipListener(GossipMembershipListener listener)
    {
        deferredCommands.runAsync(() -> memberList.addListener(listener));
    }

    @Override
    public void removeMembershipListener(GossipMembershipListener listener)
    {
        deferredCommands.runAsync(() -> memberList.removeListener(listener));
    }

    @Override
    public void addCustomEventListener(GossipCustomEventListener listener)
    {
        deferredCommands.runAsync(() -> gossipEventFactory.addCustomEventListener(listener));
    }

    @Override
    public void removeCustomEventListener(GossipCustomEventListener listener)
    {
        deferredCommands.runAsync(() -> gossipEventFactory.removeCustomEventListener(listener));
    }

}
