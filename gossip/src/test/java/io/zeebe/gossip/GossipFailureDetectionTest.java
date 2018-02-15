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
import java.util.Optional;

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.protocol.MembershipEvent;
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import io.zeebe.test.util.ClockRule;
import io.zeebe.test.util.agent.ManualActorScheduler;
import org.junit.*;

public class GossipFailureDetectionTest
{
//    private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();
//
//    private ManualActorScheduler actorScheduler = new ManualActorScheduler();
//
//    private GossipRule gossip1 = new GossipRule(() -> actorScheduler, CONFIGURATION, "localhost", 8001);
//    private GossipRule gossip2 = new GossipRule(() -> actorScheduler, CONFIGURATION, "localhost", 8002);
//    private GossipRule gossip3 = new GossipRule(() -> actorScheduler, CONFIGURATION, "localhost", 8003);
//
//    @Rule
//    public GossipClusterRule cluster = new GossipClusterRule(actorScheduler, gossip1, gossip2, gossip3);
//
//    @Rule
//    public ClockRule clock = ClockRule.pinCurrentTime();
//
//    @Before
//    public void init()
//    {
//        gossip2.join(gossip1);
//        gossip3.join(gossip1);
//
//        actorScheduler.waitUntilDone();
//        actorScheduler.waitUntilDone();
//
//        gossip1.clearReceivedEvents();
//        gossip2.clearReceivedEvents();
//        gossip3.clearReceivedEvents();
//
//        assertThat(gossip2.hasMember(gossip3)).isTrue();
//        assertThat(gossip3.hasMember(gossip2)).isTrue();
//    }
//
//    @Test
//    public void shouldSendPingAndAck()
//    {
//        // when
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(gossip1.receivedEvent(GossipEventType.PING, gossip2)).isTrue();
//        assertThat(gossip1.receivedEvent(GossipEventType.PING, gossip3)).isTrue();
//
//        assertThat(gossip1.receivedEvent(GossipEventType.ACK, gossip2)).isTrue();
//        assertThat(gossip1.receivedEvent(GossipEventType.ACK, gossip3)).isTrue();
//
//        assertThat(gossip2.receivedEvent(GossipEventType.PING, gossip1)).isTrue();
//        assertThat(gossip2.receivedEvent(GossipEventType.PING, gossip3)).isTrue();
//
//        assertThat(gossip2.receivedEvent(GossipEventType.ACK, gossip1)).isTrue();
//        assertThat(gossip2.receivedEvent(GossipEventType.ACK, gossip3)).isTrue();
//
//        assertThat(gossip3.receivedEvent(GossipEventType.PING, gossip1)).isTrue();
//        assertThat(gossip3.receivedEvent(GossipEventType.PING, gossip2)).isTrue();
//
//        assertThat(gossip3.receivedEvent(GossipEventType.ACK, gossip1)).isTrue();
//        assertThat(gossip3.receivedEvent(GossipEventType.ACK, gossip2)).isTrue();
//    }
//
//    @Test
//    public void shouldSendPingReqAndForwardAck()
//    {
//        // given
//        cluster.interruptConnectionBetween(gossip1, gossip2);
//
//        // when
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeTimeout()));
//        actorScheduler.waitUntilDone();
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(gossip3.receivedEvent(GossipEventType.PING_REQ, gossip1)).isTrue();
//        assertThat(gossip2.receivedEvent(GossipEventType.PING, gossip3)).isTrue();
//
//        assertThat(gossip3.receivedEvent(GossipEventType.ACK, gossip2)).isTrue();
//        assertThat(gossip1.receivedEvent(GossipEventType.ACK, gossip3)).isTrue();
//
//        assertThat(gossip2.receivedEvent(GossipEventType.PING, gossip1)).isFalse();
//        assertThat(gossip1.receivedEvent(GossipEventType.ACK, gossip2)).isFalse();
//    }
//
//    @Test
//    public void shouldSpreadSuspectEvent()
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
//        // then
//        assertThat(gossip1.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3)).isTrue();
//        assertThat(gossip2.receivedMembershipEvent(MembershipEventType.SUSPECT, gossip3)).isTrue();
//
//        assertThat(gossip1.hasMember(gossip3)).isTrue();
//        assertThat(gossip2.hasMember(gossip3)).isTrue();
//    }
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
