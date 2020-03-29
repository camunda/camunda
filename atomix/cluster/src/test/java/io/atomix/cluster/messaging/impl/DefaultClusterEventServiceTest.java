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
package io.atomix.cluster.messaging.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.util.concurrent.MoreExecutors;
import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.ManagedClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.Node;
import io.atomix.cluster.TestBootstrapService;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DefaultClusterMembershipService;
import io.atomix.cluster.impl.DefaultNodeDiscoveryService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.ManagedClusterEventService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.protocol.HeartbeatMembershipProtocol;
import io.atomix.cluster.protocol.HeartbeatMembershipProtocolConfig;
import io.atomix.utils.Version;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

/** Cluster event service test. */
public class DefaultClusterEventServiceTest {
  private static final Serializer SERIALIZER = Serializer.using(Namespaces.BASIC);

  private Member buildNode(final int memberId) {
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
  public void testClusterEventService() throws Exception {
    final TestMessagingServiceFactory messagingServiceFactory = new TestMessagingServiceFactory();
    final TestUnicastServiceFactory unicastServiceFactory = new TestUnicastServiceFactory();
    final TestBroadcastServiceFactory broadcastServiceFactory = new TestBroadcastServiceFactory();

    final Collection<Node> bootstrapLocations = buildBootstrapNodes(3);

    final Member localMember1 = buildNode(1);
    final MessagingService messagingService1 =
        messagingServiceFactory.newMessagingService(localMember1.address()).start().join();
    final BootstrapService bootstrapService1 =
        new TestBootstrapService(
            messagingService1,
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
            new HeartbeatMembershipProtocol(new HeartbeatMembershipProtocolConfig()));
    final ClusterMembershipService clusterMembershipService1 = clusterService1.start().join();
    final ManagedClusterEventService clusterEventingService1 =
        new DefaultClusterEventService(clusterMembershipService1, messagingService1);
    final ClusterEventService eventService1 = clusterEventingService1.start().join();

    final Member localMember2 = buildNode(2);
    final MessagingService messagingService2 =
        messagingServiceFactory.newMessagingService(localMember2.address()).start().join();
    final BootstrapService bootstrapService2 =
        new TestBootstrapService(
            messagingService2,
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
            new HeartbeatMembershipProtocol(new HeartbeatMembershipProtocolConfig()));
    final ClusterMembershipService clusterMembershipService2 = clusterService2.start().join();
    final ManagedClusterEventService clusterEventingService2 =
        new DefaultClusterEventService(clusterMembershipService2, messagingService2);
    final ClusterEventService eventService2 = clusterEventingService2.start().join();

    final Member localMember3 = buildNode(3);
    final MessagingService messagingService3 =
        messagingServiceFactory.newMessagingService(localMember3.address()).start().join();
    final BootstrapService bootstrapService3 =
        new TestBootstrapService(
            messagingService3,
            unicastServiceFactory.newUnicastService(localMember1.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join());
    final ManagedClusterMembershipService clusterService3 =
        new DefaultClusterMembershipService(
            localMember3,
            Version.from("1.0.0"),
            new DefaultNodeDiscoveryService(
                bootstrapService3,
                localMember3,
                new BootstrapDiscoveryProvider(bootstrapLocations)),
            bootstrapService3,
            new HeartbeatMembershipProtocol(new HeartbeatMembershipProtocolConfig()));
    final ClusterMembershipService clusterMembershipService3 = clusterService3.start().join();
    final ManagedClusterEventService clusterEventingService3 =
        new DefaultClusterEventService(clusterMembershipService3, messagingService3);
    final ClusterEventService eventService3 = clusterEventingService3.start().join();

    Thread.sleep(250);

    final Set<Integer> events = new CopyOnWriteArraySet<>();

    eventService1
        .<String>subscribe(
            "test1",
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(1);
            },
            MoreExecutors.directExecutor())
        .join();

    eventService2
        .<String>subscribe(
            "test1",
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(2);
            },
            MoreExecutors.directExecutor())
        .join();

    eventService2
        .<String>subscribe(
            "test1",
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(3);
            },
            MoreExecutors.directExecutor())
        .join();

