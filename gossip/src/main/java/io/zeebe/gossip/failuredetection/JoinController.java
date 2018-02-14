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

import static io.zeebe.gossip.failuredetection.RequestCloser.close;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.*;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.*;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.state.*;
import io.zeebe.util.time.ClockUtil;
import org.slf4j.Logger;

public class JoinController
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_RECEIVED = 1;
    private static final int TRANSITION_TIMEOUT = 2;
    private static final int TRANSITION_FAIL = 3;
    private static final int TRANSITION_JOIN = 4;
    private static final int TRANSITION_LEAVE = 5;

    private final GossipConfiguration configuration;


    private final DisseminationComponent disseminationComponent;
    private final Member self;
    private final MembershipList membershipList;
    private final GossipEventSender gossipEventSender;
    private final GossipEventFactory gossipEventFactory;

    private final StateMachine<Context> stateMachine;
    private final ActorControl actor;

    private List<SocketAddress> contactPoints;
    protected List<ActorFuture<ClientRequest>> futureRequests;
    private long timeout;
    private long nextJoinInterval;
    private SocketAddress contactPoint;
    private CompletableActorFuture<Void> future;

    private boolean isJoined;

    public JoinController(GossipContext context, ActorControl actor)
    {
        this.configuration = context.getConfiguration();

        this.actor = actor;
        this.disseminationComponent = context.getDisseminationComponent();
        this.self = context.getMembershipList().self();
        this.membershipList = context.getMembershipList();
        this.gossipEventSender = context.getGossipEventSender();
        this.gossipEventFactory = context.getGossipEventFactory();


        final WaitState<Context> awaitJoinState = ctx ->
        { };

        final WaitState<Context> joinedState = ctx ->
        { };

        final SendJoinState sendJoinState = new SendJoinState(context.getDisseminationComponent(), context.getMembershipList().self(), context.getGossipEventSender());
        final AwaitJoinResponseState awaitJoinResponseState = new AwaitJoinResponseState(context.getGossipEventFactory());
        final SendSyncRequestState sendSyncRequestState = new SendSyncRequestState(context.getGossipEventSender());
        final AwaitSyncResponseState awaitSyncResponseState = new AwaitSyncResponseState();
        final AwaitNextJoinIntervalState awaitRetryState = new AwaitNextJoinIntervalState();
        final LeaveState leaveState = new LeaveState(context.getDisseminationComponent(), context.getGossipEventSender(), context.getMembershipList());
        final AwaitLeaveResponseState awaitLeaveResponseState = new AwaitLeaveResponseState(context.getGossipEventFactory());

        this.stateMachine = StateMachine.<Context> builder(sm -> new Context(sm, context.getGossipEventFactory()))
                .initialState(awaitJoinState)
                .from(awaitJoinState).take(TRANSITION_JOIN).to(sendJoinState)
                .from(awaitJoinState).take(TRANSITION_LEAVE).to(leaveState)
                .from(sendJoinState).take(TRANSITION_DEFAULT).to(awaitJoinResponseState)
                .from(awaitJoinResponseState).take(TRANSITION_RECEIVED).to(sendSyncRequestState)
                .from(awaitJoinResponseState).take(TRANSITION_TIMEOUT).to(awaitRetryState)
                .from(sendSyncRequestState).take(TRANSITION_DEFAULT).to(awaitSyncResponseState)
                .from(awaitSyncResponseState).take(TRANSITION_DEFAULT).to(joinedState)
                .from(awaitSyncResponseState).take(TRANSITION_FAIL).to(awaitRetryState)
                .from(awaitRetryState).take(TRANSITION_DEFAULT).to(sendJoinState)
                .from(awaitRetryState).take(TRANSITION_JOIN).to(sendJoinState)
                .from(joinedState).take(TRANSITION_LEAVE).to(leaveState)
                .from(leaveState).take(TRANSITION_DEFAULT).to(awaitLeaveResponseState)
                .from(awaitLeaveResponseState).take(TRANSITION_RECEIVED).to(awaitJoinState)
                .from(awaitLeaveResponseState).take(TRANSITION_TIMEOUT).to(awaitJoinState)
                .build();
    }


    public ActorFuture<Void> join(List<SocketAddress> contactPoints)
    {
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

        if (contactPoints == null || contactPoints.isEmpty())
        {
            future.completeExceptionally(
                new IllegalArgumentException("Can't join cluster without contact points."));
        }
        else
        {
            if (!isJoined)
            {
                actor.run(this::sendJoin);
                isJoined = true;

                this.futureRequests = new ArrayList<>(contactPoints.size());
                this.contactPoints = contactPoints;
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Already joined."));
            }
        }
        return future;
    }

    private boolean firstJoin = false;

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

                actor.pollBlocking(() -> clientRequestActorFuture.join(), () -> {
                   if (!firstJoin)
                   {
                       firstJoin = true;


                   }

                    // after await
                });


            }
        }



