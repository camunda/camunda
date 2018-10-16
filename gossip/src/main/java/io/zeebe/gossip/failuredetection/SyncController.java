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
import io.zeebe.gossip.membership.RoundRobinMemberIterator;
import io.zeebe.gossip.protocol.GossipEvent;
import io.zeebe.gossip.protocol.GossipEventSender;
import io.zeebe.transport.ClientResponse;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class SyncController {
  private static final Logger LOG = Loggers.GOSSIP_LOGGER;

  private final RoundRobinMemberIterator memberIterator;
  private final GossipEventSender gossipEventSender;
  private final GossipEvent syncResponse;
  private final ActorControl actor;
  private final GossipConfiguration configuration;

  public SyncController(GossipContext context, ActorControl actorControl) {
    configuration = context.getConfiguration();

    actor = actorControl;
    final MembershipList membershipList = context.getMembershipList();
    this.memberIterator = new RoundRobinMemberIterator(membershipList);

    this.gossipEventSender = context.getGossipEventSender();
    this.syncResponse = context.getGossipEventFactory().createSyncResponse();
  }

  public void setupSyncRepetition() {
    actor.runAtFixedRate(
        configuration.getSyncIntervalDuration(),
        () -> {
          if (memberIterator.hasNext()) {
            final Member nextMember = memberIterator.next();
            LOG.debug("Send sync request to node '{}'.", nextMember.getId());

            final ActorFuture<ClientResponse> requestFuture =
                gossipEventSender.sendSyncRequest(
                    nextMember.getId(), configuration.getSyncTimeoutDuration());

            actor.runOnCompletion(
                requestFuture,
                (request, failure) -> {
                  if (failure == null) {
                    LOG.debug("Received SYNC response.");
                    final DirectBuffer response = request.getResponseBuffer();
                    syncResponse.wrap(response, 0, response.capacity());
                  } else {
                    LOG.debug(
                        "Failed to receive SYNC response from '{}'. Try again in {}",
                        nextMember.getId(),
                        configuration.getSyncIntervalDuration());
                  }
                });
          }
        });
  }
}
