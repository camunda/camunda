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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class GossipJoinTest {
  private GossipRule gossip1 = new GossipRule(1);
  private GossipRule gossip2 = new GossipRule(2);
  private GossipRule gossip3 = new GossipRule(3);

  @Rule public GossipClusterRule cluster = new GossipClusterRule(gossip1, gossip2, gossip3);

  @Rule public Timeout timeout = Timeout.seconds(10);

  @Test
  public void shouldSendSyncRequestOnJoin() {
    // when
    gossip2.join(gossip1).join();

    // then
    assertThat(gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip2)).isTrue();
    assertThat(gossip2.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1)).isTrue();
  }

  @Test
  public void shouldSendSyncRequestAfterReconnect() {
    // given
    gossip2.join(gossip1).join();
    gossip3.join(gossip1).join();
    cluster.interruptConnectionBetween(gossip1, gossip2);
    gossip1.clearReceivedEvents();
    gossip2.clearReceivedEvents();
    gossip3.clearReceivedEvents();

    // when
    cluster.waitUntil(
        () ->
            gossip3.receivedEvent(GossipEventType.SYNC_REQUEST, gossip1)
                && gossip3.receivedEvent(GossipEventType.SYNC_REQUEST, gossip1));
    gossip3.clearReceivedEvents();
    cluster.waitUntil(
        () ->
            gossip3.receivedEvent(GossipEventType.SYNC_REQUEST, gossip1)
                && gossip3.receivedEvent(GossipEventType.SYNC_REQUEST, gossip1));

    // then
    assertThat(gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip2)).isFalse();
    assertThat(gossip2.receivedEvent(GossipEventType.SYNC_REQUEST, gossip1)).isFalse();

    // when
    cluster.reconnect(gossip1, gossip2);

    // then
    cluster.waitUntil(
        () ->
            (gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip2)
                    && gossip2.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1))
                && (gossip2.receivedEvent(GossipEventType.SYNC_REQUEST, gossip1)
                    && gossip1.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip2)));
  }

  @Test
  public void shouldRepeatSyncRequestAfterAnInterval() {
    // given
    gossip2.join(gossip1).join();

    // when
    gossip1.clearReceivedEvents();
    gossip2.clearReceivedEvents();
    assertThat(gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip2)).isFalse();
    assertThat(gossip2.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1)).isFalse();

    // then
    cluster.waitUntil(
        () ->
            gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip2)
                && gossip2.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1));
  }

  @Test
  public void shouldSendSyncRequestOnAllNodes() {
    // given
    gossip2.join(gossip1).join();

    // when
    gossip1.clearReceivedEvents();
    gossip2.clearReceivedEvents();
    assertThat(gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip2)).isFalse();
    assertThat(gossip2.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1)).isFalse();

    // then
    cluster.waitUntil(
        () ->
            (gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip2)
                    && gossip2.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1))
                && (gossip2.receivedEvent(GossipEventType.SYNC_REQUEST, gossip1)
                    && gossip1.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip2)));
  }

  @Test
  public void shouldSpreadJoinEvent() {
    // when
    gossip2.join(gossip1).join();

    // then
    assertThat(gossip1.receivedMembershipEvent(MembershipEventType.JOIN, gossip2)).isTrue();
  }

  @Test
  public void shouldAddMemberOnJoin() {
    // when
    gossip2.join(gossip1).join();

    // then
    assertThat(gossip1.hasMember(gossip2)).isTrue();
    assertThat(gossip2.hasMember(gossip1)).isTrue();
  }

  @Test
  public void shouldSyncMembersOnJoin() {
    // given
    gossip2.join(gossip1).join();

    // when
    gossip3.join(gossip1).join();

    // then
    assertThat(gossip3.hasMember(gossip1)).isTrue();
    assertThat(gossip3.hasMember(gossip2)).isTrue();
  }

  @Test
  public void shouldJoinWithMultipleContactPoints() {
    // given
    gossip2.join(gossip1).join();

    // when
    gossip3.join(gossip1, gossip2).join();

    // then

    // either gossip 1 or gossip 2 or both have received the join event
    final boolean hasJoined =
        gossip1.receivedMembershipEvent(MembershipEventType.JOIN, gossip3)
            || gossip2.receivedMembershipEvent(MembershipEventType.JOIN, gossip3);

    assertThat(hasJoined).isTrue();
  }

  @Test
  public void shouldJoinDifferentNodes() {
    // given
    final ActorFuture<Void> joinGossip2 = gossip2.join(gossip1);
    final ActorFuture<Void> joinGossip3 = gossip3.join(gossip2);

    // when - sync response are spread via ping
    joinGossip2.join();
    joinGossip3.join();

    // then
    cluster.waitUntil(() -> gossip3.hasMember(gossip1));
    cluster.waitUntil(() -> gossip1.hasMember(gossip3));

    assertThat(gossip1.hasMember(gossip2)).isTrue();
    assertThat(gossip2.hasMember(gossip3)).isTrue();
    assertThat(gossip2.hasMember(gossip1)).isTrue();
    assertThat(gossip3.hasMember(gossip2)).isTrue();
  }

  @Test
  public void shouldCompleteFutureWithFailureWhenAlreadyJoined() {
    // given
    gossip2.join(gossip1).join();

    // when
    final ActorFuture<Void> future = gossip2.join(gossip1);

    // then
    assertThatThrownBy(() -> future.join()).hasMessageContaining("Already joined.");
  }

  @Test
  public void shouldJoinAfterLeave() {
    // given
    gossip2.join(gossip1).join();

    gossip2.leave().join();

    // when
    gossip2.join(gossip1).join();

    // then
    assertThat(gossip1.hasMember(gossip2)).isTrue();
  }

  @Test
  public void shouldIncreaseGossipTermOnJoin() {
    // given
    gossip2.join(gossip1).join();
    gossip2.leave().join();

    // when
    gossip2.join(gossip1).join();

    // then
    final MembershipEvent leaveEvent =
        gossip1.getReceivedMembershipEvents(MembershipEventType.LEAVE, gossip2).findFirst().get();

    final MembershipEvent secondJoinEvent =
        gossip1
            .getReceivedMembershipEvents(MembershipEventType.JOIN, gossip2)
            .distinct()
            .skip(1)
            .findFirst()
            .get();

    assertThat(secondJoinEvent.getGossipTerm().isGreaterThan(leaveEvent.getGossipTerm())).isTrue();
  }

  @Test
  public void shouldFailToJoinWithoutContactPoints() {
    // when
    final ActorFuture<Void> future = gossip2.getController().join(Collections.emptyList());

    // then
    assertThatThrownBy(() -> future.join())
        .hasMessageContaining("Can't join cluster without contact points.");
  }
}
