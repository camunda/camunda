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

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.gossip.dissemination.CustomEventListenerConsumer;
import io.zeebe.gossip.dissemination.CustomEventSyncResponseSupplier;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.dissemination.SyncRequestEventHandler;
import io.zeebe.gossip.failuredetection.*;
import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.GossipEventFactory;
import io.zeebe.gossip.protocol.GossipEventSender;
import io.zeebe.gossip.protocol.GossipRequestHandler;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

import java.util.List;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

/**
 * Implementation of the SWIM (Scalable Weakly-consistent Infection-style Membership) protocol.
 * <p>
 * Note that implementation is designed to run on a single thread as an actor.
 *
 */
public class Gossip extends ZbActor implements GossipController, GossipEventPublisher
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private final GossipConfiguration configuration;
    private final MembershipList membershipList;
    private final DisseminationComponent disseminationComponent;
    private final GossipEventFactory gossipEventFactory;

    private final JoinController joinController;
    private final PingController pingController;
    private final PingReqEventHandler pingReqController;
    private final SyncRequestEventHandler syncRequestHandler;
    private final SuspicionController suspicionController;

    private CustomEventListenerConsumer customEventListenerConsumer;
    private final BufferingServerTransport serverTransport;
    private final GossipRequestHandler requestHandler;

    public Gossip(
            final SocketAddress socketAddress,
            final BufferingServerTransport serverTransport,
            final ClientTransport clientTransport,
            final GossipConfiguration configuration)
    {
        this.serverTransport = serverTransport;
        membershipList = new MembershipList(socketAddress, configuration);
        disseminationComponent = new DisseminationComponent(configuration, membershipList);

        customEventListenerConsumer = new CustomEventListenerConsumer();
        final CustomEventSyncResponseSupplier customEventSyncRequestSupplier = new CustomEventSyncResponseSupplier();

        gossipEventFactory = new GossipEventFactory(configuration, membershipList, disseminationComponent, customEventSyncRequestSupplier, customEventListenerConsumer);
        final GossipEventSender gossipEventSender = new GossipEventSender(clientTransport, serverTransport, membershipList, gossipEventFactory);

        this.configuration = configuration;
        final GossipContext context = new GossipContext(configuration, membershipList, disseminationComponent, gossipEventSender, gossipEventFactory);

        joinController = new JoinController(context, actor);
        suspicionController = new SuspicionController(context);

        pingController = new PingController(context, actor);
        pingReqController = new PingReqEventHandler(context, actor);
        syncRequestHandler = new SyncRequestEventHandler(context, customEventSyncRequestSupplier, actor);

        requestHandler = new GossipRequestHandler(gossipEventFactory);
        requestHandler.registerGossipEventConsumer(GossipEventType.PING, new PingEventHandler(context));
        requestHandler.registerGossipEventConsumer(GossipEventType.PING_REQ, pingReqController);
        requestHandler.registerGossipEventConsumer(GossipEventType.SYNC_REQUEST, syncRequestHandler);
    }


    @Override
    protected void onActorStarted()
    {
        // ping timer


        // defered -> calls
        // done

        // subscriptionController -> consume
        final ActorFuture<ServerInputSubscription> serverInputSubscriptionActorFuture =
            serverTransport.openSubscription("gossip", null, requestHandler);

        actor.await(serverInputSubscriptionActorFuture, (subscription, throwable) ->
        {
            if (throwable == null)
            {
                actor.consume(subscription, () -> subscription.poll());
            }
            else
            {
                LOG.error("Failed to open subscription!", throwable);
            }
        });



        actor.runDelayed(configuration.getProbeInterval(), pingController::sendPing);

        // sync same

        // suspicion -> in ping ctrl
        // run delayed with timeout time -> runnable checks if still suspected
        //

    }



    @Override
    protected void onActorClosing()
    {
        super.onActorClosing();
    }

    @Override
    public int doWork()
    {
        int workCount = 0;

        workCount += suspicionController.doWork();

        return workCount;
    }


    @Override
    public ActorFuture<Void> join(List<SocketAddress> contactPoints)
    {
        return joinController.join(contactPoints);
    }

    @Override
    public ActorFuture<Void> leave()
    {
        return joinController.leave();
    }

    @Override
    public void publishEvent(DirectBuffer typeBuffer, DirectBuffer payloadBuffer, int offset, int length)
    {
        // copy the buffer because of the asynchronous execution
        final DirectBuffer type = BufferUtil.cloneBuffer(typeBuffer);
        final DirectBuffer payload = BufferUtil.cloneBuffer(payloadBuffer, offset, length);

        actor.call(() ->
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
        actor.call(() -> membershipList.addListener(listener));
    }

    @Override
    public void removeMembershipListener(GossipMembershipListener listener)
    {
        actor.call(() -> membershipList.removeListener(listener));
    }

    @Override
    public void addCustomEventListener(DirectBuffer eventType, GossipCustomEventListener listener)
    {
        actor.call(() -> customEventListenerConsumer.addCustomEventListener(eventType, listener));
    }

    @Override
    public void removeCustomEventListener(GossipCustomEventListener listener)
    {
        actor.call(() -> customEventListenerConsumer.removeCustomEventListener(listener));
    }

    @Override
    public void registerSyncRequestHandler(DirectBuffer eventType, GossipSyncRequestHandler handler)
    {
        actor.call(() -> syncRequestHandler.registerSyncRequestHandler(eventType, handler));
    }

}
