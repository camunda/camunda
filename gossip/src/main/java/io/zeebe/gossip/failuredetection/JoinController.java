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
package io.zeebe.gossip.failuredetection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.gossip.GossipContext;
import io.zeebe.gossip.GossipMath;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.GossipEvent;
import io.zeebe.gossip.protocol.GossipEventFactory;
import io.zeebe.gossip.protocol.GossipEventSender;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class JoinController
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;
    private final GossipConfiguration configuration;

    private final DisseminationComponent disseminationComponent;
    private final Member self;
    private final MembershipList membershipList;
    private final GossipEventSender gossipEventSender;
    private final GossipEventFactory gossipEventFactory;
    private final ActorControl actor;

    private List<SocketAddress> contactPoints;
    protected List<ActorFuture<ClientRequest>> futureRequests;
    private SocketAddress contactPoint;

    private boolean isJoined;

    private final GossipEvent ackResponse;
    private CompletableActorFuture<Void> joinFuture;
    private GossipEvent syncResponse;

    public JoinController(GossipContext context, ActorControl actor)
    {
        this.configuration = context.getConfiguration();

        this.actor = actor;
        this.disseminationComponent = context.getDisseminationComponent();
        this.self = context.getMembershipList().self();
        this.membershipList = context.getMembershipList();
        this.gossipEventSender = context.getGossipEventSender();
        this.gossipEventFactory = context.getGossipEventFactory();

        this.ackResponse = gossipEventFactory.createAckResponse();
        this.syncResponse = gossipEventFactory.createSyncResponse();
    }

    public ActorFuture<Void> join(List<SocketAddress> contactPoints)
    {
        final CompletableActorFuture<Void> completedActorFuture = new CompletableActorFuture<>();
        actor.call(() ->
        {
            if (!isJoined)
            {
                if (joinFuture == null)
                {
                    joinFuture = completedActorFuture;
                    if (contactPoints == null || contactPoints.isEmpty())
                    {
                        joinFuture.completeExceptionally(
                            new IllegalArgumentException("Can't join cluster without contact points."));
                    }
                    else
                    {
                        actor.run(this::sendJoin);
                        this.futureRequests = new ArrayList<>(contactPoints.size());
                        this.contactPoints = contactPoints;
                    }
                }
                else
                {
                    completedActorFuture.completeExceptionally(new IllegalStateException(("Currently join in progress.")));
                }
            }
            else
            {
                completedActorFuture.completeExceptionally(new IllegalStateException("Already joined."));
            }
        });
        return completedActorFuture;
    }

    private void sendJoin()
    {
        self.getTerm().increment();

        for (SocketAddress contactPoint : contactPoints)
        {
            if (!self.getAddress().equals(contactPoint))
            {
                LOG.trace("Spread JOIN event to contact point '{}'", contactPoint);

                disseminationComponent.addMembershipEvent()
                    .address(self.getAddress())
                    .type(MembershipEventType.JOIN)
                    .gossipTerm(self.getTerm());

                final ActorFuture<ClientRequest> clientRequestActorFuture =
                    gossipEventSender.sendPing(contactPoint, configuration.getJoinTimeout());
                futureRequests.add(clientRequestActorFuture);
            }
        }

        actor.runOnFirstCompletion(futureRequests, (result, throwable) ->
        {
            if (throwable == null)
            {
                final DirectBuffer response = result.join();
                ackResponse.wrap(response, 0, response.capacity());

                contactPoint = ackResponse.getSender();
                actor.submit(this::sendSync);
            }
            else
            {
                actor.runDelayed(configuration.getJoinInterval(), this::sendJoin);
            }
        });
    }

    private void sendSync()
    {
        LOG.trace("Send SYNC request to '{}'", contactPoint);

        final ActorFuture<ClientRequest> clientRequestActorFuture =
            gossipEventSender.sendSyncRequest(contactPoint, configuration.getSyncTimeout());

        actor.await(clientRequestActorFuture, (response, throwable) ->
        {
            if (throwable == null)
            {
                LOG.debug("Received SYNC response.");

                final DirectBuffer join = response.join();
                syncResponse.wrap(join, 0, join.capacity());

                LOG.debug("Joined cluster successfully");

                isJoined = true;
                joinFuture.complete(null);
                joinFuture = null;
            }
            else
            {
                actor.runDelayed(configuration.getJoinInterval(), this::sendJoin);
            }
        });
    }

    public ActorFuture<Void> leave()
    {
        final CompletableActorFuture<Void> completableActorFuture = new CompletableActorFuture<>();
        actor.call(() ->
        {
            if (isJoined)
            {
                LOG.info("Leave cluster");
                final Member self = membershipList.self();

                self.getTerm().increment();

                disseminationComponent.addMembershipEvent()
                    .address(self.getAddress())
                    .type(MembershipEventType.LEAVE)
                    .gossipTerm(self.getTerm());

                final int clusterSize = membershipList.size();
                final int multiplier = configuration.getRetransmissionMultiplier();
                final int spreadCount = Math.min(GossipMath.gossipPeriodsToSpread(multiplier, clusterSize), clusterSize);

                final List<Member> members = new ArrayList<>(membershipList.getMembersView());
                Collections.shuffle(members);

                futureRequests = new ArrayList<>(spreadCount);

                for (int n = 0; n < spreadCount; n++)
                {
                    final Member member = members.get(n);

                    LOG.trace("Spread LEAVE event to '{}'", member.getAddress());

                    final ActorFuture<ClientRequest> clientRequestActorFuture =
                        gossipEventSender.sendPing(member.getAddress(), configuration.getLeaveTimeout());
                    futureRequests.add(clientRequestActorFuture);
                }

                actor.awaitAll(futureRequests, (throwable) ->
                {

                    completableActorFuture.complete(null);
                    if (throwable == null)
                    {
                        LOG.info("Left cluster successfully");
                    }
                    else
                    {
                        LOG.info("Left cluster but timeout is reached before event is confirmed by all members");
                    }
                });
            }
            else
            {
                completableActorFuture.completeExceptionally(new IllegalStateException("Not joined."));
            }
        });
        return completableActorFuture;
    }
}
