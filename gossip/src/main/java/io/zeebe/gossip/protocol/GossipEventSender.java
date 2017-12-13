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

import java.util.concurrent.*;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.transport.*;
import org.agrona.DirectBuffer;

public class GossipEventSender
{
    private static final ClientRequest FAILED_REQUEST = new FailedClientRequest();

    private final ServerResponse serverResponse = new ServerResponse();

    private final ClientTransport clientTransport;
    private final ServerTransport serverTransport;

    private final DisseminationComponent disseminationComponent;
    private final MembershipList memberList;

    private final GossipEvent gossipFailureDetectionEvent;
    private final GossipEvent gossipSyncEvent;

    public GossipEventSender(
            ClientTransport clientTransport,
            ServerTransport serverTransport,
            MembershipList memberList,
            DisseminationComponent disseminationComponent,
            GossipEventFactory eventFactory)
    {
        this.clientTransport = clientTransport;
        this.serverTransport = serverTransport;
        this.disseminationComponent = disseminationComponent;
        this.memberList = memberList;

        this.gossipFailureDetectionEvent = eventFactory.createFailureDetectionEvent();
        this.gossipSyncEvent = eventFactory.createSyncEvent();
    }

    public ClientRequest sendPing(SocketAddress receiver)
    {
        gossipFailureDetectionEvent
            .reset()
            .eventType(GossipEventType.PING);

        return sendEventTo(gossipFailureDetectionEvent, receiver);
    }

    public ClientRequest sendPingReq(SocketAddress receiver, SocketAddress probeMember)
    {
        gossipFailureDetectionEvent
            .reset()
            .eventType(GossipEventType.PING_REQ)
            .probeMember(probeMember);

        return sendEventTo(gossipFailureDetectionEvent, receiver);
    }

    public ClientRequest sendSyncRequest(SocketAddress receiver)
    {
        gossipSyncEvent
            .reset()
            .eventType(GossipEventType.SYNC_REQUEST);

        return sendEventTo(gossipSyncEvent, receiver);
    }

    public void responseAck(long requestId, int streamId)
    {
        gossipFailureDetectionEvent
            .reset()
            .eventType(GossipEventType.ACK);

        responseTo(gossipFailureDetectionEvent, requestId, streamId);
    }

    public void responseSync(long requestId, int streamId)
    {
        gossipSyncEvent
            .reset()
            .eventType(GossipEventType.SYNC_RESPONSE);

        responseTo(gossipSyncEvent, requestId, streamId);
    }

    private ClientRequest sendEventTo(GossipEvent event, SocketAddress receiver)
    {
        prepareGossipEvent(event);

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(receiver);

        try
        {
            final ClientRequest request = clientTransport.getOutput().sendRequest(remoteAddress, event);

            return request != null ? request : FAILED_REQUEST;
        }
        catch (Throwable t)
        {
            // ignore
            return FAILED_REQUEST;
        }
    }

    private void responseTo(GossipEvent event, long requestId, int streamId)
    {
        prepareGossipEvent(event);

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
            // ignore
        }
    }

    private void prepareGossipEvent(GossipEvent event)
    {
        disseminationComponent.clearSpreadEvents();

        event.sender(memberList.self().getAddress());
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
