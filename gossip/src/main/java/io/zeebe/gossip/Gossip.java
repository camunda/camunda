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
import io.zeebe.gossip.dissemination.*;
import io.zeebe.gossip.failuredetection.*;
import io.zeebe.gossip.membership.*;
import io.zeebe.gossip.protocol.*;
import io.zeebe.transport.*;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

/**
 * Implementation of the SWIM (Scalable Weakly-consistent Infection-style Membership) protocol.
 * <p>
 * Note that implementation is designed to run on a single thread as an actor.
 *
 */
public class Gossip implements Actor, GossipController, GossipEventPublisher
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private final DeferredCommandContext deferredCommands = new DeferredCommandContext();

    private final MembershipList membershipList;
    private final DisseminationComponent disseminationComponent;
    private final GossipEventFactory gossipEventFactory;

    private final SubscriptionController subscriptionController;
    private final JoinController joinController;
    private final PingController pingController;
    private final PingReqEventHandler pingReqController;
    private final SyncRequestEventHandler syncRequestHandler;
    private final SuspicionController suspicionController;

    private CustomEventListenerConsumer customEventListenerConsumer;

    public Gossip(
            final SocketAddress socketAddress,
            final BufferingServerTransport serverTransport,
            final ClientTransport clientTransport,
            final GossipConfiguration configuration)
    {
        membershipList = new MembershipList(socketAddress, configuration);
        disseminationComponent = new DisseminationComponent(configuration, membershipList);

        customEventListenerConsumer = new CustomEventListenerConsumer();
        final CustomEventSyncResponseSupplier customEventSyncRequestSupplier = new CustomEventSyncResponseSupplier();

        gossipEventFactory = new GossipEventFactory(configuration, membershipList, disseminationComponent, customEventSyncRequestSupplier, customEventListenerConsumer);
        final GossipEventSender gossipEventSender = new GossipEventSender(clientTransport, serverTransport, membershipList, gossipEventFactory);

        final GossipContext context = new GossipContext(configuration, membershipList, disseminationComponent, gossipEventSender, gossipEventFactory);

        joinController = new JoinController(context);
        suspicionController = new SuspicionController(context);

        pingController = new PingController(context);
        pingReqController = new PingReqEventHandler(context);
        syncRequestHandler = new SyncRequestEventHandler(context, customEventSyncRequestSupplier);

        final GossipRequestHandler requestHandler = new GossipRequestHandler(gossipEventFactory);
        requestHandler.registerGossipEventConsumer(GossipEventType.PING, new PingEventHandler(context));
        requestHandler.registerGossipEventConsumer(GossipEventType.PING_REQ, pingReqController);
        requestHandler.registerGossipEventConsumer(GossipEventType.SYNC_REQUEST, syncRequestHandler);

        subscriptionController = new SubscriptionController(serverTransport, requestHandler, configuration.getSubscriptionPollLimit());
    }

    @Override
    public int doWork()
    {
        int workCount = 0;

        workCount += deferredCommands.doWork();
        workCount += joinController.doWork();

        workCount += subscriptionController.doWork();
        workCount += pingController.doWork();
        workCount += pingReqController.doWork();
        workCount += syncRequestHandler.doWork();
        workCount += suspicionController.doWork();

        return workCount;
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_HIGH;
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
    public void publishEvent(DirectBuffer typeBuffer, DirectBuffer payloadBuffer, int offset, int length)
    {
        // copy the buffer because of the asynchronous execution
        final DirectBuffer type = BufferUtil.cloneBuffer(typeBuffer);
        final DirectBuffer payload = BufferUtil.cloneBuffer(payloadBuffer, offset, length);

        deferredCommands.runAsync(() ->
        {

            final Member self = membershipList.self();

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

            LOG.trace("Spread custom event of type '{}', in term {}", bufferAsString(type), currentTerm);

            disseminationComponent.addCustomEvent()
                .senderAddress(self.getAddress())
                .senderGossipTerm(currentTerm)
                .type(type)
                .payload(payload, offset, length);
        });
    }

    @Override
    public void addMembershipListener(GossipMembershipListener listener)
    {
        deferredCommands.runAsync(() -> membershipList.addListener(listener));
    }

    @Override
    public void removeMembershipListener(GossipMembershipListener listener)
    {
        deferredCommands.runAsync(() -> membershipList.removeListener(listener));
    }

    @Override
    public void addCustomEventListener(DirectBuffer eventType, GossipCustomEventListener listener)
    {
        deferredCommands.runAsync(() -> customEventListenerConsumer.addCustomEventListener(eventType, listener));
    }

    @Override
    public void removeCustomEventListener(GossipCustomEventListener listener)
    {
        deferredCommands.runAsync(() -> customEventListenerConsumer.removeCustomEventListener(listener));
    }

    @Override
    public void registerSyncRequestHandler(DirectBuffer eventType, GossipSyncRequestHandler handler)
    {
        deferredCommands.runAsync(() -> syncRequestHandler.registerSyncRequestHandler(eventType, handler));
    }

}
