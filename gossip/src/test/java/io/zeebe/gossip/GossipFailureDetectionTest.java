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
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class GossipFailureDetectionTest
{
    private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();

    private ControlledActorClock clock = new ControlledActorClock();
    private ActorSchedulerRule actorScheduler = new ActorSchedulerRule(clock);

    private GossipRule gossip1 = new GossipRule(() -> actorScheduler.get(), CONFIGURATION, "localhost", 8001);
    private GossipRule gossip2 = new GossipRule(() -> actorScheduler.get(), CONFIGURATION, "localhost", 8002);
    private GossipRule gossip3 = new GossipRule(() -> actorScheduler.get(), CONFIGURATION, "localhost", 8003);

    @Rule
    public GossipClusterRule cluster = new GossipClusterRule(actorScheduler, gossip1, gossip2, gossip3);

    @Rule
    public Timeout timeout = Timeout.seconds(10);

    @Before
    public void init()
    {
        gossip2.join(gossip1).join();
        gossip3.join(gossip1).join();

        clock.addTime(CONFIGURATION.getProbeInterval());
        clock.addTime(CONFIGURATION.getProbeInterval());

        TestUtil.waitUntil(() -> gossip2.hasMember(gossip3) && gossip3.hasMember(gossip2));

        gossip1.clearReceivedEvents();
        gossip2.clearReceivedEvents();
        gossip3.clearReceivedEvents();
    }

    @Test
    public void shouldSendPingAndAck()
    {
        // when
        clock.addTime(CONFIGURATION.getProbeInterval());

        TestUtil.waitUntil(() -> gossip1.receivedEvent(GossipEventType.PING, gossip2)
                           || gossip1.receivedEvent(GossipEventType.PING, gossip3));

        clock.addTime(CONFIGURATION.getProbeInterval());

        // then
        TestUtil.waitUntil(() -> gossip1.receivedEvent(GossipEventType.PING, gossip2));
        TestUtil.waitUntil(() -> gossip1.receivedEvent(GossipEventType.PING, gossip3));

        TestUtil.waitUntil(() -> gossip2.receivedEvent(GossipEventType.PING, gossip1));
        TestUtil.waitUntil(() -> gossip2.receivedEvent(GossipEventType.PING, gossip3));

        TestUtil.waitUntil(() -> gossip3.receivedEvent(GossipEventType.PING, gossip1));
        TestUtil.waitUntil(() -> gossip3.receivedEvent(GossipEventType.PING, gossip2));

        assertThat(gossip1.receivedEvent(GossipEventType.ACK, gossip2)).isTrue();
        assertThat(gossip1.receivedEvent(GossipEventType.ACK, gossip3)).isTrue();

        assertThat(gossip2.receivedEvent(GossipEventType.ACK, gossip1)).isTrue();
        assertThat(gossip2.receivedEvent(GossipEventType.ACK, gossip3)).isTrue();

        assertThat(gossip3.receivedEvent(GossipEventType.ACK, gossip1)).isTrue();
        assertThat(gossip3.receivedEvent(GossipEventType.ACK, gossip2)).isTrue();
    }

    @Test
    public void shouldSendPingReqAndForwardAck()
    {
        // given
        cluster.interruptConnectionBetween(gossip1, gossip2);

        // when
        TestUtil.doRepeatedly(() ->
        {
            clock.addTime(CONFIGURATION.getProbeInterval());
            return null;
        }).until(v -> gossip3.receivedEvent(GossipEventType.PING_REQ, gossip1));

        // then
        assertThat(gossip2.receivedEvent(GossipEventType.PING, gossip3)).isTrue();

        assertThat(gossip3.receivedEvent(GossipEventType.ACK, gossip2)).isTrue();
        assertThat(gossip1.receivedEvent(GossipEventType.ACK, gossip3)).isTrue();

        assertThat(gossip2.receivedEvent(GossipEventType.PING, gossip1)).isFalse();
        assertThat(gossip1.receivedEvent(GossipEventType.ACK, gossip2)).isFalse();
    }

    @Test
    public void shouldSpreadSuspectEvent()
    {
        // given
        cluster.interruptConnectionBetween(gossip3, gossip1);
        cluster.interruptConnectionBetween(gossip3, gossip2);

        // when
        TestUtil.doRepeatedly(() ->
        {
            clock.addTime(CONFIGURATION.getProbeInterval());
            return null;
        }).until(v -> gossip1.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3)
                && gossip2.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3));

        // then
        assertThat(gossip1.hasMember(gossip3)).isTrue();
        assertThat(gossip2.hasMember(gossip3)).isTrue();
    }
//
//    @Test
//    public void shouldSpreadConfirmEvent()
//    {
//        // given
//        cluster.interruptConnectionBetween(gossip3, gossip1);
//        cluster.interruptConnectionBetween(gossip3, gossip2);
//
//        // when
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeTimeout()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeIndirectTimeout()));
//        actorScheduler.waitUntilDone();
//
//        final long suspicionTimeout = GossipMath.suspicionTimeout(CONFIGURATION.getSuspicionMultiplier(), 3, CONFIGURATION.getProbeInterval());
//        clock.addTime(Duration.ofMillis(suspicionTimeout));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeTimeout()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeIndirectTimeout()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.CONFIRM, gossip3)).isTrue();
//        assertThat(gossip2.receivedMembershipEvent(MembershipEventType.CONFIRM, gossip3)).isTrue();
//
//        assertThat(gossip1.hasMember(gossip3)).isFalse();
//        assertThat(gossip2.hasMember(gossip3)).isFalse();
//    }
//
//    @Test
//    public void shouldCounterSuspectEventIfAlive()
//    {
//        // given
//        cluster.interruptConnectionBetween(gossip3, gossip1);
//        cluster.interruptConnectionBetween(gossip3, gossip2);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeTimeout()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeIndirectTimeout()));
//        actorScheduler.waitUntilDone();
//
//        // when
//        cluster.reconnect(gossip3, gossip1);
//        cluster.reconnect(gossip3, gossip2);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(gossip3.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3)).isTrue();
//
//        final MembershipEvent suspectEvent = gossip1.getReceivedMembershipEvents(MembershipEventType.SUSPECT, gossip3)
//            .findFirst()
//            .get();
//
//        final MembershipEvent counterAliveEvent = gossip1.getReceivedMembershipEvents(MembershipEventType.ALIVE, gossip3)
//            .findFirst()
//            .get();
//
//        assertThat(counterAliveEvent.getGossipTerm().isEqual(suspectEvent.getGossipTerm())).isTrue();
//        final Optional<MembershipEvent> lastAliveEvent = gossip1.getReceivedMembershipEvents(MembershipEventType.ALIVE, gossip3)
//                                                               .filter(e -> e.getGossipTerm()
//                                                                     .isGreaterThan(suspectEvent.getGossipTerm()))
//                                                               .findFirst();
//
//        assertThat(lastAliveEvent).isPresent();
//    }

}
