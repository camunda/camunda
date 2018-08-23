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

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.gossip.GossipContext;
import io.zeebe.gossip.GossipMath;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.membership.MembershipStatus;
import io.zeebe.gossip.protocol.GossipEvent;
import io.zeebe.gossip.protocol.GossipEventFactory;
import io.zeebe.gossip.protocol.GossipEventSender;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class JoinController {
  private static final Logger LOG = Loggers.GOSSIP_LOGGER;

  private final ActorControl actor;

  private final GossipConfiguration configuration;

  private final Member self;
  private final MembershipList membershipList;

  private final DisseminationComponent disseminationComponent;

  private final GossipEventSender gossipEventSender;
  private final GossipEventFactory gossipEventFactory;

  private final GossipEvent ackResponse;
  private final GossipEvent syncResponse;

  private List<SocketAddress> contactPoints;

  private boolean isJoined;
  private CompletableActorFuture<Void> joinFuture;
  private CompletableActorFuture<Void> leaveFuture;

  public JoinController(GossipContext context, ActorControl actor) {
    this.actor = actor;
    this.configuration = context.getConfiguration();

    this.self = context.getMembershipList().self();
    this.membershipList = context.getMembershipList();
    this.disseminationComponent = context.getDisseminationComponent();
    this.gossipEventSender = context.getGossipEventSender();
    this.gossipEventFactory = context.getGossipEventFactory();

    this.ackResponse = gossipEventFactory.createAckResponse();
    this.syncResponse = gossipEventFactory.createSyncResponse();
  }

  public void join(List<SocketAddress> contactPoints, CompletableActorFuture<Void> future) {
    if (isJoined) {
      future.completeExceptionally(new IllegalStateException("Already joined."));
    } else if (contactPoints == null || contactPoints.isEmpty()) {
      future.completeExceptionally(
          new IllegalArgumentException("Can't join cluster without contact points."));
    } else if (joinFuture != null) {
      future.completeExceptionally(new IllegalStateException(("Currently join in progress.")));
    } else {
      this.joinFuture = future;
      this.contactPoints = contactPoints;

      sendJoinEvent();
    }
  }

  private void sendJoinEvent() {
    final List<ActorFuture<ClientResponse>> requestFutures = new ArrayList<>(contactPoints.size());

    self.getTerm().increment();

    for (SocketAddress contactPoint : contactPoints) {
      LOG.trace("Spread JOIN event to contact point '{}'", contactPoint);

      disseminationComponent
          .addMembershipEvent()
          .memberId(self.getId())
          .type(MembershipEventType.JOIN)
          .gossipTerm(self.getTerm());

      final ActorFuture<ClientResponse> requestFuture =
          gossipEventSender.sendPing(contactPoint, configuration.getJoinTimeoutDuration());
      requestFutures.add(requestFuture);
    }

    actor.runOnFirstCompletion(
        requestFutures,
        (response, failure) -> {
          if (failure == null) {
            processAckResponse(response);

            actor.submit(
                () -> sendSyncRequest(ackResponse.getSenderId(), response.getRemoteAddress()));
          } else {
            LOG.info(
                "Failed to contact any of '{}'. Try again in {}",
                contactPoints,
                configuration.getJoinInterval());

            actor.runDelayed(configuration.getJoinIntervalDuration(), this::sendJoinEvent);
          }
        },
        this::processAckResponse);
  }

  private void processAckResponse(ClientResponse response) {
    final DirectBuffer responseBuffer = response.getResponseBuffer();
    ackResponse.wrap(responseBuffer, 0, responseBuffer.capacity());
  }

  private void sendSyncRequest(final int nodeId, final RemoteAddress remoteAddress) {
    LOG.trace("Send SYNC request to node '{}' with address '{}'", nodeId, remoteAddress);

    final ActorFuture<ClientResponse> requestFuture =
        gossipEventSender.sendSyncRequest(
            nodeId, remoteAddress, configuration.getSyncTimeoutDuration());

    actor.runOnCompletion(
        requestFuture,
        (request, failure) -> {
          if (failure == null) {
            LOG.debug("Received SYNC response.");

            // process response
            final DirectBuffer response = request.getResponseBuffer();
            syncResponse.wrap(response, 0, response.capacity());

            isJoined = true;
            joinFuture.complete(null);
            joinFuture = null;

            LOG.debug("Joined cluster successfully");
          } else {
            LOG.debug(
                "Failed to receive SYNC response from node '{}'. Try again in {}",
                nodeId,
                configuration.getJoinInterval());

            actor.runDelayed(configuration.getJoinIntervalDuration(), this::sendJoinEvent);
          }
        });
  }

  public void leave(CompletableActorFuture<Void> future) {
    if (!isJoined) {
      future.complete(null);
    } else if (leaveFuture != null) {
      future.completeExceptionally(new IllegalStateException(("Currently leave in progress.")));
    } else {
      this.leaveFuture = future;
      sendLeaveEvent();
    }
  }

  private void sendLeaveEvent() {
    final Member self = membershipList.self();

    self.getTerm().increment();

    disseminationComponent
        .addMembershipEvent()
        .memberId(self.getId())
        .type(MembershipEventType.LEAVE)
        .gossipTerm(self.getTerm());

    // spread LEAVE event to random members
    final List<Member> members = new ArrayList<>(membershipList.getMembersView());
    Collections.shuffle(members);

    final int clusterSize = membershipList.size();
    final int multiplier = configuration.getRetransmissionMultiplier();
    final int spreadLimit =
        Math.min(GossipMath.gossipPeriodsToSpread(multiplier, clusterSize), clusterSize);

    final List<ActorFuture<ClientResponse>> requestFutures = new ArrayList<>(spreadLimit);

    int spreadCount = 0;
    for (int m = 0; m < members.size() && spreadCount < spreadLimit; m++) {
      final Member member = members.get(m);

      if (member.getStatus() == MembershipStatus.ALIVE) {
        LOG.trace("Spread LEAVE event to node '{}'", member.getId());

        final ActorFuture<ClientResponse> requestFuture =
            gossipEventSender.sendPing(member.getId(), configuration.getLeaveTimeoutDuration());
        requestFutures.add(requestFuture);

        spreadCount += 1;
      }
    }

    actor.runOnCompletion(
        requestFutures,
        (failure) -> {
          if (failure == null) {
            LOG.info("Left cluster successfully");
          } else {
            LOG.info(
                "Left cluster but timeout is reached before event is confirmed by all members");
          }

          isJoined = false;
          leaveFuture.complete(null);
          leaveFuture = null;
        });
  }
}
