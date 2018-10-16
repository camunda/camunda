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

import io.zeebe.gossip.GossipContext;
import io.zeebe.gossip.GossipSyncRequestHandler;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.protocol.GossipEvent;
import io.zeebe.gossip.protocol.GossipEventConsumer;
import io.zeebe.gossip.protocol.GossipEventSender;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.Reusable;
import io.zeebe.util.collection.ReusableObjectList;
import io.zeebe.util.collection.Tuple;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class SyncRequestEventHandler implements GossipEventConsumer {
  private static final Logger LOG = Loggers.GOSSIP_LOGGER;

  private final ActorControl actor;
  private final MembershipList membershipList;
  private final CustomEventSyncResponseSupplier customEventSyncRequestSupplier;
  private final GossipEventSender gossipEventSender;

  private final List<Tuple<DirectBuffer, GossipSyncRequestHandler>> handlers = new ArrayList<>();
  private final ReusableObjectList<GossipSyncRequest> syncRequests =
      new ReusableObjectList<>(GossipSyncRequest::new);

  private final ReusableObjectList<ReceivedRequest> receivedRequests =
      new ReusableObjectList<>(ReceivedRequest::new);

  public SyncRequestEventHandler(
      GossipContext context,
      CustomEventSyncResponseSupplier customEventSyncRequestSupplier,
      ActorControl actor) {
    this.membershipList = context.getMembershipList();
    this.customEventSyncRequestSupplier = customEventSyncRequestSupplier;
    this.actor = actor;
    this.gossipEventSender = context.getGossipEventSender();
  }

  @Override
  public void accept(GossipEvent event, long requestId, int streamId) {
    receivedRequests.add().wrap(requestId, streamId);

    if (receivedRequests.size() == 1) {
      if (!handlers.isEmpty()) {
        final List<ActorFuture<Void>> syncHandlerFutures = new ArrayList<>();

        for (Tuple<DirectBuffer, GossipSyncRequestHandler> tuple : handlers) {
          final GossipSyncRequest request = syncRequests.add();
          request.wrap(tuple.getLeft());

          LOG.trace(
              "Request SYNC data for custom event type '{}'", bufferAsString(tuple.getLeft()));

          final GossipSyncRequestHandler handler = tuple.getRight();
          final ActorFuture<Void> future = handler.onSyncRequest(request);
          syncHandlerFutures.add(future);
        }

        actor.runOnCompletion(
            syncHandlerFutures,
            (failure) -> {
              if (failure == null) {
                actor.submit(this::sendSyncResponse);
              } else {
                LOG.warn("Can't produce sync response.", failure);
              }
            });
      } else {
        actor.submit(this::sendSyncResponse);
      }
    } else {
      // don't request the data again if already requested
      // - instead, response the data from the ongoing request
      customEventSyncRequestSupplier.increaseSpreadLimit();
    }
  }

  private void sendSyncResponse() {
    for (GossipSyncRequest request : syncRequests) {
      for (GossipSyncResponsePart response : request.getResponse()) {
        final int nodeId = response.getNodeId();

        final Member member = membershipList.getMemberOrSelf(response.getNodeId());
        if (member != null) {
          final GossipTerm term = member.getTermForEventType(request.getType());
          if (term != null) {
            customEventSyncRequestSupplier
                .add()
                .type(request.getType())
                .senderId(member.getId())
                .senderGossipTerm(term)
                .payload(response.getPayload());
          } else {
            LOG.debug(
                "Ignore sync response with type '{}' and sender id '{}'. Event type is unknown. ",
                bufferAsString(request.getType()),
                nodeId);
          }
        } else {
          LOG.debug(
              "Ignore sync response with type '{}' and sender id '{}'. Sender is unknown. ",
              bufferAsString(request.getType()),
              nodeId);
        }
      }
    }

    for (ReceivedRequest request : receivedRequests) {
      final long requestId = request.getRequestId();
      final int streamId = request.getStreamId();

      LOG.trace("Send SYNC response");
      gossipEventSender.responseSync(requestId, streamId);
    }

    syncRequests.clear();
    receivedRequests.clear();
    customEventSyncRequestSupplier.reset();
  }

  public void registerSyncRequestHandler(DirectBuffer eventType, GossipSyncRequestHandler handler) {
    final Tuple<DirectBuffer, GossipSyncRequestHandler> tuple =
        new Tuple<>(BufferUtil.cloneBuffer(eventType), handler);
    handlers.add(tuple);
  }

  private class ReceivedRequest implements Reusable {
    private long requestId;
    private int streamId;

    public void wrap(long requestId, int streamId) {
      this.requestId = requestId;
      this.streamId = streamId;
    }

    public long getRequestId() {
      return requestId;
    }

    public int getStreamId() {
      return streamId;
    }

    @Override
    public void reset() {
      requestId = -1L;
      streamId = -1;
    }
  }
}
