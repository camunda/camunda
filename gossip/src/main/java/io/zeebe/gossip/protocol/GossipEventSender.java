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

import static io.zeebe.transport.ClientTransport.UNKNOWN_NODE_ID;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;

public class GossipEventSender {

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
      GossipEventFactory gossipEventFactory) {
    this.clientTransport = clientTransport;
    this.serverTransport = serverTransport;
    this.membershipList = membershipList;

    this.gossipFailureDetectionEvent = gossipEventFactory.createFailureDetectionEvent();
    this.gossipSyncRequestEvent = gossipEventFactory.createSyncRequestEvent();
    this.gossipSyncResponseEvent = gossipEventFactory.createSyncResponseEvent();
  }

  public ActorFuture<ClientResponse> sendPing(int nodeId, Duration timeout) {
    gossipFailureDetectionEvent
        .reset()
        .eventType(GossipEventType.PING)
        .senderId(membershipList.self().getId());

    return sendEventTo(gossipFailureDetectionEvent, nodeId, timeout);
  }

  public ActorFuture<ClientResponse> sendPingReq(int nodeId, int probeMemberId, Duration timeout) {
    gossipFailureDetectionEvent
        .reset()
        .eventType(GossipEventType.PING_REQ)
        .probeMemberId(probeMemberId)
        .senderId(membershipList.self().getId());

    return sendEventTo(gossipFailureDetectionEvent, nodeId, timeout);
  }

  // initial sync request on join
  public ActorFuture<ClientResponse> sendSyncRequest(
      int nodeId, RemoteAddress remoteAddress, Duration timeout) {
    clientTransport.registerEndpoint(nodeId, remoteAddress.getAddress());
    return sendSyncRequest(nodeId, timeout);
  }

  public ActorFuture<ClientResponse> sendSyncRequest(int nodeId, Duration timeout) {
    gossipSyncRequestEvent
        .reset()
        .eventType(GossipEventType.SYNC_REQUEST)
        .senderId(membershipList.self().getId());

    return sendEventTo(gossipSyncRequestEvent, nodeId, timeout);
  }

  public void responseAck(long requestId, int streamId) {
    gossipFailureDetectionEvent
        .reset()
        .eventType(GossipEventType.ACK)
        .senderId(membershipList.self().getId());

    responseTo(gossipFailureDetectionEvent, requestId, streamId);
  }

  public void responseSync(long requestId, int streamId) {
    gossipSyncResponseEvent
        .reset()
        .eventType(GossipEventType.SYNC_RESPONSE)
        .senderId(membershipList.self().getId());

    responseTo(gossipSyncResponseEvent, requestId, streamId);
  }

  private ActorFuture<ClientResponse> sendEventTo(GossipEvent event, int nodeId, Duration timeout) {
    return clientTransport.getOutput().sendRequest(nodeId, event, timeout);
  }

  private void responseTo(GossipEvent event, long requestId, int streamId) {
    serverResponse.reset().writer(event).requestId(requestId).remoteStreamId(streamId);

    try {
      serverTransport.getOutput().sendResponse(serverResponse);
    } catch (Throwable t) {
      Loggers.GOSSIP_LOGGER.error("Error on sending response.", t);
      // ignore
    }
  }

  /** Only use if node id is not known, i.e. on initial join with contact points */
  public ActorFuture<ClientResponse> sendPing(SocketAddress socketAddress, Duration timeout) {
    gossipFailureDetectionEvent
        .reset()
        .eventType(GossipEventType.PING)
        .senderId(membershipList.self().getId());

    clientTransport.registerEndpoint(UNKNOWN_NODE_ID, socketAddress);

    return clientTransport
        .getOutput()
        .sendRequest(UNKNOWN_NODE_ID, gossipFailureDetectionEvent, timeout);
  }
}
