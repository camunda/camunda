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

import java.time.Duration;
import java.util.List;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.dissemination.*;
import io.zeebe.gossip.failuredetection.*;
import io.zeebe.gossip.membership.*;
import io.zeebe.gossip.protocol.*;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

/**
 * Implementation of the SWIM (Scalable Weakly-consistent Infection-style Membership) protocol.
 * <p>
 * Note that implementation is designed to run on a single thread as an actor.
 *
 */
public class Gossip extends Actor implements GossipController, GossipEventPublisher
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private final GossipConfiguration configuration;
    private final MembershipList membershipList;
    private final DisseminationComponent disseminationComponent;

    private final JoinController joinController;
    private final PingController pingController;
    private final SyncRequestEventHandler syncRequestHandler;

    private final CustomEventListenerConsumer customEventListenerConsumer;
    private final BufferingServerTransport serverTransport;
    private final GossipRequestHandler requestHandler;
    private final String gossipName;

    public Gossip(
            final SocketAddress socketAddress,
            final BufferingServerTransport serverTransport,
            final ClientTransport clientTransport,
            final GossipConfiguration configuration)
    {
        gossipName = socketAddress.toString();
        this.serverTransport = serverTransport;
        this.configuration = configuration;

        membershipList = new MembershipList(socketAddress, this::onSuspectMember);
        disseminationComponent = new DisseminationComponent(configuration, membershipList);

        customEventListenerConsumer = new CustomEventListenerConsumer();
        final CustomEventSyncResponseSupplier customEventSyncRequestSupplier = new CustomEventSyncResponseSupplier();

        final GossipEventFactory gossipEventFactory = new GossipEventFactory(configuration, membershipList, disseminationComponent, customEventSyncRequestSupplier, customEventListenerConsumer);
        final GossipEventSender gossipEventSender = new GossipEventSender(clientTransport, serverTransport, membershipList, gossipEventFactory);

        final GossipContext context = new GossipContext(configuration, membershipList, disseminationComponent, gossipEventSender, gossipEventFactory);

        joinController = new JoinController(context, actor);
        pingController = new PingController(context, actor);
        syncRequestHandler = new SyncRequestEventHandler(context, customEventSyncRequestSupplier, actor);

        requestHandler = new GossipRequestHandler(gossipEventFactory);
        requestHandler.registerGossipEventConsumer(GossipEventType.PING, new PingEventHandler(context));
        requestHandler.registerGossipEventConsumer(GossipEventType.PING_REQ, new PingReqEventHandler(context, actor));
        requestHandler.registerGossipEventConsumer(GossipEventType.SYNC_REQUEST, syncRequestHandler);
    }

    @Override
    protected void onActorStarting()
    {
        final ActorFuture<ServerInputSubscription> openSubscriptionFuture =
            serverTransport.openSubscription("gossip", null, requestHandler);

        actor.runOnCompletion(openSubscriptionFuture, (subscription, failure) ->
        {
            if (failure == null)
            {
                actor.consume(subscription, () ->
                {
                    if (subscription.poll(1) <= 0)
                    {
                        actor.yield();
                    }
                });
            }
            else
            {
                LOG.error("Failed to open subscription", failure);
            }
        });

        membershipList.addListener(new GossipMembershipListener()
        {
            @Override
            public void onAdd(Member member)
            {
                // start ping when the first member is added
                if (membershipList.size() == 1)
                {
                    actor.submit(pingController::sendPing);
                }
            }

            @Override
            public void onRemove(Member member)
            {
                // ping is stopped when the last member is removed
            }
        });
    }

    public ActorFuture<Void> close()
    {
        return actor.close();
    }

    private void onSuspectMember(Member member)
    {
        final GossipTerm suspicionTerm = new GossipTerm().wrap(member.getTerm());

        final int multiplier = configuration.getSuspicionMultiplier();
        final int clusterSize = 1 + membershipList.size();
        final Duration probeInterval = configuration.getProbeInterval();
        final Duration suspicionTimeout = GossipMath.suspicionTimeout(multiplier, clusterSize, probeInterval);

        actor.runDelayed(suspicionTimeout, () ->
        {
            // ensure that the member is still suspected
            if (member.getTerm().isEqual(suspicionTerm))
            {
                LOG.info("Remove suspicious member '{}'", member.getId());

                membershipList.removeMember(member.getAddress());

                LOG.trace("Spread CONFIRM event about '{}'", member.getId());

                disseminationComponent.addMembershipEvent()
                    .address(member.getAddress())
                    .gossipTerm(member.getTerm())
                    .type(MembershipEventType.CONFIRM);
            }
        });
    }

    @Override
    public String getName()
    {
        return gossipName;
    }

    @Override
    public ActorFuture<Void> join(List<SocketAddress> contactPoints)
    {
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

        actor.call(() -> joinController.join(contactPoints, future));

        return future;
    }

    @Override
    public ActorFuture<Void> leave()
    {
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

        actor.call(() -> joinController.leave(future));

        return future;
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
