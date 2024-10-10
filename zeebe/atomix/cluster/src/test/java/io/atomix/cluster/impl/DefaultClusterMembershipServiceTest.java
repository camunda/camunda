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

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.NodeId;
import io.atomix.cluster.TestBootstrapService;
import io.atomix.cluster.TestDiscoveryProvider;
import io.atomix.cluster.discovery.ManagedNodeDiscoveryService;
import io.atomix.cluster.messaging.impl.TestMessagingServiceFactory;
import io.atomix.cluster.messaging.impl.TestUnicastServiceFactory;
import io.atomix.utils.Version;
import io.atomix.utils.net.Address;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class DefaultClusterMembershipServiceTest {

  private final TestMessagingServiceFactory messagingServiceFactory =
      new TestMessagingServiceFactory();
  private final TestUnicastServiceFactory unicastServiceFactory = new TestUnicastServiceFactory();
  private final Member localMember = Member.member("0", "localhost:5000");
  private final Version version = Version.from("1.0.0");
  private final BootstrapService bootstrapService =
      new TestBootstrapService(
          messagingServiceFactory.newMessagingService(localMember.address()),
          unicastServiceFactory.newUnicastService(localMember.address()));
  private final TestDiscoveryProvider discoveryProvider = new TestDiscoveryProvider();
  private final ManagedNodeDiscoveryService discoveryService =
      new DefaultNodeDiscoveryService(bootstrapService, localMember, discoveryProvider);
  private final DiscoveryMembershipProtocol protocol = new DiscoveryMembershipProtocol();

  @Test
  void shouldManageDiscoveryService() {
    // given
    final var membershipService =
        new DefaultClusterMembershipService(
            localMember,
            "membershipTestPrefix",
            version,
            discoveryService,
            bootstrapService,
            protocol);

    // when - then
    membershipService.start().join();
    assertThat(discoveryService.isRunning()).as("the discovery service is now running").isTrue();

    // when - then
    membershipService.stop().join();
    assertThat(discoveryService.isRunning())
        .as("the discovery service is not running anymore")
        .isFalse();
  }

  @Test
  void shouldGetLocalMember() {
    // given
    final var membershipService =
        new DefaultClusterMembershipService(
            localMember,
            "membershipTestPrefix",
            version,
            discoveryService,
            bootstrapService,
            protocol);

    // when
    membershipService.start().join();

    // then
    final var expectedMember = new StatefulMember(localMember, version);
    assertThat(membershipService.getLocalMember())
        .as("the local member is member 0")
        .isEqualTo(expectedMember);

    membershipService.stop().join();
  }

  @Test
  void shouldGetMembers() {
    // given
    final var membershipService =
        new DefaultClusterMembershipService(
            localMember,
            "membershipTestPrefix",
            version,
            discoveryService,
            bootstrapService,
            protocol);

    // when
    protocol.getMembers().add(Member.member("1", "localhost:5001"));
    membershipService.start().join();

    // then
    assertThat(membershipService.getMembers())
        .as("there should be at least one member")
        .isNotEmpty()
        .as("the reported members are exactly the same as the protocol's")
        .containsExactlyInAnyOrderElementsOf(protocol.getMembers());

    membershipService.stop().join();
  }

  @Test
  void shouldTrackLocalMemberReachability() {
    // given
    final var membershipService =
        new DefaultClusterMembershipService(
            localMember,
            "membershipTestPrefix",
            version,
            discoveryService,
            bootstrapService,
            protocol);

    // when - then
    membershipService.start().join();
    assertThat(membershipService.getLocalMember().isActive())
        .as("local member is active after starting")
        .isTrue();

    // when - then
    membershipService.stop().join();
    assertThat(membershipService.getLocalMember().isActive())
        .as("local member is not active after stopping")
        .isFalse();
  }

  @Test
  void shouldTrackReachabilityOfLocalMember() {
    // given
    final var membershipService =
        new DefaultClusterMembershipService(
            localMember,
            "membershipTestPrefix",
            version,
            discoveryService,
            bootstrapService,
            protocol);

    // when - then
    membershipService.start().join();
    assertThat(membershipService.getLocalMember().isReachable())
        .as("local member is reachable after starting")
        .isTrue();

    // when - then
    membershipService.stop().join();
    assertThat(membershipService.getLocalMember().isReachable())
        .as("local member is not reachable after stopping")
        .isFalse();
  }

  @Test
  void shouldManageGroupMembershipOfLocalMember() {
    // given
    final var membershipService =
        new DefaultClusterMembershipService(
            localMember,
            "membershipTestPrefix",
            version,
            discoveryService,
            bootstrapService,
            protocol);

    // when - then
    membershipService.start().join();
    assertThat(protocol.getMembers())
        .as("local member has joined the group protocol")
        .containsExactly(membershipService.getLocalMember());

    // when - then
    membershipService.stop().join();
    assertThat(protocol.getMembers()).as("local member has left the group protocol").isEmpty();
  }

  @Test
  void shouldForwardGroupEvents() {
    // given
    final var membershipService =
        new DefaultClusterMembershipService(
            localMember,
            "membershipTestPrefix",
            version,
            discoveryService,
            bootstrapService,
            protocol);
    membershipService.start().join();

    // when
    final var nodeAddress = Address.from("localhost", 5002);
    final var receivedEvent = new AtomicReference<ClusterMembershipEvent>();
    membershipService.addListener(receivedEvent::set);
    discoveryProvider.join(
        bootstrapService, Node.builder().withId(NodeId.from("1")).withAddress(nodeAddress).build());

    // then
    assertThat(receivedEvent)
        .as("the received event is the same as the one sent")
        .hasValueSatisfying(
            event ->
                assertThat(event)
                    .extracting(ClusterMembershipEvent::type, ClusterMembershipEvent::subject)
                    .containsExactly(
                        Type.MEMBER_ADDED, Member.member(MemberId.from("1"), nodeAddress)));

    membershipService.stop().join();
  }
}