    eventService3.broadcast("test1", "Hello world!", SERIALIZER::encode);

    Thread.sleep(100);

    assertEquals(3, events.size());
    events.clear();

    eventService3.unicast("test1", "Hello world!");
    Thread.sleep(100);
    assertEquals(1, events.size());
    assertTrue(events.contains(3));
    events.clear();

    eventService3.unicast("test1", "Hello world!");
    Thread.sleep(100);
    assertEquals(1, events.size());
    assertTrue(events.contains(1));
    events.clear();

    eventService3.unicast("test1", "Hello world!");
    Thread.sleep(100);
    assertEquals(1, events.size());
    assertTrue(events.contains(2));
    events.clear();

    eventService3.unicast("test1", "Hello world!");
    Thread.sleep(100);
    assertEquals(1, events.size());
    assertTrue(events.contains(3));
    events.clear();

    eventService1
        .<String, String>subscribe(
            "test2",
            SERIALIZER::decode,
            message -> {
              events.add(1);
              return message;
            },
            SERIALIZER::encode,
            MoreExecutors.directExecutor())
        .join();
    eventService2
        .<String, String>subscribe(
            "test2",
            SERIALIZER::decode,
            message -> {
              events.add(2);
              return message;
            },
            SERIALIZER::encode,
            MoreExecutors.directExecutor())
        .join();

    assertEquals("Hello world!", eventService3.send("test2", "Hello world!").join());
    assertEquals(1, events.size());
    assertTrue(events.contains(1));
    events.clear();

    assertEquals("Hello world!", eventService3.send("test2", "Hello world!").join());
    assertEquals(1, events.size());
    assertTrue(events.contains(2));
    events.clear();

    assertEquals("Hello world!", eventService3.send("test2", "Hello world!").join());
    assertEquals(1, events.size());
    assertTrue(events.contains(1));

    CompletableFuture.allOf(
            new CompletableFuture[] {
              clusterEventingService1.stop(),
              clusterEventingService2.stop(),
              clusterEventingService3.stop()
            })
        .join();

    CompletableFuture.allOf(
            new CompletableFuture[] {
              clusterService1.stop(), clusterService2.stop(), clusterService3.stop()
            })
        .join();
  }

  @Test
  public void shouldLogHandlerFailuresWithoutCrashing() throws InterruptedException {
    final TestMessagingServiceFactory messagingServiceFactory = new TestMessagingServiceFactory();
    final TestUnicastServiceFactory unicastServiceFactory = new TestUnicastServiceFactory();
    final TestBroadcastServiceFactory broadcastServiceFactory = new TestBroadcastServiceFactory();
    final Collection<Node> bootstrapLocations = buildBootstrapNodes(1);

    final Member localMember1 = buildNode(1);
    final MessagingService messagingService1 =
        messagingServiceFactory.newMessagingService(localMember1.address()).start().join();
    final BootstrapService bootstrapService1 =
        new TestBootstrapService(
            messagingService1,
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
            new HeartbeatMembershipProtocol(new HeartbeatMembershipProtocolConfig()));
    final ClusterMembershipService clusterMembershipService1 = clusterService1.start().join();
    final ManagedClusterEventService clusterEventingService1 =
        new DefaultClusterEventService(clusterMembershipService1, messagingService1);
    final ClusterEventService eventService1 = clusterEventingService1.start().join();

    final AtomicInteger eventsCounter = new AtomicInteger(0);
    final CountDownLatch awaitCompletion = new CountDownLatch(1);
    final AtomicReference<String> received = new AtomicReference<>("");
    eventService1
        .<String>subscribe(
            "test",
            SERIALIZER::decode,
            s -> {
              received.set(s);

              if (eventsCounter.getAndIncrement() == 0) {
                throw new RuntimeException("e");
              }

              awaitCompletion.countDown();
            },
            MoreExecutors.directExecutor())
        .join();
    eventService1.broadcast("test", "foo");
    eventService1.broadcast("test", "bar");
    awaitCompletion.await(10, TimeUnit.SECONDS);
    assertEquals("bar", received.get());
  }
}
