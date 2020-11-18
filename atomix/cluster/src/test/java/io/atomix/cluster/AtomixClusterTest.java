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
import io.atomix.utils.net.Address;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
}
