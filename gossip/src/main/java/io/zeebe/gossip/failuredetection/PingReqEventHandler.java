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

import io.zeebe.gossip.*;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.*;
import io.zeebe.transport.ClientRequest;
import io.zeebe.util.state.*;
import org.slf4j.Logger;

public class PingReqEventHandler implements GossipEventConsumer
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_ACK_RECEIVED = 1;
    private static final int TRANSITION_PING_REQ_RECEIVED = 2;
    private static final int TRANSITION_FAIL = 3;

    private final GossipConfiguration configuration;
    private final MembershipList membershipList;

    private final StateMachine<Context> stateMachine;

    public PingReqEventHandler(GossipContext context)
    {
        this.configuration = context.getConfiguration();
        this.membershipList = context.getMembershipList();

        final WaitState<Context> awaitPingReqState = ctx ->
        { };

        final ForwardPingState forwardPingState = new ForwardPingState(context.getGossipEventSender());
        final AwaitAckState awaitAckState = new AwaitAckState();
        final ForwardAckState forwardAckState = new ForwardAckState(context.getGossipEventSender());

        this.stateMachine = StateMachine.<Context> builder(sm -> new Context(sm, context.getGossipEventFactory()))
                .initialState(awaitPingReqState)
                .from(awaitPingReqState).take(TRANSITION_PING_REQ_RECEIVED).to(forwardPingState)
                .from(forwardPingState).take(TRANSITION_DEFAULT).to(awaitAckState)
                .from(awaitAckState).take(TRANSITION_ACK_RECEIVED).to(forwardAckState)
                .from(awaitAckState).take(TRANSITION_FAIL).to(awaitPingReqState)
                .from(forwardAckState).take(TRANSITION_DEFAULT).to(awaitPingReqState)
                .build();
    }

    @Override
    public void accept(GossipEvent event, long requestId, int streamId)
    {
        final Member sender = membershipList.get(event.getSender());
        final Member probeMember = membershipList.get(event.getProbeMember());
        if (probeMember != null)
        {
            final boolean success = stateMachine.tryTake(TRANSITION_PING_REQ_RECEIVED);
            if (success)
            {
                final Context context = stateMachine.getContext();
                context.sender = sender;
                context.suspiciousMember = probeMember;
                context.requestId = requestId;
                context.streamId = streamId;
            }
            else
            {
                // TODO buffer incoming PING-REQ request
                LOG.trace("Reject PING-REQ from '{}' because the previous request isn't completed yet", sender);
            }
        }
        else
        {
            LOG.debug("Reject PING-REQ for unknown member '{}'", probeMember);
        }
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    private class Context extends SimpleStateMachineContext
    {
        private final GossipEventResponse ackResponse;

        private Member sender;
        private Member suspiciousMember;
        private long requestId;
        private int streamId;

        Context(StateMachine<Context> stateMachine, GossipEventFactory eventFactory)
        {
            super(stateMachine);
            this.ackResponse = eventFactory.createAckResponse();
        }
    }

    private class ForwardPingState implements TransitionState<Context>
    {
        private final GossipEventSender gossipEventSender;

        ForwardPingState(GossipEventSender gossipEventSender)
        {
            this.gossipEventSender = gossipEventSender;
        }

        @Override
        public void work(Context context) throws Exception
        {
            LOG.trace("Forward PING to '{}'", context.suspiciousMember.getId());

            final ClientRequest request = gossipEventSender.sendPing(context.suspiciousMember.getAddress());
            context.ackResponse.wrap(request, configuration.getProbeTimeout());

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
                LOG.trace("Received ACK from '{}'", context.suspiciousMember.getAddress());

                context.ackResponse.process();

                context.take(TRANSITION_ACK_RECEIVED);
            }
            else if (context.ackResponse.isFailed() || context.ackResponse.isTimedOut())
            {
                LOG.trace("Doesn't receive ACK from '{}'", context.suspiciousMember.getAddress());

                context.take(TRANSITION_FAIL);
            }
        }

        @Override
        public void onExit()
        {
            stateMachine.getContext().ackResponse.clear();
        }
    }

    private class ForwardAckState implements TransitionState<Context>
    {
        private final GossipEventSender gossipEventSender;

        ForwardAckState(GossipEventSender gossipEventSender)
        {
            this.gossipEventSender = gossipEventSender;
        }

        @Override
        public void work(Context context) throws Exception
        {
            LOG.trace("Forward ACK to '{}'", context.sender.getId());

            gossipEventSender.responseAck(context.requestId, context.streamId);

            context.take(TRANSITION_DEFAULT);
        }
    }

}
