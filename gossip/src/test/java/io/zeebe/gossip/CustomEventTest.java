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

import io.zeebe.gossip.protocol.CustomEvent;
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import io.zeebe.gossip.util.GossipRule.ReceivedCustomEvent;
import io.zeebe.gossip.util.GossipRule.RecordingCustomEventListener;
import io.zeebe.test.util.BufferAssert;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class CustomEventTest {
  private static final DirectBuffer TYPE_1 = wrapString("CUST_1");
  private static final DirectBuffer TYPE_2 = wrapString("CUST_2");

  private static final DirectBuffer PAYLOAD_1 = wrapString("FOO");
  private static final DirectBuffer PAYLOAD_2 = wrapString("BAR");

  private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();

  private GossipRule gossip1 = new GossipRule(1);
  private GossipRule gossip2 = new GossipRule(2);
  private GossipRule gossip3 = new GossipRule(3);

  @Rule
  public GossipClusterRule cluster =
      new GossipClusterRule(CONFIGURATION, gossip1, gossip2, gossip3);

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
  public void shouldSpreadCustomEvent() {
    // when
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);

    cluster.waitUntil(() -> gossip2.receivedCustomEvent(TYPE_1, gossip1));

    // then
    final CustomEvent customEvent =
        gossip2.getReceivedCustomEvents(TYPE_1, gossip1).findFirst().get();
    BufferAssert.assertThatBuffer(customEvent.getPayload())
        .hasCapacity(PAYLOAD_1.capacity())
        .hasBytes(PAYLOAD_1);
  }

  @Test
  public void shouldSpreadCustomEventToAllNodes() {
    // when
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);

    cluster.waitUntil(() -> gossip2.receivedCustomEvent(TYPE_1, gossip1));
    cluster.waitUntil(() -> gossip3.receivedCustomEvent(TYPE_1, gossip1));

    // then
    final CustomEvent customEvent =
        gossip2.getReceivedCustomEvents(TYPE_1, gossip1).findFirst().get();
    BufferAssert.assertThatBuffer(customEvent.getPayload())
        .hasCapacity(PAYLOAD_1.capacity())
        .hasBytes(PAYLOAD_1);

    final CustomEvent customEvent2 =
        gossip3.getReceivedCustomEvents(TYPE_1, gossip1).findFirst().get();
    BufferAssert.assertThatBuffer(customEvent2.getPayload())
        .hasCapacity(PAYLOAD_1.capacity())
        .hasBytes(PAYLOAD_1);
  }

  @Test
  public void shouldInvokeCustomEventListener() {
    // given
    final RecordingCustomEventListener customEventListener = new RecordingCustomEventListener();
    gossip2.getController().addCustomEventListener(TYPE_1, customEventListener);

    // when
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);

    cluster.waitUntil(() -> customEventListener.getInvocations().count() == 1);

    // then
    final ReceivedCustomEvent customEvent = customEventListener.getInvocations().findFirst().get();
    assertThat(customEvent.getSenderId()).isEqualTo(gossip1.getNodeId());
    BufferAssert.assertThatBuffer(customEvent.getPayload())
        .hasCapacity(PAYLOAD_1.capacity())
        .hasBytes(PAYLOAD_1);
  }

  @Test
  public void shouldInvokeCustomEventListenerOfAllNodes() {
    // given
    final RecordingCustomEventListener customEventListener2 = new RecordingCustomEventListener();
    gossip2.getController().addCustomEventListener(TYPE_1, customEventListener2);

    final RecordingCustomEventListener customEventListener3 = new RecordingCustomEventListener();
    gossip3.getController().addCustomEventListener(TYPE_1, customEventListener3);

    // when
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);

    cluster.waitUntil(() -> customEventListener2.getInvocations().count() == 1);
    cluster.waitUntil(() -> customEventListener3.getInvocations().count() == 1);

    // then
    assertThat(customEventListener2.getInvocations().count()).isEqualTo(1);
    assertThat(customEventListener3.getInvocations().count()).isEqualTo(1);

    final ReceivedCustomEvent customEvent2 =
        customEventListener2.getInvocations().findFirst().get();
    assertThat(customEvent2.getSenderId()).isEqualTo(gossip1.getNodeId());
    BufferAssert.assertThatBuffer(customEvent2.getPayload())
        .hasCapacity(PAYLOAD_1.capacity())
        .hasBytes(PAYLOAD_1);

    final ReceivedCustomEvent customEvent3 =
        customEventListener3.getInvocations().findFirst().get();
    assertThat(customEvent3.getSenderId()).isEqualTo(gossip1.getNodeId());
    BufferAssert.assertThatBuffer(customEvent3.getPayload())
        .hasCapacity(PAYLOAD_1.capacity())
        .hasBytes(PAYLOAD_1);
  }

  @Test
  public void shouldInvokeCustomEventListenerForMoreEvents() {
    // given
    final RecordingCustomEventListener customEventListener = new RecordingCustomEventListener();
    gossip2.getController().addCustomEventListener(TYPE_1, customEventListener);

    // when
    final int customEventCount = CONFIGURATION.getMaxCustomEventsPerMessage() + 1;
    for (int i = 0; i < customEventCount; i++) {
      final DirectBuffer payload = wrapString("PAYLOAD_" + i);
      gossip1.getPublisher().publishEvent(TYPE_1, payload);
    }

    cluster.waitUntil(() -> customEventListener.getInvocations().count() == customEventCount);

    // then
    final List<ReceivedCustomEvent> customEvents =
        customEventListener.getInvocations().collect(toList());
    for (int i = 0; i < customEventCount; i++) {
      final ReceivedCustomEvent customEvent = customEvents.get(i);
      assertThat(customEvent.getSenderId()).isEqualTo(gossip1.getNodeId());

      final DirectBuffer payload = wrapString("PAYLOAD_" + i);

      BufferAssert.assertThatBuffer(customEvent.getPayload())
          .hasCapacity(payload.capacity())
          .hasBytes(payload);
    }
  }

  @Test
  public void shouldInvokeCustomEventListenerOnlyOncePerEvent() {
    // given
    final RecordingCustomEventListener customEventListener1 = new RecordingCustomEventListener();
    gossip2.getController().addCustomEventListener(TYPE_1, customEventListener1);

    final RecordingCustomEventListener customEventListener2 = new RecordingCustomEventListener();
    gossip2.getController().addCustomEventListener(TYPE_2, customEventListener2);

    // when
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);
    gossip1.getPublisher().publishEvent(TYPE_2, PAYLOAD_2);

    cluster.waitUntil(() -> customEventListener1.getInvocations().count() == 1);
    cluster.waitUntil(() -> customEventListener2.getInvocations().count() == 1);

    // then
    final ReceivedCustomEvent customEvent1 =
        customEventListener1.getInvocations().findFirst().get();
    BufferAssert.assertThatBuffer(customEvent1.getPayload()).hasBytes(PAYLOAD_1);

    final ReceivedCustomEvent customEvent2 =
        customEventListener2.getInvocations().findFirst().get();
    BufferAssert.assertThatBuffer(customEvent2.getPayload()).hasBytes(PAYLOAD_2);
  }

  @Test
  public void shouldNotInvokeCustomEventListenerForOwnEvent() {
    // given
    final AtomicBoolean invoked = new AtomicBoolean(false);
    gossip1.getController().addCustomEventListener(TYPE_1, (s, p) -> invoked.set(true));

    // when
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);

    cluster.waitUntil(() -> gossip1.receivedCustomEvent(TYPE_1, gossip1));

    // then
    assertThat(invoked.get()).isFalse();
  }

  @Test
  public void shouldIncreaseGossipTermPerEventType() {
    // when
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);
    gossip1.getPublisher().publishEvent(TYPE_2, PAYLOAD_1);

    cluster.waitUntil(() -> gossip2.receivedCustomEvent(TYPE_2, gossip1));

    // then
    assertThat(gossip2.getReceivedCustomEvents(TYPE_1, gossip1).distinct())
        .hasSize(2)
        .extracting(e -> e.getSenderGossipTerm().getHeartbeat())
        .containsExactly(0L, 1L);

    final CustomEvent customEventType2 =
        gossip2.getReceivedCustomEvents(TYPE_2, gossip1).findFirst().get();
    assertThat(customEventType2.getSenderGossipTerm().getHeartbeat()).isEqualTo(0L);
  }

  @Test
  public void shouldInvokeAllCustomEventListeners() {
    // given
    final AtomicInteger counter = new AtomicInteger(0);

    gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
    gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
    gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());

    // when
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);

    // then
    cluster.waitUntil(() -> counter.get() == 3);
    assertThat(counter.get()).isEqualTo(3);
  }

  @Test
  public void shouldRemoveCustomEventListener() {
    // given
    final AtomicInteger counter = new AtomicInteger(0);

    final GossipCustomEventListener listener = (s, p) -> counter.incrementAndGet();

    gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
    gossip2.getController().addCustomEventListener(TYPE_1, listener);
    gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());

    // when
    gossip2.getController().removeCustomEventListener(listener);

    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);

    // then
    cluster.waitUntil(() -> counter.get() == 2);
    assertThat(counter.get()).isEqualTo(2);
  }

  @Test
  public void shouldInvokeCustomEventListenersFailsafe() {
    // given
    final AtomicInteger counter = new AtomicInteger(0);

    gossip2
        .getController()
        .addCustomEventListener(
            TYPE_1,
            (s, p) -> {
              throw new RuntimeException("expected");
            });

    gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());
    gossip2.getController().addCustomEventListener(TYPE_1, (s, p) -> counter.incrementAndGet());

    // when
    gossip1.getPublisher().publishEvent(TYPE_1, PAYLOAD_1);

    // then
    cluster.waitUntil(() -> counter.get() == 2);
  }
}
