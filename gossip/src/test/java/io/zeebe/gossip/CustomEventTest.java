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
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CustomEventTest
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
    public void shouldSpreadCustomEvent()
    {
        // given
        final DirectBuffer type = wrapString("CUST");
        final DirectBuffer payload = wrapString("PAYLOAD");

        // when
        gossip1.getPushlisher().publishEvent(type, payload);

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip2.receivedCustomEvent(type, gossip1)).isTrue();
        assertThat(gossip3.receivedCustomEvent(type, gossip1)).isTrue();

        final CustomEvent customEvent = gossip2.getReceivedCustomEvents(type, gossip1).findFirst().get();
        BufferAssert.assertThatBuffer(customEvent.getPayload())
            .hasCapacity(payload.capacity())
            .hasBytes(payload);
    }

    @Test
    public void shouldInvokeCustomEventListener()
    {
        // given
        final DirectBuffer type = wrapString("CUST");
        final DirectBuffer payload = wrapString("PAYLOAD");

        final RecordingCustomEventListener customEventListener = new RecordingCustomEventListener();
        gossip2.getController().addCustomEventListener(type, customEventListener);

        // when
        gossip1.getPushlisher().publishEvent(type, payload);

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(customEventListener.getInvocations().count()).isEqualTo(1);

        final ReceivedCustomEvent customEvent = customEventListener.getInvocations().findFirst().get();

        assertThat(customEvent.getSender()).isEqualTo(gossip1.getAddress());

        BufferAssert.assertThatBuffer(customEvent.getPayload())
            .hasCapacity(payload.capacity())
            .hasBytes(payload);
    }

    @Test
    public void shouldInvokeCustomEventListenerForMoreEvents()
    {
        // given
        final DirectBuffer type = wrapString("CUST");

        final RecordingCustomEventListener customEventListener = new RecordingCustomEventListener();
        gossip2.getController().addCustomEventListener(type, customEventListener);

        // when
        final int customEventCount = CONFIGURATION.getMaxCustomEventsPerMessage() + 1;
        for (int i = 0; i < customEventCount; i++)
        {
            final DirectBuffer payload = wrapString("PAYLOAD_" + i);
            gossip1.getPushlisher().publishEvent(type, payload);
        }

        final int iterationsToSpread = GossipMath.gossipPeriodsToSpread(CONFIGURATION.getRetransmissionMultiplier(), 3) + 1;
        for (int i = 0; i < iterationsToSpread; i++)
        {
            clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
            actorScheduler.waitUntilDone();
        }


        // then
        assertThat(customEventListener.getInvocations().count()).isEqualTo(customEventCount);

        for (int i = 0; i < customEventCount; i++)
        {
            final ReceivedCustomEvent customEvent = customEventListener.getInvocations().skip(i).findFirst().get();
            assertThat(customEvent.getSender()).isEqualTo(gossip1.getAddress());

            final DirectBuffer payload = wrapString("PAYLOAD_" + i);

            BufferAssert.assertThatBuffer(customEvent.getPayload())
                        .hasCapacity(payload.capacity())
                        .hasBytes(payload);
        }
    }

    @Test
    public void shouldInvokeCustomEventListenerOnlyOncePerEvent()
    {
        // given
        final DirectBuffer type1 = wrapString("CUST_1");
        final DirectBuffer type2 = wrapString("CUST_2");

        final DirectBuffer payload1 = wrapString("PAYLOAD_1");
        final DirectBuffer payload2 = wrapString("PAYLOAD_2");

        final RecordingCustomEventListener customEventListener1 = new RecordingCustomEventListener();
        gossip2.getController().addCustomEventListener(type1, customEventListener1);

        final RecordingCustomEventListener customEventListener2 = new RecordingCustomEventListener();
        gossip2.getController().addCustomEventListener(type2, customEventListener2);

        // when
        gossip1.getPushlisher().publishEvent(type1, payload1);
        gossip1.getPushlisher().publishEvent(type2, payload2);

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(customEventListener1.getInvocations().count()).isEqualTo(1);
        final ReceivedCustomEvent customEvent1 = customEventListener1.getInvocations().findFirst().get();
        BufferAssert.assertThatBuffer(customEvent1.getPayload()).hasBytes(payload1);

        assertThat(customEventListener2.getInvocations().count()).isEqualTo(1);
        final ReceivedCustomEvent customEvent2 = customEventListener2.getInvocations().findFirst().get();
        BufferAssert.assertThatBuffer(customEvent2.getPayload()).hasBytes(payload2);
    }

    @Test
    public void shouldNotInvokeCustomEventListenerForOwnEvent()
    {
        // given
        final DirectBuffer type = wrapString("CUST");
        final DirectBuffer payload = wrapString("PAYLOAD");

        final AtomicBoolean invoked = new AtomicBoolean(false);
        gossip1.getController().addCustomEventListener(type, (s, p) -> invoked.set(true));

        // when
        gossip1.getPushlisher().publishEvent(type, payload);

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(invoked.get()).isFalse();
    }

    @Test
    public void shouldIncreaseGossipTermPerEventType()
    {
        // given
        final DirectBuffer type1 = wrapString("CUST_1");
        final DirectBuffer type2 = wrapString("CUST_2");
        final DirectBuffer payload = wrapString("PAYLOAD");

        // when
        gossip1.getPushlisher().publishEvent(type1, payload);
        gossip1.getPushlisher().publishEvent(type1, payload);
        gossip1.getPushlisher().publishEvent(type2, payload);

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(gossip2.getReceivedCustomEvents(type1, gossip1).distinct())
            .hasSize(2)
            .extracting(e -> e.getSenderGossipTerm().getHeartbeat())
            .containsExactly(0L, 1L);

        final CustomEvent customEventType2 = gossip2.getReceivedCustomEvents(type2, gossip1).findFirst().get();
        assertThat(customEventType2.getSenderGossipTerm().getHeartbeat()).isEqualTo(0L);
    }

    @Test
    public void shouldInvokeAllCustomEventListeners()
    {
        // given
        final DirectBuffer type = wrapString("CUST");
        final DirectBuffer payload = wrapString("PAYLOAD");

        final AtomicInteger counter = new AtomicInteger(0);

        gossip2.getController().addCustomEventListener(type, (s, p) -> counter.incrementAndGet());
        gossip2.getController().addCustomEventListener(type, (s, p) -> counter.incrementAndGet());
        gossip2.getController().addCustomEventListener(type, (s, p) -> counter.incrementAndGet());

        // when
        gossip1.getPushlisher().publishEvent(type, payload);

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    public void shouldRemoveCustomEventListener()
    {
        // given
        final DirectBuffer type = wrapString("CUST");
        final DirectBuffer payload = wrapString("PAYLOAD");

        final AtomicInteger counter = new AtomicInteger(0);

        final GossipCustomEventListener listener = (s, p) -> counter.incrementAndGet();

        gossip2.getController().addCustomEventListener(type, (s, p) -> counter.incrementAndGet());
        gossip2.getController().addCustomEventListener(type, listener);
        gossip2.getController().addCustomEventListener(type, (s, p) -> counter.incrementAndGet());

        // when
        gossip2.getController().removeCustomEventListener(listener);

        gossip1.getPushlisher().publishEvent(type, payload);

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    public void shouldInvokeCustomEventListenersFailsafe()
    {
        // given
        final DirectBuffer type = wrapString("CUST");
        final DirectBuffer payload = wrapString("PAYLOAD");

        final AtomicInteger counter = new AtomicInteger(0);

        gossip2.getController().addCustomEventListener(type, (s, p) ->
        {
            throw new RuntimeException("expected");
        });

        gossip2.getController().addCustomEventListener(type, (s, p) -> counter.incrementAndGet());
        gossip2.getController().addCustomEventListener(type, (s, p) -> counter.incrementAndGet());

        // when
        gossip1.getPushlisher().publishEvent(type, payload);

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        clock.addTime(Duration.ofMillis(CONFIGURATION.getProbeInterval()));
        actorScheduler.waitUntilDone();

        // then
        assertThat(counter.get()).isEqualTo(2);
    }

}
