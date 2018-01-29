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
package io.zeebe.gossip.protocol;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.transport.*;
import org.agrona.DirectBuffer;

public class GossipEventSender
{
    private static final ClientRequest FAILED_REQUEST = new FailedClientRequest();

    private final ServerResponse serverResponse = new ServerResponse();

    private final ClientTransport clientTransport;
    private final ServerTransport serverTransport;

    private final MembershipList membershipList;

    private final GossipEvent gossipFailureDetectionEvent;
    private final GossipEvent gossipSyncRequestEvent;
    private final GossipEvent gossipSyncResponseEvent;

    public GossipEventSender(
            ClientTransport clientTransport,
            ServerTransport serverTransport,
            MembershipList membershipList,
            GossipEventFactory gossipEventFactory)
    {
        this.clientTransport = clientTransport;
        this.serverTransport = serverTransport;
        this.membershipList = membershipList;

        this.gossipFailureDetectionEvent = gossipEventFactory.createFailureDetectionEvent();
        this.gossipSyncRequestEvent = gossipEventFactory.createSyncRequestEvent();
        this.gossipSyncResponseEvent = gossipEventFactory.createSyncResponseEvent();
    }

    public ClientRequest sendPing(SocketAddress receiver)
    {
        gossipFailureDetectionEvent
                .reset()
                .eventType(GossipEventType.PING)
                .sender(membershipList.self().getAddress());

        return sendEventTo(gossipFailureDetectionEvent, receiver);
    }

    public ClientRequest sendPingReq(SocketAddress receiver, SocketAddress probeMember)
    {
        gossipFailureDetectionEvent
            .reset()
            .eventType(GossipEventType.PING_REQ)
            .probeMember(probeMember)
            .sender(membershipList.self().getAddress());

        return sendEventTo(gossipFailureDetectionEvent, receiver);
    }

    public ClientRequest sendSyncRequest(SocketAddress receiver)
    {
        gossipSyncRequestEvent
            .reset()
            .eventType(GossipEventType.SYNC_REQUEST)
            .sender(membershipList.self().getAddress());

        return sendEventTo(gossipSyncRequestEvent, receiver);
    }

    public void responseAck(long requestId, int streamId)
    {
        gossipFailureDetectionEvent
            .reset()
            .eventType(GossipEventType.ACK)
            .sender(membershipList.self().getAddress());

        responseTo(gossipFailureDetectionEvent, requestId, streamId);
    }

    public void responseSync(long requestId, int streamId)
    {
        gossipSyncResponseEvent
            .reset()
            .eventType(GossipEventType.SYNC_RESPONSE)
            .sender(membershipList.self().getAddress());

        responseTo(gossipSyncResponseEvent, requestId, streamId);
    }

    private ClientRequest sendEventTo(GossipEvent event, SocketAddress receiver)
    {
        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(receiver);

        try
        {
            final ClientRequest request = clientTransport.getOutput().sendRequestWithRetry(remoteAddress, event);

            return request != null ? request : FAILED_REQUEST;
        }
        catch (Throwable t)
        {
            Loggers.GOSSIP_LOGGER.error("Error on sending request.", t);
            // ignore
            return FAILED_REQUEST;
        }
    }

    private void responseTo(GossipEvent event, long requestId, int streamId)
    {
        serverResponse
            .reset()
            .writer(event)
            .requestId(requestId)
            .remoteStreamId(streamId);

        try
        {
            serverTransport.getOutput().sendResponse(serverResponse);
        }
        catch (Throwable t)
        {
            Loggers.GOSSIP_LOGGER.error("Error on sending response.", t);
            // ignore
        }
    }

    private static class FailedClientRequest implements ClientRequest
    {
        @Override
        public boolean isFailed()
        {
            return true;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return false;
        }

        @Override
        public boolean isCancelled()
        {
            return false;
        }

        @Override
        public boolean isDone()
        {
            return true;
        }

        @Override
        public DirectBuffer get() throws InterruptedException, ExecutionException
        {
            return null;
        }

        @Override
        public DirectBuffer get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
        {
            return null;
        }

        @Override
        public long getRequestId()
        {
            return -1L;
        }

        @Override
        public DirectBuffer join()
        {
            throw new RuntimeException("Request failed.");
        }

        @Override
        public void close()
        {
        }
    }

}
