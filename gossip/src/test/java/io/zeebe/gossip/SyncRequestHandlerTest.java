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

import io.zeebe.clustering.gossip.GossipEventType;
import io.zeebe.gossip.protocol.CustomEvent;
import io.zeebe.gossip.util.GossipClusterRule;
import io.zeebe.gossip.util.GossipRule;
import io.zeebe.test.util.BufferAssert;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.junit.Rule;
import org.junit.Test;

public class SyncRequestHandlerTest {
  private static final DirectBuffer TYPE_1 = wrapString("CUST_1");
  private static final DirectBuffer TYPE_2 = wrapString("CUST_2");

  private static final DirectBuffer PAYLOAD_1 = wrapString("FOO");
  private static final DirectBuffer PAYLOAD_2 = wrapString("BAR");

  private static final GossipConfiguration CONFIGURATION = new GossipConfiguration();

  private GossipRule gossip1 = new GossipRule("localhost:8001");
  private GossipRule gossip2 = new GossipRule("localhost:8002");
  private GossipRule gossip3 = new GossipRule("localhost:8003");

  @Rule
  public GossipClusterRule cluster =
      new GossipClusterRule(CONFIGURATION, gossip1, gossip2, gossip3);

  @Test
  public void shouldInvokeSyncRequestHandler() {
    // given
    final AtomicInteger invocationsHandler1 = new AtomicInteger(0);
    final AtomicInteger invocationsHandler2 = new AtomicInteger(0);

    gossip1
        .getController()
        .registerSyncRequestHandler(
            TYPE_1,
            request -> {
              invocationsHandler1.incrementAndGet();

              return CompletableActorFuture.completed(null);
            });

    gossip1
        .getController()
        .registerSyncRequestHandler(
            TYPE_2,
            request -> {
              invocationsHandler2.incrementAndGet();

              return CompletableActorFuture.completed(null);
            });

    // when
    gossip2.join(gossip1).join();
    gossip3.join(gossip1).join();

    // then
    assertThat(invocationsHandler1.get()).isEqualTo(2);
    assertThat(invocationsHandler2.get()).isEqualTo(2);
  }

  @Test
  public void shouldReceiveCustomEvent() {
    // given
    gossip2.join(gossip1).join();

    gossip1
        .getController()
        .registerSyncRequestHandler(
            TYPE_1,
            request -> {
              request.addPayload(gossip1.getAddress(), PAYLOAD_1);

              return CompletableActorFuture.completed(null);
            });

    gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);

    waitUntilCustomEventIsSpread();

    // when
    gossip3.join(gossip1).join();

