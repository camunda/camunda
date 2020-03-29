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
package io.atomix.cluster.protocol;

import static io.atomix.cluster.protocol.GroupMembershipEvent.Type.MEMBER_ADDED;
import static io.atomix.cluster.protocol.GroupMembershipEvent.Type.MEMBER_REMOVED;
import static io.atomix.cluster.protocol.GroupMembershipEvent.Type.METADATA_CHANGED;
import static io.atomix.cluster.protocol.GroupMembershipEvent.Type.REACHABILITY_CHANGED;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.TestBootstrapService;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.cluster.discovery.NodeDiscoveryService;
import io.atomix.cluster.impl.DefaultNodeDiscoveryService;
import io.atomix.cluster.messaging.impl.TestBroadcastServiceFactory;
import io.atomix.cluster.messaging.impl.TestMessagingServiceFactory;
import io.atomix.cluster.messaging.impl.TestUnicastServiceFactory;
import io.atomix.utils.Version;
import io.atomix.utils.net.Address;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import net.jodah.concurrentunit.ConcurrentTestCase;
import org.junit.Before;
import org.junit.Test;

/** SWIM membership protocol test. */
public class SwimProtocolTest extends ConcurrentTestCase {
  private TestMessagingServiceFactory messagingServiceFactory = new TestMessagingServiceFactory();
  private TestUnicastServiceFactory unicastServiceFactory = new TestUnicastServiceFactory();
  private TestBroadcastServiceFactory broadcastServiceFactory = new TestBroadcastServiceFactory();

  private Member member1;
  private Member member2;
  private Member member3;
  private Collection<Member> members;
  private Collection<Node> nodes;

  private Version version1 = Version.from("1.0.0");
  private Version version2 = Version.from("2.0.0");

  private Map<MemberId, SwimMembershipProtocol> protocols = Maps.newConcurrentMap();
  private Map<MemberId, TestGroupMembershipEventListener> listeners = Maps.newConcurrentMap();

  private Member member(final String id, final String host, final int port, final Version version) {
    return new SwimMembershipProtocol.SwimMember(
        MemberId.from(id),
        new Address(host, port),
        null,
        null,
        null,
        new Properties(),
        version,
        System.currentTimeMillis());
  }

  @Before
  @SuppressWarnings("unchecked")
  public void reset() {
    messagingServiceFactory = new TestMessagingServiceFactory();
    unicastServiceFactory = new TestUnicastServiceFactory();
    broadcastServiceFactory = new TestBroadcastServiceFactory();

    member1 = member("1", "localhost", 5001, version1);
    member2 = member("2", "localhost", 5002, version1);
    member3 = member("3", "localhost", 5003, version1);
    members = Arrays.asList(member1, member2, member3);
    nodes = (Collection) members;
    listeners = Maps.newConcurrentMap();
  }

