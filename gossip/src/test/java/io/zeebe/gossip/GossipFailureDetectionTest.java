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
package io.zeebe.gossip;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class GossipFailureDetectionTest {
  private static final GossipConfiguration CONFIGURATION =
      new GossipConfiguration().setSuspicionMultiplier(10);

  private GossipRule gossip1 = new GossipRule(1);
  private GossipRule gossip2 = new GossipRule(2);
  private GossipRule gossip3 = new GossipRule(3);

  @Rule
  public GossipClusterRule cluster =
      new GossipClusterRule(CONFIGURATION, gossip1, gossip2, gossip3);

  @Rule public Timeout timeout = Timeout.seconds(20);

  @Before
  public void init() {
    gossip2.join(gossip1).join();
    gossip3.join(gossip1).join();

    cluster.waitUntil(() -> gossip2.hasMember(gossip3));
    cluster.waitUntil(() -> gossip3.hasMember(gossip2));

    gossip1.clearReceivedEvents();
    gossip2.clearReceivedEvents();
    gossip3.clearReceivedEvents();
  }

  @Test
  public void shouldSendPingAndAck() {
    // send PING and ACK events and verify that

    cluster.waitUntil(() -> gossip1.receivedEvent(GossipEventType.PING, gossip2));
    cluster.waitUntil(() -> gossip1.receivedEvent(GossipEventType.PING, gossip3));

    cluster.waitUntil(() -> gossip2.receivedEvent(GossipEventType.PING, gossip1));
    cluster.waitUntil(() -> gossip2.receivedEvent(GossipEventType.PING, gossip3));

    cluster.waitUntil(() -> gossip3.receivedEvent(GossipEventType.PING, gossip1));
    cluster.waitUntil(() -> gossip3.receivedEvent(GossipEventType.PING, gossip2));

    cluster.waitUntil(() -> gossip1.receivedEvent(GossipEventType.ACK, gossip2));
    cluster.waitUntil(() -> gossip1.receivedEvent(GossipEventType.ACK, gossip3));

    cluster.waitUntil(() -> gossip2.receivedEvent(GossipEventType.ACK, gossip1));
    cluster.waitUntil(() -> gossip2.receivedEvent(GossipEventType.ACK, gossip3));

    cluster.waitUntil(() -> gossip3.receivedEvent(GossipEventType.ACK, gossip1));
    cluster.waitUntil(() -> gossip3.receivedEvent(GossipEventType.ACK, gossip2));
  }

  @Test
  public void shouldSendPingReqAndForwardAck() {
    // given
    cluster.interruptConnectionBetween(gossip1, gossip2);

    // when send PING-REQ (indirect PING) to probe member
    cluster.waitUntil(() -> gossip3.receivedEvent(GossipEventType.PING_REQ, gossip1));
    cluster.waitUntil(() -> gossip2.receivedEvent(GossipEventType.PING, gossip3));
    cluster.waitUntil(() -> gossip3.receivedEvent(GossipEventType.ACK, gossip2));
    cluster.waitUntil(() -> gossip1.receivedEvent(GossipEventType.ACK, gossip3));

    assertThat(gossip1.hasMember(gossip2)).isTrue();
    assertThat(gossip2.hasMember(gossip1)).isTrue();
  }

  @Test
  public void shouldSpreadSuspectEvent() {
    // given
    cluster.interruptConnectionBetween(gossip3, gossip1);
    cluster.interruptConnectionBetween(gossip3, gossip2);

    // when send SUSPECT event
    cluster.waitUntil(() -> gossip1.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3));
    cluster.waitUntil(() -> gossip2.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3));
  }

  @Test
  public void shouldSpreadConfirmEvent() {
    // given
    cluster.interruptConnectionBetween(gossip3, gossip1);
    cluster.interruptConnectionBetween(gossip3, gossip2);

    // when member is removed
    cluster.waitUntil(() -> !gossip1.hasMember(gossip3));
    cluster.waitUntil(() -> !gossip2.hasMember(gossip3));

    // then confirm event was spread
    cluster.waitUntil(() -> gossip1.receivedMembershipEvent(MembershipEventType.CONFIRM, gossip3));
    cluster.waitUntil(() -> gossip2.receivedMembershipEvent(MembershipEventType.CONFIRM, gossip3));
  }

  @Test
  public void shouldCounterSuspectEventIfAlive() {
    // given
    cluster.interruptConnectionBetween(gossip3, gossip1);
    cluster.interruptConnectionBetween(gossip3, gossip2);

    cluster.waitUntil(() -> gossip1.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3));
    cluster.waitUntil(() -> gossip2.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3));

    final MembershipEvent suspectEvent =
        gossip1.getReceivedMembershipEvents(MembershipEventType.SUSPECT, gossip3).findFirst().get();

    // when
    cluster.reconnect(gossip3, gossip1);
    cluster.reconnect(gossip3, gossip2);

    // then
    cluster.waitUntil(() -> gossip3.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3));

    cluster.waitUntil(
        () ->
            gossip1
                .getReceivedMembershipEvents(MembershipEventType.ALIVE, gossip3)
                .anyMatch(e -> e.getGossipTerm().isGreaterThan(suspectEvent.getGossipTerm())));
  }
}
