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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import io.zeebe.test.util.ClockRule;
import io.zeebe.test.util.agent.ManualActorScheduler;
import org.junit.*;

public class GossipLeaveTest
{
    private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();

    private ManualActorScheduler actorScheduler = new ManualActorScheduler();

    private GossipRule gossip1 = new GossipRule(() -> actorScheduler, CONFIGURATION, "localhost", 8001);
    private GossipRule gossip2 = new GossipRule(() -> actorScheduler, CONFIGURATION, "localhost", 8002);
    private GossipRule gossip3 = new GossipRule(() -> actorScheduler, CONFIGURATION, "localhost", 8003);

    @Rule
    public GossipClusterRule cluster = new GossipClusterRule(actorScheduler, gossip1, gossip2, gossip3);

    @Rule
    public ClockRule clock = ClockRule.pinCurrentTime();

    @Before
    public void init()
    {
        gossip2.join(gossip1);
        gossip3.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        gossip1.clearReceivedEvents();
        gossip2.clearReceivedEvents();
        gossip3.clearReceivedEvents();

        assertThat(gossip2.hasMember(gossip3)).isTrue();
        assertThat(gossip3.hasMember(gossip2)).isTrue();
    }

    @Test
    public void shouldSpreadLeaveEvent()
    {
        // when
        gossip3.getController().leave();

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.LEAVE, gossip3)).isTrue();
        assertThat(gossip2.receivedMembershipEvent(MembershipEventType.LEAVE, gossip3)).isTrue();
    }

    @Test
    public void shouldRemoveMemberOnLeave()
    {
        // when
        gossip3.getController().leave();

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip1.hasMember(gossip3)).isFalse();
        assertThat(gossip2.hasMember(gossip3)).isFalse();
    }

    @Test
    public void shouldCompleteFutureWhenEventIsSpread()
    {
        // when
        final CompletableFuture<Void> future = gossip3.getController().leave();

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.LEAVE, gossip3)).isTrue();
        assertThat(gossip2.receivedMembershipEvent(MembershipEventType.LEAVE, gossip3)).isTrue();

        assertThat(future).isDone().hasNotFailed();
    }

    @Test
    public void shouldCompleteFutureWhenTimeoutIsReached()
    {
        // given
        cluster.interruptConnectionBetween(gossip3, gossip1);

        // when
        final CompletableFuture<Void> future = gossip3.getController().leave();
        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        assertThat(future).isNotDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getLeaveTimeout()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(future).isDone().hasNotFailed();
    }

    @Test
    public void shouldLeaveWhenNotJoined()
    {
        // given
        gossip3.getController().leave();

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // when
        final CompletableFuture<Void> future = gossip3.getController().leave();

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(future).isDone().hasNotFailed();
    }

    @Test
    public void shouldLeaveWhenHaveNoMembers()
    {
        // given
        gossip2.getController().leave();
        gossip3.getController().leave();

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        assertThat(gossip1.hasMember(gossip2)).isFalse();
        assertThat(gossip1.hasMember(gossip3)).isFalse();

        // when
        final CompletableFuture<Void> future = gossip1.getController().leave();

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(future).isDone().hasNotFailed();
    }
}