  @Test
  public void testSwimProtocol() throws Exception {
    // Start a node and check its events.
    startProtocol(member1);
    checkEvent(member1, MEMBER_ADDED, member1);
    checkMembers(member1, member1);

    // Start a node and check its events.
    startProtocol(member2);
    checkEvent(member2, MEMBER_ADDED, member2);
    checkEvent(member2, MEMBER_ADDED, member1);
    checkMembers(member2, member1, member2);
    checkEvent(member1, MEMBER_ADDED, member2);
    checkMembers(member1, member1, member2);

    // Start a node and check its events.
    startProtocol(member3);
    checkEvent(member3, MEMBER_ADDED, member3);
    checkEvent(member3, MEMBER_ADDED);
    checkEvent(member3, MEMBER_ADDED);
    checkMembers(member3, member1, member2, member3);
    checkEvent(member2, MEMBER_ADDED, member3);
    checkMembers(member2, member1, member2, member3);
    checkEvent(member1, MEMBER_ADDED, member3);
    checkMembers(member1, member1, member2, member3);

    // Isolate node 3 from the rest of the cluster.
    partition(member3);

    // Nodes 1 and 2 should see REACHABILITY_CHANGED events and then MEMBER_REMOVED events.
    checkEvent(member1, REACHABILITY_CHANGED, member3);
    checkEvent(member2, REACHABILITY_CHANGED, member3);
    checkEvent(member1, MEMBER_REMOVED, member3);
    checkEvent(member2, MEMBER_REMOVED, member3);

    // Verify that node 3 was removed from nodes 1 and 2.
    checkMembers(member1, member1, member2);
    checkMembers(member2, member1, member2);

    // Node 3 should also see REACHABILITY_CHANGED and MEMBER_REMOVED events for nodes 1 and 2.
    checkEvents(
        member3,
        new GroupMembershipEvent(REACHABILITY_CHANGED, member1),
        new GroupMembershipEvent(REACHABILITY_CHANGED, member2),
        new GroupMembershipEvent(MEMBER_REMOVED, member1),
        new GroupMembershipEvent(MEMBER_REMOVED, member2));

    // Verify that nodes 1 and 2 were removed from node 3.
    checkMembers(member3, member3);

    // Heal the partition.
    heal(member3);

    // Verify that the nodes discovery each other again.
    checkEvent(member1, MEMBER_ADDED, member3);
    checkEvent(member2, MEMBER_ADDED, member3);
    checkEvents(
        member3,
        new GroupMembershipEvent(MEMBER_ADDED, member1),
        new GroupMembershipEvent(MEMBER_ADDED, member2));

    // Partition node 1 from node 2.
    partition(member1, member2);

    // Verify that neither node is ever removed from the cluster since node 3 can still ping nodes 1
    // and 2.
    Thread.sleep(5000);
    checkMembers(member1, member1, member2, member3);
    checkMembers(member2, member1, member2, member3);
    checkMembers(member3, member1, member2, member3);

    // Heal the partition.
    heal(member1, member2);

    // Update node 1's metadata.
    member1.properties().put("foo", "bar");

    // Verify the metadata change is propagated throughout the cluster.
    checkEvent(member1, METADATA_CHANGED, member1);
    checkEvent(member2, METADATA_CHANGED, member1);
    checkEvent(member3, METADATA_CHANGED, member1);

    // Stop member 3 and change its version.
    stopProtocol(member3);
    final Member member =
        member(member3.id().id(), member3.address().host(), member3.address().port(), version2);
    startProtocol(member);

    // Verify that version 1 is removed and version 2 is added.
    checkEvent(member1, MEMBER_REMOVED, member3);
    checkEvent(member1, MEMBER_ADDED, member);
    checkEvent(member2, MEMBER_REMOVED, member3);
    checkEvent(member2, MEMBER_ADDED, member);
  }

  @Test
  public void shouldSynchronizePeriodically() throws InterruptedException {
    // given
    final Duration gossipInterval = Duration.ofMillis(100);
    final Duration syncInterval = Duration.ofMillis(500);
    startProtocol(member1, c -> c.setSyncInterval(syncInterval).setGossipInterval(gossipInterval));
    startProtocol(member2, c -> c.setSyncInterval(syncInterval).setGossipInterval(gossipInterval));
    final SwimMembershipProtocol protocol3 =
        startProtocol(
            member3, c -> c.setSyncInterval(syncInterval).setGossipInterval(gossipInterval));

    // wait for all nodes to know about each other
    checkEvents(
        member1,
        new GroupMembershipEvent(MEMBER_ADDED, member1),
        new GroupMembershipEvent(MEMBER_ADDED, member2),
        new GroupMembershipEvent(MEMBER_ADDED, member3));
    checkEvents(
        member2,
        new GroupMembershipEvent(MEMBER_ADDED, member1),
        new GroupMembershipEvent(MEMBER_ADDED, member2),
        new GroupMembershipEvent(MEMBER_ADDED, member3));
    checkEvents(
        member3,
        new GroupMembershipEvent(MEMBER_ADDED, member1),
        new GroupMembershipEvent(MEMBER_ADDED, member2),
        new GroupMembershipEvent(MEMBER_ADDED, member3));

    // when
    // isolate member3
    partition(member3);
    checkEvents(
        member1,
        new GroupMembershipEvent(REACHABILITY_CHANGED, member3),
        new GroupMembershipEvent(MEMBER_REMOVED, member3));
    checkEvents(
        member2,
        new GroupMembershipEvent(REACHABILITY_CHANGED, member3),
        new GroupMembershipEvent(MEMBER_REMOVED, member3));
    checkEvents(
        member3,
        new GroupMembershipEvent(REACHABILITY_CHANGED, member1),
        new GroupMembershipEvent(MEMBER_REMOVED, member1),
        new GroupMembershipEvent(REACHABILITY_CHANGED, member2),
        new GroupMembershipEvent(MEMBER_REMOVED, member2));

    // update member1 and wait for the property to be propagated
    member1.properties().put("newProperty", 1);
    checkEvents(member1, new GroupMembershipEvent(METADATA_CHANGED, member1));
    checkEvents(member2, new GroupMembershipEvent(METADATA_CHANGED, member1));

    // ensure member2 has already tried to propagate the new property, then reconnect it to member3
    // it shouldn't try to update it with member1, and member1 is disconnected from member3 so will
    // not send it probe requests - the only way for member3 to receive the new property is for it
    // to sync with member2
    Thread.sleep(gossipInterval.toMillis());
    heal(member2, member3);
    checkEvent(member2, MEMBER_ADDED, member3);
    checkEvents(member3, new GroupMembershipEvent(MEMBER_ADDED, member2));

    // then
    // wait until member3 has tried to sync
    Thread.sleep(syncInterval.toMillis());
    assertEquals(1, protocol3.getMember(member1.id()).properties().get("newProperty"));
  }

