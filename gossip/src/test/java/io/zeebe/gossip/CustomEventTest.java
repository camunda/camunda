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

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.zeebe.gossip.protocol.CustomEvent;
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import io.zeebe.gossip.util.GossipRule.ReceivedCustomEvent;
import io.zeebe.gossip.util.GossipRule.RecordingCustomEventListener;
import io.zeebe.test.util.BufferAssert;
import io.zeebe.test.util.ClockRule;
import io.zeebe.test.util.agent.ManualActorScheduler;
import org.agrona.DirectBuffer;
import org.junit.*;

public class CustomEventTest
{
//    private static final DirectBuffer TYPE_1 = wrapString("CUST_1");
//    private static final DirectBuffer TYPE_2 = wrapString("CUST_2");
//
//    private static final DirectBuffer PAYLOAD_1 = wrapString("FOO");
//    private static final DirectBuffer PAYLOAD_2 = wrapString("BAR");
//
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
//    public void shouldSpreadCustomEvent()
//    {
//        // when
//        gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(gossip2.receivedCustomEvent(TYPE_1, gossip1)).isTrue();
//        assertThat(gossip3.receivedCustomEvent(TYPE_1, gossip1)).isTrue();
//
//        final CustomEvent customEvent = gossip2.getReceivedCustomEvents(TYPE_1, gossip1).findFirst().get();
//        BufferAssert.assertThatBuffer(customEvent.getPayload())
//            .hasCapacity(PAYLOAD_1.capacity())
//            .hasBytes(PAYLOAD_1);
//    }
//
//    @Test
//    public void shouldInvokeCustomEventListener()
//    {
//        // given
//        final RecordingCustomEventListener customEventListener = new RecordingCustomEventListener();
//        gossip2.getController().addCustomEventListener(TYPE_1, customEventListener);
//
//        // when
//        gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(customEventListener.getInvocations().count()).isEqualTo(1);
//
//        final ReceivedCustomEvent customEvent = customEventListener.getInvocations().findFirst().get();
//
//        assertThat(customEvent.getSender()).isEqualTo(gossip1.getAddress());
//
//        BufferAssert.assertThatBuffer(customEvent.getPayload())
//            .hasCapacity(PAYLOAD_1.capacity())
//            .hasBytes(PAYLOAD_1);
//    }
//
//    @Test
//    public void shouldInvokeCustomEventListenerForMoreEvents()
//    {
//        // given
//        final RecordingCustomEventListener customEventListener = new RecordingCustomEventListener();
//        gossip2.getController().addCustomEventListener(TYPE_1, customEventListener);
//
//        // when
//        final int customEventCount = CONFIGURATION.getMaxCustomEventsPerMessage() + 1;
//        for (int i = 0; i < customEventCount; i++)
//        {
//            final DirectBuffer payload = wrapString("PAYLOAD_" + i);
//            gossip1.getPushlisher().publishEvent(TYPE_1, payload);
//        }
//
//        final int iterationsToSpread = GossipMath.gossipPeriodsToSpread(CONFIGURATION.getRetransmissionMultiplier(), 3) + 1;
//        for (int i = 0; i < iterationsToSpread; i++)
//        {
//            clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//            actorScheduler.waitUntilDone();
//        }
//
//
//        // then
//        final List<ReceivedCustomEvent> customEvents = customEventListener.getInvocations().collect(toList());
//
//        assertThat(customEvents.size()).isEqualTo(customEventCount);
//
//        for (int i = 0; i < customEventCount; i++)
//        {
//            final ReceivedCustomEvent customEvent = customEvents.get(i);
//            assertThat(customEvent.getSender()).isEqualTo(gossip1.getAddress());
//
//            final DirectBuffer payload = wrapString("PAYLOAD_" + i);
//
//            BufferAssert.assertThatBuffer(customEvent.getPayload())
//                        .hasCapacity(payload.capacity())
//                        .hasBytes(payload);
//        }
//    }
//
//    @Test
//    public void shouldInvokeCustomEventListenerOnlyOncePerEvent()
//    {
//        // given
//        final RecordingCustomEventListener customEventListener1 = new RecordingCustomEventListener();
//        gossip2.getController().addCustomEventListener(TYPE_1, customEventListener1);
//
//        final RecordingCustomEventListener customEventListener2 = new RecordingCustomEventListener();
//        gossip2.getController().addCustomEventListener(TYPE_2, customEventListener2);
//
//        // when
//        gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
//        gossip1.getPushlisher().publishEvent(TYPE_2, PAYLOAD_2);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(customEventListener1.getInvocations().count()).isEqualTo(1);
//        final ReceivedCustomEvent customEvent1 = customEventListener1.getInvocations().findFirst().get();
//        BufferAssert.assertThatBuffer(customEvent1.getPayload()).hasBytes(PAYLOAD_1);
//
//        assertThat(customEventListener2.getInvocations().count()).isEqualTo(1);
//        final ReceivedCustomEvent customEvent2 = customEventListener2.getInvocations().findFirst().get();
//        BufferAssert.assertThatBuffer(customEvent2.getPayload()).hasBytes(PAYLOAD_2);
//    }
//
//    @Test
//    public void shouldNotInvokeCustomEventListenerForOwnEvent()
//    {
//        // given
//        final AtomicBoolean invoked = new AtomicBoolean(false);
//        gossip1.getController().addCustomEventListener(TYPE_1, (s, p) -> invoked.set(true));
//
//        // when
//        gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(invoked.get()).isFalse();
//    }
//
//    @Test
//    public void shouldIncreaseGossipTermPerEventType()
//    {
//        // when
//        gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
//        gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
//        gossip1.getPushlisher().publishEvent(TYPE_2, PAYLOAD_1);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(gossip2.getReceivedCustomEvents(TYPE_1, gossip1).distinct())
//            .hasSize(2)
//            .extracting(e -> e.getSenderGossipTerm().getHeartbeat())
//            .containsExactly(0L, 1L);
//
//        final CustomEvent customEventType2 = gossip2.getReceivedCustomEvents(TYPE_2, gossip1).findFirst().get();
//        assertThat(customEventType2.getSenderGossipTerm().getHeartbeat()).isEqualTo(0L);
//    }
//
//    @Test
//    public void shouldInvokeAllCustomEventListeners()
//    {
//        // given
//        final AtomicInteger counter = new AtomicInteger(0);
//
//        gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
//        gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
//        gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
//
//        // when
//        gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(counter.get()).isEqualTo(3);
//    }
//
//    @Test
//    public void shouldRemoveCustomEventListener()
//    {
//        // given
//        final AtomicInteger counter = new AtomicInteger(0);
//
//        final GossipCustomEventListener listener = (s, p) -> counter.incrementAndGet();
//
//        gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
//        gossip2.getController().addCustomEventListener(TYPE_1, listener);
//        gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
//
//        // when
//        gossip2.getController().removeCustomEventListener(listener);
//
//        gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(counter.get()).isEqualTo(2);
//    }
//
//    @Test
//    public void shouldInvokeCustomEventListenersFailsafe()
//    {
//        // given
//        final AtomicInteger counter = new AtomicInteger(0);
//
//        gossip2.getController().addCustomEventListener(TYPE_1, (s, p) ->
//        {
//            throw new RuntimeException("expected");
//        });
//
//        gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
//        gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
//
//        // when
//        gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
//        actorScheduler.waitUntilDone();
//
//        // then
//        assertThat(counter.get()).isEqualTo(2);
//    }

}
