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

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.common.util.concurrent.MoreExecutors;
import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.ManagedClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.TestBootstrapService;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DefaultClusterMembershipService;
import io.atomix.cluster.impl.DefaultNodeDiscoveryService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.ManagedClusterEventService;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.cluster.protocol.SwimMembershipProtocol;
import io.atomix.utils.Managed;
import io.atomix.utils.Version;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Test;

/** Cluster event service test. */
public class DefaultClusterEventServiceTest {

  private static final Serializer SERIALIZER = Serializer.using(Namespaces.BASIC);

  private final TestMessagingServiceFactory messagingServiceFactory =
      new TestMessagingServiceFactory();
  private final TestUnicastServiceFactory unicastServiceFactory = new TestUnicastServiceFactory();

  private final Map<Integer, Managed> managedMemberShipServices = new HashMap<>();
  private final Map<Integer, Managed> managedEventService = new HashMap<>();
  private CountDownLatch membersDiscovered;

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

  private ClusterEventService buildServices(
      final int memberId, final Collection<Node> bootstrapLocations) {

    final Member localMember = buildNode(memberId);
    final MessagingService messagingService =
        messagingServiceFactory.newMessagingService(localMember.address()).start().join();
    final BootstrapService bootstrapService1 =
        new TestBootstrapService(
            messagingService,
            unicastServiceFactory.newUnicastService(localMember.address()).start().join());
    final ManagedClusterMembershipService managedClusterMembershipService =
        new DefaultClusterMembershipService(
            localMember,
            "testingPrefix",
            Version.from("1.0.0"),
            new DefaultNodeDiscoveryService(
                bootstrapService1, localMember, new BootstrapDiscoveryProvider(bootstrapLocations)),
            bootstrapService1,
            SwimMembershipProtocol.builder().build());
    managedMemberShipServices.put(memberId, managedClusterMembershipService);
    managedClusterMembershipService.addListener(event -> membersDiscovered.countDown());
    final ClusterMembershipService clusterMembershipService =
        managedClusterMembershipService.start().join();
    final ManagedClusterEventService clusterEventingService1 =
        new DefaultClusterEventService(clusterMembershipService, messagingService);
    managedEventService.put(memberId, clusterEventingService1);
    return clusterEventingService1.start().join();
  }

  @After
  public void tearDown() {
    CompletableFuture.allOf(
            managedMemberShipServices.values().stream()
                .map(Managed::stop)
                .toArray(CompletableFuture[]::new))
        .join();
    CompletableFuture.allOf(
            managedEventService.values().stream()
                .map(Managed::stop)
                .toArray(CompletableFuture[]::new))
        .join();
  }

  @Test
  public void shouldBroadcast() throws InterruptedException {
    // given
    membersDiscovered = new CountDownLatch(6);
    final Collection<Node> bootstrapLocations = buildBootstrapNodes(3);
    final ClusterEventService eventService1 = buildServices(1, bootstrapLocations);
    final ClusterEventService eventService2 = buildServices(2, bootstrapLocations);
    final ClusterEventService eventService3 = buildServices(3, bootstrapLocations);
    membersDiscovered.await();
    final Set<Integer> events = new CopyOnWriteArraySet<>();
    final CountDownLatch latch = new CountDownLatch(3);

    final String topic = "test-topic";

    eventService1
        .<String>subscribe(
            topic,
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(1);
              latch.countDown();
            },
            MoreExecutors.directExecutor())
        .join();

