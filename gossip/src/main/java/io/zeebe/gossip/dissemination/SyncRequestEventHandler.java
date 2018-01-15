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

import java.util.*;

import io.zeebe.gossip.*;
import io.zeebe.gossip.membership.*;
import io.zeebe.gossip.protocol.*;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.*;
import io.zeebe.util.state.*;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class SyncRequestEventHandler implements GossipEventConsumer
{
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    private static final int REQUEST_BUFFER_CAPACITY = 16;

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_SYNC_RECEIVED = 1;
    private static final int TRANSITION_RESPONSE_AVAILABLE = 2;

    private final MembershipList membershipList;
    private final CustomEventSyncResponseSupplier customEventSyncRequestSupplier;

    private final List<Tuple<DirectBuffer, GossipSyncRequestHandler>> handlers = new ArrayList<>();

    private final ReusableObjectList<ReceivedRequest> bufferedRequests = new ReusableObjectList<>(() -> new ReceivedRequest());

    private final StateMachine<Context> stateMachine;

    public SyncRequestEventHandler(GossipContext context, CustomEventSyncResponseSupplier customEventSyncRequestSupplier)
    {
        this.membershipList = context.getMembershipList();
        this.customEventSyncRequestSupplier = customEventSyncRequestSupplier;

        final WaitState<Context> awaitSyncState = ctx ->
        { };

        final RequestCustomEventState requestCustomEventState = new RequestCustomEventState();
        final AwaitCustomEventResponseState awaitCustomEventResponseState = new AwaitCustomEventResponseState();
        final SendSyncResponseState sendSyncResponseState = new SendSyncResponseState(context.getGossipEventSender());
        final PollRequestState pollRequestState = new PollRequestState();

        this.stateMachine = StateMachine.<Context> builder(Context::new)
                .initialState(awaitSyncState)
                .from(awaitSyncState).take(TRANSITION_SYNC_RECEIVED).to(requestCustomEventState)
                .from(requestCustomEventState).take(TRANSITION_DEFAULT).to(awaitCustomEventResponseState)
                .from(awaitCustomEventResponseState).take(TRANSITION_RESPONSE_AVAILABLE).to(sendSyncResponseState)
                .from(sendSyncResponseState).take(TRANSITION_DEFAULT).to(pollRequestState)
                .from(pollRequestState).take(TRANSITION_DEFAULT).to(awaitSyncState)
                .from(pollRequestState).take(TRANSITION_SYNC_RECEIVED).to(requestCustomEventState)
                .build();
    }

    @Override
    public void accept(GossipEvent event, long requestId, int streamId)
    {
        final boolean success = stateMachine.tryTake(TRANSITION_SYNC_RECEIVED);
        if (success)
        {
            final Context context = stateMachine.getContext();
            context.requestId = requestId;
            context.streamId = streamId;
        }
        else
        {
            if (bufferedRequests.size() <= REQUEST_BUFFER_CAPACITY)
            {
                if (LOG.isTraceEnabled())
                {
                    LOG.trace("Buffer SYNC-REQUEST from '{}'. The previous request isn't completed yet.", event.getSender());
                }

                bufferedRequests.add().wrap(requestId, streamId);
            }
            else
            {
                LOG.info("Reject SYNC-REQUEST from '{}'. Buffer capacity is reached.", event.getSender());
            }
        }
    }

    public void registerSyncRequestHandler(DirectBuffer eventType, GossipSyncRequestHandler handler)
    {
        final Tuple<DirectBuffer, GossipSyncRequestHandler> tuple = new Tuple<>(BufferUtil.cloneBuffer(eventType), handler);
        handlers.add(tuple);
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    private class Context extends SimpleStateMachineContext
    {
        private long requestId;
        private int streamId;

        private final ReusableObjectList<GossipSyncRequest> requests = new ReusableObjectList<>(() -> new GossipSyncRequest());

        Context(StateMachine<Context> stateMachine)
        {
            super(stateMachine);
        }
    }

    private class RequestCustomEventState implements TransitionState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            for (Tuple<DirectBuffer, GossipSyncRequestHandler> tuple : handlers)
            {
                final GossipSyncRequest request = context.requests.add();
                request.wrap(tuple.getLeft());

                if (LOG.isTraceEnabled())
                {
                    LOG.trace("Request SYNC data for custom event type '{}'", bufferAsString(tuple.getLeft()));
                }

                final GossipSyncRequestHandler handler = tuple.getRight();
                handler.onSyncRequest(request);
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    private class AwaitCustomEventResponseState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            boolean allResponsesAvailable = true;

            final Iterator<GossipSyncRequest> requests = context.requests.iterator();
            while (requests.hasNext() && allResponsesAvailable)
            {
                allResponsesAvailable &= requests.next().isDone();
            }

            if (allResponsesAvailable)
            {
                context.take(TRANSITION_RESPONSE_AVAILABLE);
            }
        }
    }

    private class SendSyncResponseState implements TransitionState<Context>
    {
        private final GossipEventSender gossipEventSender;

        SendSyncResponseState(GossipEventSender gossipEventSender)
        {
            this.gossipEventSender = gossipEventSender;
        }

        @Override
        public void work(Context context) throws Exception
        {
            for (GossipSyncRequest request : context.requests)
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

            if (LOG.isTraceEnabled())
            {
                LOG.trace("Send SYNC response");
            }

            gossipEventSender.responseSync(context.requestId, context.streamId);

            context.requests.clear();
            context.take(TRANSITION_DEFAULT);
        }
    }

    private class PollRequestState implements TransitionState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            final ReceivedRequest request = bufferedRequests.poll();
            if (request != null)
            {
                context.requestId = request.getRequestId();
                context.streamId = request.getStreamId();

                context.take(TRANSITION_SYNC_RECEIVED);
            }
            else
            {
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    private class ReceivedRequest implements Reusable
    {
        private long requestId;
        private int streamId;

        public void wrap(long requestId, int streamId)
        {
            this.requestId = requestId;
            this.streamId = streamId;
        }

        public long getRequestId()
        {
            return requestId;
        }

        public int getStreamId()
        {
            return streamId;
        }

        @Override
        public void reset()
        {
            requestId = -1L;
            streamId = -1;
        }
    }

}
