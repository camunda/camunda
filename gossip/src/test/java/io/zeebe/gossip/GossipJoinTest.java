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

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import io.zeebe.test.util.ClockRule;
import io.zeebe.test.util.agent.ManualActorScheduler;
import org.junit.Rule;
import org.junit.Test;

public class GossipJoinTest
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

    @Test
    public void shouldSendSyncRequestOnJoin()
    {
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        assertThat(gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip2)).isTrue();
        assertThat(gossip2.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1)).isTrue();
    }

    @Test
    public void shouldSpreadJoinEvent()
    {
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.JOIN, gossip2)).isTrue();
    }

    @Test
    public void shouldAddMemberOnJoin()
    {
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        assertThat(gossip1.hasMember(gossip2)).isTrue();
        assertThat(gossip2.hasMember(gossip1)).isTrue();
    }

    @Test
    public void shouldSyncMembersOnJoin()
    {
        // given
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // when
        gossip3.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip3.hasMember(gossip1)).isTrue();
        assertThat(gossip3.hasMember(gossip2)).isTrue();
    }

    @Test
    public void shouldRetryJoinIfContactPointIsNotAvailable()
    {
        // given
        cluster.interruptConnectionBetween(gossip1, gossip2);

        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getJoinTimeout()));

        actorScheduler.waitUntilDone();

        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.JOIN, gossip2)).isFalse();

        // when
        cluster.reconnect(gossip1, gossip2);

        clock.addTime(Duration.ofMillis(CONFIGURATION.getJoinInterval()));

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.JOIN, gossip2)).isTrue();
    }

    @Test
    public void shouldJoinIfOneContactPointIsAvailable()
    {
        // given
        cluster.interruptConnectionBetween(gossip1, gossip3);

        // when
        gossip3.join(gossip1, gossip2);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip2.receivedMembershipEvent(MembershipEventType.JOIN, gossip3)).isTrue();
        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.JOIN, gossip3)).isFalse();
    }

    @Test
    public void shouldJoinWithMultipleContactPoints()
    {
        // given
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // when
        gossip3.join(gossip1, gossip2);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.JOIN, gossip3)).isTrue();
        assertThat(gossip2.receivedMembershipEvent(MembershipEventType.JOIN, gossip3)).isTrue();
    }

    @Test
    public void shouldCompleteFutureWhenJoined()
    {
        // when
        final CompletableFuture<Void> future = gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip2.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1)).isTrue();
        assertThat(future).isDone().hasNotFailed();
    }

    @Test
    public void shouldCompleteFutureWithFailureWhenAlreadyJoined()
    {
        // given
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // when
        final CompletableFuture<Void> future = gossip2.join(gossip1);

        actorScheduler.waitUntilDone();

        // then
        assertThat(future).isDone().withFailMessage("Already joined.");
    }

    @Test
    public void shouldJoinAfterLeave()
    {
        // given
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        final CompletableFuture<Void> leaveFuture = gossip2.getController().leave();
        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        assertThat(leaveFuture).isDone().hasNotFailed();

        // when
        final CompletableFuture<Void> joinFuture = gossip2.join(gossip1);
        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(joinFuture).isDone().hasNotFailed();
        assertThat(gossip1.hasMember(gossip2)).isTrue();
    }

    @Test
    public void shouldIncreaseGossipTermOnJoin()
    {
        // given
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        final CompletableFuture<Void> leaveFuture = gossip2.getController().leave();
        actorScheduler.waitUntilDone();

        assertThat(leaveFuture).isDone().hasNotFailed();

        // when
        final CompletableFuture<Void> joinFuture = gossip2.join(gossip1);
        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // then
        assertThat(joinFuture).isDone().hasNotFailed();

        final MembershipEvent leaveEvent = gossip1.getReceivedMembershipEvents(MembershipEventType.LEAVE, gossip2)
                .findFirst()
                .get();

        final MembershipEvent secondJoinEvent = gossip1.getReceivedMembershipEvents(MembershipEventType.JOIN, gossip2)
                .distinct()
                .skip(1)
                .findFirst()
                .get();

        assertThat(secondJoinEvent.getGossipTerm().isGreaterThan(leaveEvent.getGossipTerm())).isTrue();
    }

}
