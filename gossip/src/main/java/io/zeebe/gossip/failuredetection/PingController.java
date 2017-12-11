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
import java.util.List;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.*;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.*;
import io.zeebe.gossip.protocol.*;
import io.zeebe.transport.ClientRequest;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.state.*;
import io.zeebe.util.time.ClockUtil;
import org.slf4j.Logger;

public class PingController implements Actor
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_ACK_RECEIVED = 1;
    private static final int TRANSITION_TIMEOUT = 2;
    private static final int TRANSITION_FAIL = 3;

    private final GossipConfiguration config;

    private final MembershipList memberList;
    private final RoundRobinMemberIterator memberIterator;

    private final StateMachine<Context> stateMachine;

    public PingController(GossipContext context)
    {
        this.config = context.getConfiguration();

        this.memberList = context.getMemberList();
        this.memberIterator = new RoundRobinMemberIterator(memberList);

        final AwaitNextIntervalState awaitNextIntervalState = new AwaitNextIntervalState();
        final SendPingState sendPingState = new SendPingState(context.getGossipEventSender());
        final AwaitAckState awaitAckState = new AwaitAckState();
        final SendPingReqState sendPingReqState = new SendPingReqState(context.getGossipEventSender());
        final AwaitIndirectAckState awaitIndirectAckState = new AwaitIndirectAckState();
        final SuspectMemberState markAsSuspectState = new SuspectMemberState(context.getDisseminationComponent());

        this.stateMachine = StateMachine.<Context> builder(sm -> new Context(sm, context.getGossipEventFactory(), config.getProbeIndirectNodes()))
                .initialState(awaitNextIntervalState)
                .from(awaitNextIntervalState).take(TRANSITION_DEFAULT).to(sendPingState)
                .from(sendPingState).take(TRANSITION_DEFAULT).to(awaitAckState)
                .from(awaitAckState).take(TRANSITION_ACK_RECEIVED).to(awaitNextIntervalState)
                .from(awaitAckState).take(TRANSITION_FAIL).to(sendPingReqState)
                .from(sendPingReqState).take(TRANSITION_DEFAULT).to(awaitIndirectAckState)
                .from(awaitIndirectAckState).take(TRANSITION_ACK_RECEIVED).to(awaitNextIntervalState)
                .from(awaitIndirectAckState).take(TRANSITION_TIMEOUT).to(markAsSuspectState)
                .from(markAsSuspectState).take(TRANSITION_DEFAULT).to(awaitNextIntervalState)
                .build();
    }

    @Override
    public int doWork() throws Exception
    {
        return stateMachine.doWork();
    }

    private class Context extends SimpleStateMachineContext
    {
        private long nextInterval;
        private Member probeMember;
        private long pingReqTimeout;
        private final GossipEventResponse ackResponse;
        private final List<ClientRequest> indirectRequests;

        Context(StateMachine<Context> stateMachine, GossipEventFactory eventFactory, int probeIndirectNodes)
        {
            super(stateMachine);

            this.ackResponse = new GossipEventResponse(eventFactory.createFailureDetectionEvent());
            this.indirectRequests = new ArrayList<>(probeIndirectNodes);
        }
    }

    private class AwaitNextIntervalState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            final long currentTime = ClockUtil.getCurrentTimeInMillis();

            if (currentTime >= context.nextInterval)
            {
                context.nextInterval = currentTime + config.getProbeInterval();

                if (memberIterator.hasNext())
                {
                    context.take(TRANSITION_DEFAULT);
                }
            }
        }
    }

    private class SendPingState implements TransitionState<Context>
    {
        private final GossipEventSender gossipEventSender;

        SendPingState(GossipEventSender gossipEventSender)
        {
            this.gossipEventSender = gossipEventSender;
        }

        @Override
        public void work(Context context) throws Exception
        {
            final Member member = memberIterator.next();
            context.probeMember = member;

            LOG.trace("Send PING to '{}'", member.getId());

            final ClientRequest request = gossipEventSender.sendPing(member.getAddress());
            context.ackResponse.wrap(request, config.getProbeTimeout());

            context.take(TRANSITION_DEFAULT);
        }
    }

    private class AwaitAckState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            if (context.ackResponse.isReceived())
            {
                LOG.trace("Received ACK from '{}'", context.probeMember.getId());

                context.ackResponse.process();

                context.take(TRANSITION_ACK_RECEIVED);
            }
            else if (context.ackResponse.isFailed() || context.ackResponse.isTimedOut())
            {
                LOG.trace("Doesn't receive ACK from '{}'", context.probeMember.getId());

                context.take(TRANSITION_FAIL);
            }
        }

        @Override
        public void onExit()
        {
            stateMachine.getContext().ackResponse.clear();
        }
    }

    private class SendPingReqState implements TransitionState<Context>
    {
        private final GossipEventSender gossipEventSender;

        SendPingReqState(GossipEventSender gossipEventSender)
        {
            this.gossipEventSender = gossipEventSender;
        }

        @Override
        public void work(Context context) throws Exception
        {
            final Member suspiciousMember = context.probeMember;

            final int probeNodes = Math.min(config.getProbeIndirectNodes(), memberList.size() - 1);

            for (int n = 0; n < probeNodes;)
            {
                final Member member = memberIterator.next();

                if (member != suspiciousMember)
                {
                    LOG.debug("Send PING-REQ to '{}' to probe '{}'", member.getId(), suspiciousMember.getId());

                    final ClientRequest request = gossipEventSender.sendPingReq(member.getAddress(), suspiciousMember.getId());
                    context.indirectRequests.add(request);

                    n += 1;
                }
            }

            context.pingReqTimeout = ClockUtil.getCurrentTimeInMillis() + config.getProbeIndirectTimeout();
            context.take(TRANSITION_DEFAULT);
        }
    }

    private class AwaitIndirectAckState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            // only wait for the first ACK
            boolean receivedAck = false;

            for (int r = 0; r < context.indirectRequests.size() && !receivedAck; r++)
            {
                context.ackResponse.wrap(context.indirectRequests.get(r));

                if (context.ackResponse.isReceived())
                {
                    LOG.trace("Received ACK from '{}'", context.probeMember.getId());

                    context.ackResponse.process();

                    receivedAck = true;
                }
            }

            if (receivedAck)
            {
                context.take(TRANSITION_ACK_RECEIVED);
            }
            else if (ClockUtil.getCurrentTimeInMillis() >= context.pingReqTimeout)
            {
                LOG.trace("Doesn't receive any ACK to probe '{}'", context.probeMember.getId());

                context.take(TRANSITION_TIMEOUT);
            }
        }

        @Override
        public void onExit()
        {
            stateMachine.getContext().ackResponse.clear();
            stateMachine.getContext().indirectRequests.clear();
        }
    }

    private class SuspectMemberState implements TransitionState<Context>
    {
        private final DisseminationComponent disseminationComponent;

        SuspectMemberState(DisseminationComponent disseminationComponent)
        {
            this.disseminationComponent = disseminationComponent;
        }

        @Override
        public void work(Context context) throws Exception
        {
            final Member probeMember = context.probeMember;

            if (probeMember.getStatus() == MembershipStatus.ALIVE)
            {
                LOG.debug("Spread SUSPECT event of member '{}'", probeMember.getId());

                memberList.suspectMember(probeMember.getId(), probeMember.getTerm());

                disseminationComponent.addMembershipEvent()
                    .memberId(probeMember.getId())
                    .type(MembershipEventType.SUSPECT)
                    .gossipTerm(probeMember.getTerm());

            }

            context.take(TRANSITION_DEFAULT);
        }
    }

}