//        context.take(TRANSITION_DEFAULT);
        // where to go


    }

    public void leave(CompletableFuture<Void> future)
    {
        final boolean success = stateMachine.tryTake(TRANSITION_LEAVE);
        if (success)
        {
            LOG.info("Leave cluster");

            final Context context = stateMachine.getContext();
            context.clear();
            context.future = future;
        }
        else
        {
            future.completeExceptionally(new IllegalStateException("Not joined."));
        }
    }



    private class Context extends SimpleStateMachineContext
    {

        Context(StateMachine<Context> stateMachine, GossipEventFactory eventFactory)
        {
            super(stateMachine);
            this.syncResponse = eventFactory.createSyncResponse();
            clear();
        }

        private void clear()
        {
            contactPoints = Collections.emptyList();
            close(requests);
            requests = Collections.emptyList();
            contactPoint = null;
            future = null;
        }
    }

    private class SendJoinState implements TransitionState<Context>
    {
        private final DisseminationComponent disseminationComponent;
        private final Member self;
        private final GossipEventSender gossipEventSender;

        SendJoinState(DisseminationComponent disseminationComponent, Member self, GossipEventSender gossipEventSender)
        {
            this.disseminationComponent = disseminationComponent;
            this.self = self;
            this.gossipEventSender = gossipEventSender;
        }

        @Override
        public void work(Context context) throws Exception
        {
            self.getTerm().increment();

            for (SocketAddress contactPoint : context.contactPoints)
            {
                if (!self.getAddress().equals(contactPoint))
                {
                    LOG.trace("Spread JOIN event to contact point '{}'", contactPoint);

                    disseminationComponent.addMembershipEvent()
                        .address(self.getAddress())
                        .type(MembershipEventType.JOIN)
                        .gossipTerm(self.getTerm());

                    final ClientRequest request = gossipEventSender.sendPing(contactPoint);
                    context.requests.add(request);
                }
            }

            context.timeout = ClockUtil.getCurrentTimeInMillis() + configuration.getJoinTimeout();
            context.take(TRANSITION_DEFAULT);
        }
    }

    private class AwaitJoinResponseState implements WaitState<Context>
    {
        private final GossipEventResponse response;

        AwaitJoinResponseState(GossipEventFactory eventFactory)
        {
            this.response = eventFactory.createAckResponse();
        }

        @Override
        public void work(Context context) throws Exception
        {
            final long currentTime = ClockUtil.getCurrentTimeInMillis();

            // only wait for the first response
            SocketAddress contactPoint = null;

            for (int r = 0; r < context.requests.size() && contactPoint == null; r++)
            {
                response.wrap(context.requests.get(r));

                if (response.isReceived())
                {
                    contactPoint = context.contactPoints.get(r);
                    LOG.trace("Received join response from contact point '{}'", contactPoint);

                    response.process();
                }
            }

            if (contactPoint != null)
            {
                context.contactPoint = contactPoint;
                context.take(TRANSITION_RECEIVED);
            }
            else if (currentTime >= context.timeout)
            {
                LOG.info("Failed to contact any of '{}'. Try again in {}ms", context.contactPoints, configuration.getJoinInterval());

                context.nextJoinInterval = currentTime + configuration.getJoinInterval();
                context.take(TRANSITION_TIMEOUT);
            }
        }

        @Override
        public void onExit()
        {
            response.clear();
            close(stateMachine.getContext().requests);
        }
    }


    private class SendSyncRequestState implements TransitionState<Context>
    {
        private final GossipEventSender gossipEventSender;

        SendSyncRequestState(GossipEventSender gossipEventSender)
        {
            this.gossipEventSender = gossipEventSender;
        }

        @Override
        public void work(Context context) throws Exception
        {
            LOG.trace("Send SYNC request to '{}'", context.contactPoint);

            final ClientRequest request = gossipEventSender.sendSyncRequest(context.contactPoint);

            context.syncResponse.wrap(request, configuration.getSyncTimeout());
            context.take(TRANSITION_DEFAULT);
        }
    }

    private class AwaitSyncResponseState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            final GossipEventResponse response = context.syncResponse;
            if (response.isReceived())
            {
                LOG.trace("Received SYNC response from '{}'", context.contactPoint);

                response.process();

                LOG.info("Joined cluster successfully");
                context.future.complete(null);

                context.clear();
                context.take(TRANSITION_DEFAULT);
            }
            else if (response.isFailed() || response.isTimedOut())
            {
                LOG.debug("Failed to receive SYNC response from '{}'. Try again in {}ms", context.contactPoint, configuration.getJoinInterval());

                context.nextJoinInterval = ClockUtil.getCurrentTimeInMillis() + configuration.getJoinInterval();
                context.take(TRANSITION_FAIL);
            }
        }

        @Override
        public void onExit()
        {
            stateMachine.getContext().syncResponse.clear();
        }
    }

    private class AwaitNextJoinIntervalState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            if (ClockUtil.getCurrentTimeInMillis() >= context.nextJoinInterval)
            {
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    private class LeaveState implements TransitionState<Context>
    {
        private final DisseminationComponent disseminationComponent;
        private final GossipEventSender gossipEventSender;
        private final MembershipList membershipList;

        LeaveState(DisseminationComponent disseminationComponent, GossipEventSender gossipEventSender, MembershipList membershipList)
        {
            this.disseminationComponent = disseminationComponent;
            this.gossipEventSender = gossipEventSender;
            this.membershipList = membershipList;
        }

        @Override
        public void work(Context context) throws Exception
        {
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

            context.requests = new ArrayList<>(spreadCount);

            for (int n = 0; n < spreadCount; n++)
            {
                final Member member = members.get(n);

                LOG.trace("Spread LEAVE event to '{}'", member.getAddress());

                final ClientRequest clientRequest = gossipEventSender.sendPing(member.getAddress());
                context.requests.add(clientRequest);
            }

            context.timeout = ClockUtil.getCurrentTimeInMillis() + configuration.getLeaveTimeout();
            context.take(TRANSITION_DEFAULT);
        }
    }

    private class AwaitLeaveResponseState implements WaitState<Context>
    {
        private final GossipEventResponse response;

        AwaitLeaveResponseState(GossipEventFactory gossipEventFactory)
        {
            this.response = gossipEventFactory.createAckResponse();
        }

        @Override
        public void work(Context context) throws Exception
        {
            boolean receivedResponses = true;

            for (int r = 0; r < context.requests.size() && receivedResponses; r++)
            {
                final ClientRequest request = context.requests.get(r);
                response.wrap(request);

                receivedResponses &= (response.isReceived() || response.isFailed());
            }

            if (receivedResponses)
            {
                LOG.info("Left cluster successfully");

                context.future.complete(null);
                context.clear();
                context.take(TRANSITION_RECEIVED);
            }
            else if (ClockUtil.getCurrentTimeInMillis() >= context.timeout)
            {
                LOG.info("Left cluster but timeout is reached before event is confirmed by all members");

                context.future.complete(null);
                context.take(TRANSITION_TIMEOUT);
            }
        }
    }
}
