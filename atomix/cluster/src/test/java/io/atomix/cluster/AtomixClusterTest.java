/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.cluster;

import static org.junit.Assert.assertEquals;

import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.MulticastDiscoveryProvider;
import io.atomix.utils.net.Address;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/** Atomix cluster test. */
public class AtomixClusterTest {

  @Test
  public void testBootstrap() throws Exception {
    final Collection<Node> bootstrapLocations =
        Arrays.asList(
            Node.builder().withId("foo").withAddress(Address.from("localhost:5000")).build(),
            Node.builder().withId("bar").withAddress(Address.from("localhost:5001")).build(),
            Node.builder().withId("baz").withAddress(Address.from("localhost:5002")).build());

    final AtomixCluster cluster1 =
        AtomixCluster.builder()
            .withMemberId("foo")
            .withHost("localhost")
            .withPort(5000)
            .withMembershipProvider(
                BootstrapDiscoveryProvider.builder().withNodes(bootstrapLocations).build())
            .build();
    cluster1.start().join();

    assertEquals("foo", cluster1.getMembershipService().getLocalMember().id().id());

    final AtomixCluster cluster2 =
        AtomixCluster.builder()
            .withMemberId("bar")
            .withHost("localhost")
            .withPort(5001)
            .withMembershipProvider(
                BootstrapDiscoveryProvider.builder().withNodes(bootstrapLocations).build())
            .build();
    cluster2.start().join();

    assertEquals("bar", cluster2.getMembershipService().getLocalMember().id().id());

    final AtomixCluster cluster3 =
        AtomixCluster.builder()
            .withMemberId("baz")
            .withHost("localhost")
            .withPort(5002)
            .withMembershipProvider(
                BootstrapDiscoveryProvider.builder().withNodes(bootstrapLocations).build())
            .build();
    cluster3.start().join();

    assertEquals("baz", cluster3.getMembershipService().getLocalMember().id().id());

    final List<CompletableFuture<Void>> futures =
        Stream.of(cluster1, cluster2, cluster3)
            .map(AtomixCluster::stop)
            .collect(Collectors.toList());
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
    } catch (final Exception e) {
      // Do nothing
    }
  }

  @Test
  public void testDiscovery() throws Exception {
    final AtomixCluster cluster1 =
        AtomixCluster.builder()
            .withHost("localhost")
            .withPort(5000)
            .withMulticastEnabled()
            .withMembershipProvider(new MulticastDiscoveryProvider())
            .build();
    final AtomixCluster cluster2 =
        AtomixCluster.builder()
            .withHost("localhost")
            .withPort(5001)
            .withMulticastEnabled()
            .withMembershipProvider(new MulticastDiscoveryProvider())
            .build();
    final AtomixCluster cluster3 =
        AtomixCluster.builder()
            .withHost("localhost")
            .withPort(5002)
            .withMulticastEnabled()
            .withMembershipProvider(new MulticastDiscoveryProvider())
            .build();

    final TestClusterMembershipEventListener listener1 = new TestClusterMembershipEventListener();
    cluster1.getMembershipService().addListener(listener1);
    final TestClusterMembershipEventListener listener2 = new TestClusterMembershipEventListener();
    cluster2.getMembershipService().addListener(listener2);
    final TestClusterMembershipEventListener listener3 = new TestClusterMembershipEventListener();
    cluster3.getMembershipService().addListener(listener3);

    final List<CompletableFuture<Void>> startFutures =
        Stream.of(cluster1, cluster2, cluster3)
            .map(AtomixCluster::start)
            .collect(Collectors.toList());
    CompletableFuture.allOf(startFutures.toArray(new CompletableFuture[startFutures.size()]))
        .get(10, TimeUnit.SECONDS);

    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, listener1.nextEvent().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, listener1.nextEvent().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, listener1.nextEvent().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, listener2.nextEvent().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, listener2.nextEvent().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, listener2.nextEvent().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, listener3.nextEvent().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, listener3.nextEvent().type());
    assertEquals(ClusterMembershipEvent.Type.MEMBER_ADDED, listener3.nextEvent().type());

    assertEquals(3, cluster1.getMembershipService().getMembers().size());
    assertEquals(3, cluster2.getMembershipService().getMembers().size());
    assertEquals(3, cluster3.getMembershipService().getMembers().size());

    final List<CompletableFuture<Void>> stopFutures =
        Stream.of(cluster1, cluster2, cluster3)
            .map(AtomixCluster::stop)
            .collect(Collectors.toList());
    try {
      CompletableFuture.allOf(stopFutures.toArray(new CompletableFuture[stopFutures.size()]))
          .get(10, TimeUnit.SECONDS);
    } catch (final Exception e) {
      // Do nothing
    }
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
        return queue.poll(10, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        return null;
      }
    }
  }
}
