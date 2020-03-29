/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ManagedClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.TestBootstrapService;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.messaging.impl.TestBroadcastServiceFactory;
import io.atomix.cluster.messaging.impl.TestMessagingServiceFactory;
import io.atomix.cluster.messaging.impl.TestUnicastServiceFactory;
import io.atomix.cluster.protocol.HeartbeatMembershipProtocol;
import io.atomix.cluster.protocol.HeartbeatMembershipProtocolConfig;
import io.atomix.utils.Version;
import io.atomix.utils.net.Address;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

/** Default cluster service test. */
public class DefaultClusterMembershipServiceTest {

  private Member buildMember(final int memberId) {
    return Member.builder(String.valueOf(memberId))
        .withHost("localhost")
        .withPort(memberId)
        .build();
  }

  private Collection<Node> buildBootstrapNodes(final int nodes) {
    return IntStream.range(1, nodes + 1)
        .mapToObj(
            id ->
                Node.builder()
                    .withId(String.valueOf(id))
                    .withAddress(Address.from("localhost", id))
                    .build())
        .collect(Collectors.toList());
  }

  @Test
  public void testClusterService() throws Exception {
    final TestMessagingServiceFactory messagingServiceFactory = new TestMessagingServiceFactory();
    final TestUnicastServiceFactory unicastServiceFactory = new TestUnicastServiceFactory();
    final TestBroadcastServiceFactory broadcastServiceFactory = new TestBroadcastServiceFactory();

    final Collection<Node> bootstrapLocations = buildBootstrapNodes(3);

    final Member localMember1 = buildMember(1);
    final BootstrapService bootstrapService1 =
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(localMember1.address()).start().join(),
            unicastServiceFactory.newUnicastService(localMember1.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join());
    final ManagedClusterMembershipService clusterService1 =
        new DefaultClusterMembershipService(
            localMember1,
            Version.from("1.0.0"),
            new DefaultNodeDiscoveryService(
                bootstrapService1,
                localMember1,
                new BootstrapDiscoveryProvider(bootstrapLocations)),
            bootstrapService1,
            new HeartbeatMembershipProtocol(
                new HeartbeatMembershipProtocolConfig().setFailureTimeout(Duration.ofSeconds(2))));

    final Member localMember2 = buildMember(2);
    final BootstrapService bootstrapService2 =
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(localMember2.address()).start().join(),
            unicastServiceFactory.newUnicastService(localMember2.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join());
    final ManagedClusterMembershipService clusterService2 =
        new DefaultClusterMembershipService(
            localMember2,
            Version.from("1.0.0"),
            new DefaultNodeDiscoveryService(
                bootstrapService2,
                localMember2,
                new BootstrapDiscoveryProvider(bootstrapLocations)),
            bootstrapService2,
            new HeartbeatMembershipProtocol(
                new HeartbeatMembershipProtocolConfig().setFailureTimeout(Duration.ofSeconds(2))));

    final Member localMember3 = buildMember(3);
    final BootstrapService bootstrapService3 =
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(localMember3.address()).start().join(),
            unicastServiceFactory.newUnicastService(localMember3.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join());
    final ManagedClusterMembershipService clusterService3 =
        new DefaultClusterMembershipService(
            localMember3,
            Version.from("1.0.1"),
            new DefaultNodeDiscoveryService(
                bootstrapService3,
                localMember3,
                new BootstrapDiscoveryProvider(bootstrapLocations)),
            bootstrapService3,
            new HeartbeatMembershipProtocol(
                new HeartbeatMembershipProtocolConfig().setFailureTimeout(Duration.ofSeconds(2))));

    assertNull(clusterService1.getMember(MemberId.from("1")));
    assertNull(clusterService1.getMember(MemberId.from("2")));
    assertNull(clusterService1.getMember(MemberId.from("3")));

    CompletableFuture.allOf(
            new CompletableFuture[] {
              clusterService1.start(), clusterService2.start(), clusterService3.start()
            })
        .join();

    Thread.sleep(5000);

    assertEquals(3, clusterService1.getMembers().size());
    assertEquals(3, clusterService2.getMembers().size());
    assertEquals(3, clusterService3.getMembers().size());

    assertTrue(clusterService1.getLocalMember().isActive());
    assertTrue(clusterService1.getMember(MemberId.from("1")).isActive());
    assertTrue(clusterService1.getMember(MemberId.from("2")).isActive());
    assertTrue(clusterService1.getMember(MemberId.from("3")).isActive());

    assertEquals("1.0.0", clusterService1.getMember("1").version().toString());
    assertEquals("1.0.0", clusterService1.getMember("2").version().toString());
    assertEquals("1.0.1", clusterService1.getMember("3").version().toString());

    final Member anonymousMember = buildMember(4);
    final BootstrapService ephemeralBootstrapService =
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(anonymousMember.address()).start().join(),
            unicastServiceFactory.newUnicastService(anonymousMember.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join());
    final ManagedClusterMembershipService ephemeralClusterService =
        new DefaultClusterMembershipService(
            anonymousMember,
            Version.from("1.1.0"),
            new DefaultNodeDiscoveryService(
                ephemeralBootstrapService,
                anonymousMember,
                new BootstrapDiscoveryProvider(bootstrapLocations)),
            ephemeralBootstrapService,
            new HeartbeatMembershipProtocol(
                new HeartbeatMembershipProtocolConfig().setFailureTimeout(Duration.ofSeconds(2))));

    assertFalse(ephemeralClusterService.getLocalMember().isActive());

    assertNull(ephemeralClusterService.getMember(MemberId.from("1")));
    assertNull(ephemeralClusterService.getMember(MemberId.from("2")));
    assertNull(ephemeralClusterService.getMember(MemberId.from("3")));
    assertNull(ephemeralClusterService.getMember(MemberId.from("4")));
    assertNull(ephemeralClusterService.getMember(MemberId.from("5")));

    ephemeralClusterService.start().join();

    Thread.sleep(1000);

    assertEquals(4, clusterService1.getMembers().size());
    assertEquals(4, clusterService2.getMembers().size());
    assertEquals(4, clusterService3.getMembers().size());
    assertEquals(4, ephemeralClusterService.getMembers().size());

    assertEquals("1.0.0", clusterService1.getMember("1").version().toString());
    assertEquals("1.0.0", clusterService1.getMember("2").version().toString());
    assertEquals("1.0.1", clusterService1.getMember("3").version().toString());
    assertEquals("1.1.0", clusterService1.getMember("4").version().toString());

    clusterService1.stop().join();

    Thread.sleep(5000);

    assertEquals(3, clusterService2.getMembers().size());

    assertNull(clusterService2.getMember(MemberId.from("1")));
    assertTrue(clusterService2.getMember(MemberId.from("2")).isActive());
    assertTrue(clusterService2.getMember(MemberId.from("3")).isActive());
    assertTrue(clusterService2.getMember(MemberId.from("4")).isActive());

    ephemeralClusterService.stop().join();

    Thread.sleep(5000);

    assertEquals(2, clusterService2.getMembers().size());
    assertNull(clusterService2.getMember(MemberId.from("1")));
    assertTrue(clusterService2.getMember(MemberId.from("2")).isActive());
    assertTrue(clusterService2.getMember(MemberId.from("3")).isActive());
    assertNull(clusterService2.getMember(MemberId.from("4")));

    Thread.sleep(2500);

    assertEquals(2, clusterService2.getMembers().size());

    assertNull(clusterService2.getMember(MemberId.from("1")));
    assertTrue(clusterService2.getMember(MemberId.from("2")).isActive());
    assertTrue(clusterService2.getMember(MemberId.from("3")).isActive());
    assertNull(clusterService2.getMember(MemberId.from("4")));

    final TestClusterMembershipEventListener eventListener =
        new TestClusterMembershipEventListener();
    clusterService2.addListener(eventListener);

    ClusterMembershipEvent event;
    clusterService3.getLocalMember().properties().put("foo", "bar");

    event = eventListener.nextEvent();
    assertEquals(ClusterMembershipEvent.Type.METADATA_CHANGED, event.type());
    assertEquals("bar", event.subject().properties().get("foo"));

    clusterService3.getLocalMember().properties().put("foo", "baz");

    event = eventListener.nextEvent();
    assertEquals(ClusterMembershipEvent.Type.METADATA_CHANGED, event.type());
    assertEquals("baz", event.subject().properties().get("foo"));

    CompletableFuture.allOf(
            new CompletableFuture[] {
              clusterService1.stop(), clusterService2.stop(), clusterService3.stop()
            })
        .join();
  }

  private class TestClusterMembershipEventListener implements ClusterMembershipEventListener {
    private BlockingQueue<ClusterMembershipEvent> queue =
        new ArrayBlockingQueue<ClusterMembershipEvent>(10);

    @Override
    public void event(final ClusterMembershipEvent event) {
      queue.add(event);
    }

    ClusterMembershipEvent nextEvent() {
      try {
        return queue.poll(5, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        return null;
      }
    }
  }
}