    // then
    final CustomEvent customEvent =
        gossip3.getReceivedCustomEvents(TYPE_1, gossip1).findFirst().get();
    BufferAssert.assertThatBuffer(customEvent.getPayload())
        .hasCapacity(PAYLOAD_1.capacity())
        .hasBytes(PAYLOAD_1);
  }

  @Test
  public void shouldReceiveCustomEventsWithDifferentTypes() {
    // given
    gossip2.join(gossip1).join();

    gossip1
        .getController()
        .registerSyncRequestHandler(
            TYPE_1,
            request -> {
              request.addPayload(gossip1.getAddress(), PAYLOAD_1);

              return CompletableActorFuture.completed(null);
            });

    gossip1
        .getController()
        .registerSyncRequestHandler(
            TYPE_2,
            request -> {
              request.addPayload(gossip1.getAddress(), PAYLOAD_2);

              return CompletableActorFuture.completed(null);
            });

    gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
    gossip1.getPushlisher().publishEvent(TYPE_2, PAYLOAD_2);

    waitUntilCustomEventIsSpread();
    waitUntilCustomEventIsSpread();

    // when
    gossip3.join(gossip1).join();

    // then
    final CustomEvent customEvent1 =
        gossip3.getReceivedCustomEvents(TYPE_1, gossip1).findFirst().get();
    BufferAssert.assertThatBuffer(customEvent1.getPayload())
        .hasCapacity(PAYLOAD_1.capacity())
        .hasBytes(PAYLOAD_1);

    final CustomEvent customEvent2 =
        gossip3.getReceivedCustomEvents(TYPE_2, gossip1).findFirst().get();
    BufferAssert.assertThatBuffer(customEvent2.getPayload())
        .hasCapacity(PAYLOAD_2.capacity())
        .hasBytes(PAYLOAD_2);
  }

  @Test
  public void shouldReceiveCustomEventsWithSameTypes() {
    // given
    gossip2.join(gossip1).join();

    gossip1
        .getController()
        .registerSyncRequestHandler(
            TYPE_1,
            request -> {
              request
                  .addPayload(gossip1.getAddress(), PAYLOAD_1)
                  .addPayload(gossip2.getAddress(), PAYLOAD_2);

              return CompletableActorFuture.completed(null);
            });

    gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);
    gossip2.getPushlisher().publishEvent(TYPE_1, PAYLOAD_2);

    waitUntilCustomEventIsSpread();

    // when
    gossip3.join(gossip1).join();

    // then
    final CustomEvent customEvent1 =
        gossip3.getReceivedCustomEvents(TYPE_1, gossip1).findFirst().get();
    BufferAssert.assertThatBuffer(customEvent1.getPayload())
        .hasCapacity(PAYLOAD_1.capacity())
        .hasBytes(PAYLOAD_1);

    final CustomEvent customEvent2 =
        gossip3.getReceivedCustomEvents(TYPE_1, gossip2).findFirst().get();
    BufferAssert.assertThatBuffer(customEvent2.getPayload())
        .hasCapacity(PAYLOAD_2.capacity())
        .hasBytes(PAYLOAD_2);
  }

  @Test
  public void shouldProcessConcurrentSyncRequest() {
    // given
    gossip1.getPushlisher().publishEvent(TYPE_1, PAYLOAD_1);

    final CompletableActorFuture<Void> syncRequestFuture = new CompletableActorFuture<>();

    gossip1
        .getController()
        .registerSyncRequestHandler(
            TYPE_1,
            request -> {
              request.addPayload(gossip1.getAddress(), PAYLOAD_1);

              return syncRequestFuture;
            });

    // when
    final ActorFuture<Void> joinFuture1 = gossip2.join(gossip1);
    final ActorFuture<Void> joinFuture2 = gossip3.join(gossip1);

    cluster.waitUntil(() -> gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip2));
    cluster.waitUntil(() -> gossip1.receivedEvent(GossipEventType.SYNC_REQUEST, gossip3));

    syncRequestFuture.complete(null);

    joinFuture1.join();
    joinFuture2.join();

    // then
    cluster.waitUntil(() -> (gossip2.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1)));
    cluster.waitUntil(() -> (gossip3.receivedEvent(GossipEventType.SYNC_RESPONSE, gossip1)));

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

  private void waitUntilCustomEventIsSpread() {
    // wait until custom event is spread to ensure that it isn't send via ACK
    final long spreadCount =
        GossipMath.gossipPeriodsToSpread(CONFIGURATION.getRetransmissionMultiplier(), 3)
            + gossip2.getReceivedGossipEvents(GossipEventType.PING, gossip1).count()
            + gossip3.getReceivedGossipEvents(GossipEventType.PING, gossip1).count();

    cluster.waitUntil(
        () -> {
          // we don't count the received custom events because we can miss some
          // - if a request times out (when we manipulate the clock)
          final long eventsFrom2 =
              gossip2.getReceivedGossipEvents(GossipEventType.PING, gossip1).count();
          final long eventsFrom3 =
              gossip3.getReceivedGossipEvents(GossipEventType.PING, gossip1).count();

          return eventsFrom2 + eventsFrom3 >= spreadCount;
        });
  }
}