    eventService2
        .<String>subscribe(
            topic,
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(2);
              latch.countDown();
            },
            MoreExecutors.directExecutor())
        .join();

    eventService3
        .<String>subscribe(
            topic,
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(3);
              latch.countDown();
            },
            MoreExecutors.directExecutor())
        .join();

    waitUntil(
        () ->
            eventService3
                .getSubscribers(topic)
                .containsAll(Set.of(MemberId.from("1"), MemberId.from("2"))));

    // when
    eventService3.broadcast(topic, "Hello world!", SERIALIZER::encode);

    // then
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertEquals(3, events.size());
  }

  @Test
  public void shouldSubscribeMultipleTopics() throws InterruptedException {
    // given
    membersDiscovered = new CountDownLatch(4);
    final Collection<Node> bootstrapLocations = buildBootstrapNodes(2);
    final ClusterEventService eventService1 = buildServices(1, bootstrapLocations);
    final ClusterEventService eventService2 = buildServices(2, bootstrapLocations);
    membersDiscovered.await();
    final Set<Integer> events = new CopyOnWriteArraySet<>();
    final CountDownLatch latch = new CountDownLatch(2);

    final String topic1 = "test-topic1";
    final String topic2 = "test-topic2";
    eventService1
        .<String>subscribe(
            topic1,
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(1);
              latch.countDown();
            },
            MoreExecutors.directExecutor())
        .join();

    eventService1
        .<String>subscribe(
            topic2,
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(2);
              latch.countDown();
            },
            MoreExecutors.directExecutor())
        .join();

    waitUntil(() -> eventService2.getSubscribers(topic1).contains(MemberId.from("1")));
    waitUntil(() -> eventService2.getSubscribers(topic2).contains(MemberId.from("1")));

    // when
    eventService2.broadcast(topic1, "Hello world!", SERIALIZER::encode);
    eventService2.broadcast(topic2, "Hello world!", SERIALIZER::encode);

    // then
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertEquals(2, events.size());
  }

  @Test
  public void shouldBroadcastToMultipleLocalSubscriptionsForSameTopic()
      throws InterruptedException {
    // given
    membersDiscovered = new CountDownLatch(0);
    final Collection<Node> bootstrapLocations = buildBootstrapNodes(1);
    final ClusterEventService eventService1 = buildServices(1, bootstrapLocations);

    final Set<Integer> events = new CopyOnWriteArraySet<>();
    final CountDownLatch latch = new CountDownLatch(2);

    final String topic = "test-topic1";

    eventService1
        .<String>subscribe(
            topic,
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(1);
              latch.countDown();
            },
            MoreExecutors.directExecutor())
        .join();

    eventService1
        .<String>subscribe(
            topic,
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(2);
              latch.countDown();
            },
            MoreExecutors.directExecutor())
        .join();

    // when
    eventService1.broadcast(topic, "Hello world!", SERIALIZER::encode);

    // then
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertEquals(2, events.size());
    assertThat(events).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  public void shouldNotCloseOtherSubscriptions() throws InterruptedException {
    // given
    membersDiscovered = new CountDownLatch(0);
    final Collection<Node> bootstrapLocations = buildBootstrapNodes(1);
    final ClusterEventService eventService1 = buildServices(1, bootstrapLocations);

    final Set<Integer> events = new CopyOnWriteArraySet<>();
    final CountDownLatch latch = new CountDownLatch(1);

    final String topic = "test-topic1";

    final var subscriptionToClose =
        eventService1
            .<String>subscribe(
                topic, SERIALIZER::decode, message -> {}, MoreExecutors.directExecutor())
            .join();

    eventService1
        .<String>subscribe(
            topic,
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(2);
              latch.countDown();
            },
            MoreExecutors.directExecutor())
        .join();

    // when
    subscriptionToClose.close().join();
    eventService1.broadcast(topic, "Hello world!", SERIALIZER::encode);

    // then
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertEquals(1, events.size());
  }

  @Test
  public void shouldBroadcastAfterRestart() throws InterruptedException {
    // given
    membersDiscovered = new CountDownLatch(4);
    final Collection<Node> bootstrapLocations = buildBootstrapNodes(2);
    final ClusterEventService eventService1 = buildServices(1, bootstrapLocations);
    final ClusterEventService eventService2 = buildServices(2, bootstrapLocations);
    membersDiscovered.await();
    final Set<Integer> events = new CopyOnWriteArraySet<>();
    final CountDownLatch latch = new CountDownLatch(1);

    final String topic = "test-topic";
    eventService1
        .<String>subscribe(
            topic,
            SERIALIZER::decode,
            message -> {
              assertEquals("Hello world!", message);
              events.add(1);
              latch.countDown();
            },
            MoreExecutors.directExecutor())
        .join();

    waitUntil(() -> eventService2.getSubscribers(topic).contains(MemberId.from("1")));

    // when
    // restart 2
    managedMemberShipServices.get(2).stop().join();
    managedEventService.get(2).stop().join();
    final ClusterEventService eventService2Restarted = buildServices(2, bootstrapLocations);

    // then
    waitUntil(() -> eventService2Restarted.getSubscribers(topic).contains(MemberId.from("1")));

    eventService2Restarted.broadcast(topic, "Hello world!", SERIALIZER::encode);

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertEquals(1, events.size());
  }

  @Test
  public void shouldLogHandlerFailuresWithoutCrashing() throws InterruptedException {
    final Collection<Node> bootstrapLocations = buildBootstrapNodes(1);
    final ClusterEventService eventService1 = buildServices(1, bootstrapLocations);

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
