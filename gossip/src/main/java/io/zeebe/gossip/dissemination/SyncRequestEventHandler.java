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
package io.zeebe.gossip.dissemination;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.gossip.GossipContext;
import io.zeebe.gossip.GossipSyncRequestHandler;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.GossipEvent;
import io.zeebe.gossip.protocol.GossipEventConsumer;
import io.zeebe.gossip.protocol.GossipEventSender;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.ReusableObjectList;
import io.zeebe.util.collection.Tuple;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class SyncRequestEventHandler implements GossipEventConsumer
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private final MembershipList membershipList;
    private final CustomEventSyncResponseSupplier customEventSyncRequestSupplier;
    private final List<Tuple<DirectBuffer, GossipSyncRequestHandler>> handlers = new ArrayList<>();

    private final GossipEventSender gossipEventSender;
    private final ReusableObjectList<GossipSyncRequest> requests = new ReusableObjectList<>(() -> new GossipSyncRequest());

    private final ActorControl actor;

    public SyncRequestEventHandler(GossipContext context, CustomEventSyncResponseSupplier customEventSyncRequestSupplier, ActorControl actorControl)
    {
        this.membershipList = context.getMembershipList();
        this.customEventSyncRequestSupplier = customEventSyncRequestSupplier;
        this.actor = actorControl;
        this.gossipEventSender = context.getGossipEventSender();
    }

    @Override
    public void accept(GossipEvent event, long requestId, int streamId)
    {
        final List<ActorFuture<Void>> futures = new ArrayList<>();
        for (Tuple<DirectBuffer, GossipSyncRequestHandler> tuple : handlers)
        {
            final GossipSyncRequest request = requests.add();
            request.wrap(tuple.getLeft());

            LOG.trace("Request SYNC data for custom event type '{}'", bufferAsString(tuple.getLeft()));

            final GossipSyncRequestHandler handler = tuple.getRight();
            final ActorFuture<Void> future = handler.onSyncRequest(request);
            futures.add(future);
        }

        actor.runOnCompletion(futures, (throwable) ->
        {
            if (throwable == null)
            {
                actor.submit(() -> sendSyncResponse(requestId, streamId));
            }
            else
            {
                Loggers.GOSSIP_LOGGER.warn("Can't produce sync response.", throwable);
            }
        });
    }

    private void sendSyncResponse(long requestId, int streamId)
    {
        for (GossipSyncRequest request : requests)
        {
            for (GossipSyncResponsePart response : request.getResponse())
            {
                final SocketAddress address = response.getAddress();

                final Member member = membershipList.getMemberOrSelf(address);
                if (member != null)
                {
                    final GossipTerm term = member.getTermForEventType(request.getType());
                    if (term != null)
                    {
                        customEventSyncRequestSupplier.add()
                            .type(request.getType())
                            .senderAddress(member.getAddress())
                            .senderGossipTerm(term)
                            .payload(response.getPayload());
                    }
                    else
                    {
                        LOG.warn("Ignore sync response with type '{}' and sender '{}'. Event type is unknown. ", bufferAsString(request.getType()), address);
                    }
                }
                else
                {
                    LOG.warn("Ignore sync response with type '{}' and sender '{}'. Sender is unknown. ", bufferAsString(request.getType()), address);
                }
            }
        }

        LOG.trace("Send SYNC response");
        gossipEventSender.responseSync(requestId, streamId);
    }

    public void registerSyncRequestHandler(DirectBuffer eventType, GossipSyncRequestHandler handler)
    {
        final Tuple<DirectBuffer, GossipSyncRequestHandler> tuple = new Tuple<>(BufferUtil.cloneBuffer(eventType), handler);
        handlers.add(tuple);
    }
}