  private SwimMembershipProtocol startProtocol(final Member member) {
    return startProtocol(member, UnaryOperator.identity());
  }

  private SwimMembershipProtocol startProtocol(
      final Member member, final UnaryOperator<SwimMembershipProtocolConfig> configurator) {
    final SwimMembershipProtocol protocol =
        new SwimMembershipProtocol(
            configurator.apply(
                new SwimMembershipProtocolConfig().setFailureTimeout(Duration.ofSeconds(2))));
    final TestGroupMembershipEventListener listener = new TestGroupMembershipEventListener();
    listeners.put(member.id(), listener);
    protocol.addListener(listener);
    final BootstrapService bootstrap =
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(member.address()).start().join(),
            unicastServiceFactory.newUnicastService(member.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join());
    final NodeDiscoveryProvider provider = new BootstrapDiscoveryProvider(nodes);
    provider.join(bootstrap, member).join();
    final NodeDiscoveryService discovery =
        new DefaultNodeDiscoveryService(bootstrap, member, provider).start().join();
    protocol.join(bootstrap, discovery, member).join();
    protocols.put(member.id(), protocol);
    return protocol;
  }

  private void stopProtocol(final Member member) {
    final SwimMembershipProtocol protocol = protocols.remove(member.id());
    if (protocol != null) {
      protocol.leave(member).join();
    }
  }

  private void partition(final Member member) {
    unicastServiceFactory.partition(member.address());
    messagingServiceFactory.partition(member.address());
  }

  private void partition(final Member member1, final Member member2) {
    unicastServiceFactory.partition(member1.address(), member2.address());
    messagingServiceFactory.partition(member1.address(), member2.address());
  }

  private void heal(final Member member) {
    unicastServiceFactory.heal(member.address());
    messagingServiceFactory.heal(member.address());
  }

  private void heal(final Member member1, final Member member2) {
    unicastServiceFactory.heal(member1.address(), member2.address());
    messagingServiceFactory.heal(member1.address(), member2.address());
  }

  private void checkMembers(final Member member, final Member... members) {
    final SwimMembershipProtocol protocol = protocols.get(member.id());
    assertEquals(Sets.newHashSet(members), protocol.getMembers());
  }

  private void checkEvents(final Member member, final GroupMembershipEvent... types)
      throws InterruptedException {
    final Multiset<GroupMembershipEvent> events = HashMultiset.create(Arrays.asList(types));
    for (int i = 0; i < types.length; i++) {
      final GroupMembershipEvent event = nextEvent(member);
      if (!events.remove(event)) {
        throw new AssertionError("Unexpected event " + event);
      }
    }
  }

  private void checkEvent(final Member member, final GroupMembershipEvent.Type type)
      throws InterruptedException {
    checkEvent(member, type, null);
  }

  private void checkEvent(
      final Member member, final GroupMembershipEvent.Type type, final Member value)
      throws InterruptedException {
    final GroupMembershipEvent event = nextEvent(member);
    assertEquals(type, event.type());
    if (value != null) {
      assertEquals(value, event.member());
    }
  }

  private GroupMembershipEvent nextEvent(final Member member) throws InterruptedException {
    final TestGroupMembershipEventListener listener = listeners.get(member.id());
    return listener != null ? listener.nextEvent() : null;
  }

  private class TestGroupMembershipEventListener implements GroupMembershipEventListener {
    private BlockingQueue<GroupMembershipEvent> queue = new ArrayBlockingQueue<>(10);

    @Override
    public void event(final GroupMembershipEvent event) {
      queue.add(event);
    }

    GroupMembershipEvent nextEvent() throws InterruptedException {
      return queue.poll(10, TimeUnit.SECONDS);
    }
  }
}
