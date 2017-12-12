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
import io.zeebe.test.util.agent.ControllableTaskScheduler;
import org.junit.Rule;
import org.junit.Test;

public class GossipLeaveTest
{
    private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();

    private ControllableTaskScheduler actorScheduler = new ControllableTaskScheduler();

    private GossipRule gossip1 = new GossipRule(() -> actorScheduler, CONFIGURATION, "localhost", 8001);
    private GossipRule gossip2 = new GossipRule(() -> actorScheduler, CONFIGURATION, "localhost", 8002);
    private GossipRule gossip3 = new GossipRule(() -> actorScheduler, CONFIGURATION, "localhost", 8003);

    @Rule
    public GossipClusterRule clusterRule = new GossipClusterRule(actorScheduler, gossip1, gossip2, gossip3);

    @Rule
    public ClockRule clock = ClockRule.pinCurrentTime();

    @Test
    public void shouldSpreadLeaveEvent()
    {
        // given
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // when
        gossip2.getController().leave();

        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.LEAVE, gossip2)).isTrue();
    }

    @Test
    public void shouldRemoveMemberOnLeave()
    {
        // given
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // when
        gossip2.getController().leave();

        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip1.hasMember(gossip2)).isFalse();
    }

    @Test
    public void shouldCompleteFutureWhenEventIsSpread()
    {
        // given
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        // when
        final CompletableFuture<Void> future = gossip2.getController().leave();

        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.LEAVE, gossip2)).isTrue();
        assertThat(future).isDone().hasNotFailed();
    }

    @Test
    public void shouldCompleteFutureWhenTimeoutIsReached()
    {
        // given
        gossip2.join(gossip1);

        actorScheduler.waitUntilDone();
        actorScheduler.waitUntilDone();

        gossip2.interruptConnectionTo(gossip1);

        // when
        final CompletableFuture<Void> future = gossip2.getController().leave();
        actorScheduler.waitUntilDone();

        assertThat(future).isNotDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getLeaveTimeout()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(future).isDone().hasNotFailed();
    }

    @Test
    public void shouldCompleteFutureWithFailureWhenNotJoined()
    {
        // when
        final CompletableFuture<Void> future = gossip2.getController().leave();

        actorScheduler.waitUntilDone();

        // then
        assertThat(future).isDone().withFailMessage("Not joined");
    }
}
