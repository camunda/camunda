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
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.membership.MembershipList;
import io.zeebe.gossip.membership.MembershipStatus;
import io.zeebe.gossip.membership.RoundRobinMemberIterator;
import io.zeebe.gossip.protocol.GossipEvent;
import io.zeebe.gossip.protocol.GossipEventFactory;
import io.zeebe.gossip.protocol.GossipEventSender;
import io.zeebe.transport.ClientResponse;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class PingController {
  private static final Logger LOG = Loggers.GOSSIP_LOGGER;

  private final ActorControl actor;

  private final GossipConfiguration configuration;
  private final MembershipList membershipList;
  private final RoundRobinMemberIterator propbeMemberIterator;
  private final RoundRobinMemberIterator indirectProbeMemberIterator;

  private final DisseminationComponent disseminationComponent;
  private final GossipEventSender gossipEventSender;
  private final GossipEventFactory gossipEventFactory;
  private final GossipEvent ackResponse;

  private final List<ActorFuture<ClientResponse>> indirectResponseFutures;

  private Member probeMember;

  public PingController(GossipContext context, ActorControl actorControl) {
    this.actor = actorControl;
    this.configuration = context.getConfiguration();
    this.gossipEventFactory = context.getGossipEventFactory();

    this.membershipList = context.getMembershipList();
    this.propbeMemberIterator = new RoundRobinMemberIterator(membershipList);
    this.indirectProbeMemberIterator = new RoundRobinMemberIterator(membershipList);

    this.disseminationComponent = context.getDisseminationComponent();
    this.gossipEventSender = context.getGossipEventSender();
    this.ackResponse = gossipEventFactory.createAckResponse();

    this.indirectResponseFutures = new ArrayList<>(configuration.getProbeIndirectNodes());
  }

  public void sendPing() {
    if (propbeMemberIterator.hasNext()) {
      probeMember = propbeMemberIterator.next();

      LOG.trace("Send PING to '{}'", probeMember.getId());

      final ActorFuture<ClientResponse> responseFuture =
          gossipEventSender.sendPing(probeMember.getId(), configuration.getProbeTimeoutDuration());

      actor.runOnCompletion(
          responseFuture,
          (response, failure) -> {
            if (failure == null) {
              LOG.trace("Received ACK from '{}'", probeMember.getId());

              processAckResponse(response);

              actor.runDelayed(configuration.getProbeIntervalDuration(), this::sendPing);
            } else {
              LOG.trace("Doesn't receive ACK from '{}'", probeMember.getId());

              actor.submit(this::sendPingReq);
            }
          });
    } else {
      LOG.trace("Stop to send PING. No members left.");
    }
  }

  private void sendPingReq() {
    indirectResponseFutures.clear();

    final int probeNodes =
        Math.min(configuration.getProbeIndirectNodes(), membershipList.size() - 1);

    for (int n = 0; n < probeNodes; ) {
      final Member member = indirectProbeMemberIterator.next();

      if (member != probeMember) {
        LOG.trace("Send PING-REQ to '{}' to probe '{}'", member.getId(), probeMember.getId());

        final ActorFuture<ClientResponse> responseFuture =
            gossipEventSender.sendPingReq(
                member.getId(),
                probeMember.getId(),
                configuration.getProbeIndirectTimeoutDuration());
        indirectResponseFutures.add(responseFuture);

        n += 1;
      }
    }

    if (indirectResponseFutures.isEmpty()) {
      actor.runDelayed(configuration.getProbeIntervalDuration(), this::sendPing);
    } else {
      actor.runOnFirstCompletion(
          indirectResponseFutures,
          (response, failure) -> {
            if (failure == null) {
              LOG.trace("Received ACK of PING-REQ from '{}'", probeMember.getId());

              processAckResponse(response);

              actor.runDelayed(configuration.getProbeIntervalDuration(), this::sendPing);
            } else {
              LOG.trace("Doesn't receive any ACK of PING-REQ to probe '{}'", probeMember.getId());

              actor.submit(this::sendSuspect);
            }
          },
          this::processAckResponse);
    }
  }

  private void processAckResponse(ClientResponse response) {
    final DirectBuffer responseBuffer = response.getResponseBuffer();
    ackResponse.wrap(responseBuffer, 0, responseBuffer.capacity());
  }

  private void sendSuspect() {
    if (probeMember.getStatus() == MembershipStatus.ALIVE) {
      LOG.debug("Spread SUSPECT event of member '{}'", probeMember.getId());

      membershipList.suspectMember(probeMember.getId(), probeMember.getTerm());

      disseminationComponent
          .addMembershipEvent()
          .memberId(probeMember.getId())
          .type(MembershipEventType.SUSPECT)
          .gossipTerm(probeMember.getTerm());
    }

    actor.runDelayed(configuration.getProbeIntervalDuration(), this::sendPing);
  }
}
