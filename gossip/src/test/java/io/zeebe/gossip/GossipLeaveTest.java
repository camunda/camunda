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

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import io.zeebe.util.sched.future.ActorFuture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class GossipLeaveTest {
  private GossipRule gossip1 = new GossipRule(1);
  private GossipRule gossip2 = new GossipRule(2);
  private GossipRule gossip3 = new GossipRule(3);

  @Rule public GossipClusterRule cluster = new GossipClusterRule(gossip1, gossip2, gossip3);

  @Rule public Timeout timeout = Timeout.seconds(10);

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
  public void shouldSpreadLeaveEvent() {
    // when
    gossip3.leave().join();

    // then
    assertThat(gossip1.receivedMembershipEvent(MembershipEventType.LEAVE, gossip3)).isTrue();
    assertThat(gossip2.receivedMembershipEvent(MembershipEventType.LEAVE, gossip3)).isTrue();
  }

  @Test
  public void shouldRemoveMemberOnLeave() {
    // when
    gossip3.leave().join();

    // then
    assertThat(gossip1.hasMember(gossip3)).isFalse();
    assertThat(gossip2.hasMember(gossip3)).isFalse();
  }

  @Test
  public void shouldCompleteFutureWhenTimeoutIsReached() {
    // given
    cluster.interruptConnectionBetween(gossip3, gossip1);

    // when
    final ActorFuture<Void> future = gossip3.leave();

    // then
    cluster.waitUntil(() -> future.isDone());

    future.join();
  }

  @Test
  public void shouldLeaveWhenNotJoined() {
    // given
    gossip3.leave().join();

    // when
    gossip3.leave().join();
  }

  @Test
  public void shouldLeaveWhenHaveNoMembers() {
    // given
    gossip2.leave().join();
    gossip3.leave().join();

    assertThat(gossip1.hasMember(gossip2)).isFalse();
    assertThat(gossip1.hasMember(gossip3)).isFalse();

    // when
    gossip1.leave().join();
  }
}
