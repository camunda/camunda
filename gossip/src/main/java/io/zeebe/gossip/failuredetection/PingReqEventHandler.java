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

import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.gossip.GossipContext;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.GossipEvent;
import io.zeebe.gossip.protocol.GossipEventConsumer;
import io.zeebe.gossip.protocol.GossipEventSender;
import io.zeebe.transport.ClientRequest;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class PingReqEventHandler implements GossipEventConsumer
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private final GossipConfiguration configuration;
    private final MembershipList membershipList;
    private final ActorControl actor;
    private final GossipEventSender gossipEventSender;
    private final GossipEvent ackResponse;

    public PingReqEventHandler(GossipContext context, ActorControl actorControl)
    {
        this.configuration = context.getConfiguration();
        this.membershipList = context.getMembershipList();
        this.actor = actorControl;
        this.gossipEventSender = context.getGossipEventSender();

        this.ackResponse = context.getGossipEventFactory().createAckResponse();
    }

    @Override
    public void accept(GossipEvent event, long requestId, int streamId)
    {
        final Member sender = membershipList.get(event.getSender());
        final Member probeMember = membershipList.get(event.getProbeMember());
        if (probeMember != null)
        {
            LOG.trace("Forward PING to '{}'", probeMember.getId());

            final ActorFuture<ClientRequest> clientRequestActorFuture =
                gossipEventSender.sendPing(probeMember.getAddress(), configuration.getProbeTimeout());

            actor.runOnCompletion(clientRequestActorFuture, (request, throwable) ->
            {
                if (throwable == null)
                {
                    LOG.trace("Received ACK from probe member '{}'", probeMember.getAddress());
                    final DirectBuffer response = request.join();
                    ackResponse.wrap(response, 0, response.capacity());

                    LOG.trace("Forward ACK to '{}'", sender.getId());
                    gossipEventSender.responseAck(requestId, streamId);
                }
                else
                {
                    LOG.trace("Doesn't receive ACK from probe member '{}'", probeMember.getAddress());
                    // do nothing
                }
            });
        }
        else
        {
            LOG.debug("Reject PING-REQ for unknown member '{}'", probeMember);
        }
    }
}
