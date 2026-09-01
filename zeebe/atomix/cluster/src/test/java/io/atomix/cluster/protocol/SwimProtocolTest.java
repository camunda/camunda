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
import static org.assertj.core.api.Assertions.assertThat;

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
import io.atomix.cluster.messaging.impl.TestMessagingServiceFactory;
import io.atomix.cluster.messaging.impl.TestUnicastServiceFactory;
import io.atomix.cluster.protocol.SwimMembershipProtocol.ImmutableMember;
import io.atomix.cluster.protocol.SwimMembershipProtocol.SwimMember;
import io.atomix.utils.Version;
import io.atomix.utils.net.Address;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import net.jodah.concurrentunit.ConcurrentTestCase;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.AutoClose;

/** SWIM membership protocol test. */
public class SwimProtocolTest extends ConcurrentTestCase {

  private static final Duration GOSSIP_INTERVAL = Duration.ofMillis(25);
  private static final Duration PROBE_INTERVAL = Duration.ofMillis(100);
  private static final Duration PROBE_TIMEOUT = Duration.ofMillis(200);
  private static final Duration FAILURE_INTERVAL = Duration.ofMillis(1000);
  private static final Duration SYNC_INTERVAL = Duration.ofMillis(1000);
  private static final long PREVIOUS_INSTANCE_ID = 0xB0071D01L;
  private static final long CURRENT_INSTANCE_ID = 0xB0071D02L;
  private static final long UNKNOWN_INSTANCE_ID = SwimMembershipProtocol.UNKNOWN_INSTANCE_ID;
  private final Version version1 = Version.from("1.0.0");
  private final Version version2 = Version.from("2.0.0");
  private final Map<MemberId, SwimMembershipProtocol> protocols = Maps.newConcurrentMap();
  private TestMessagingServiceFactory messagingServiceFactory = new TestMessagingServiceFactory();
  private TestUnicastServiceFactory unicastServiceFactory = new TestUnicastServiceFactory();
  private SwimMember member1;
  private SwimMember member2;
  private SwimMember member3;
  private Collection<Member> members;
  private Collection<Node> nodes;
  private Map<MemberId, TestGroupMembershipEventListener> listeners = Maps.newConcurrentMap();
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private SwimMember member(
      final String id, final String host, final int port, final Version version) {
    return new SwimMember(
        MemberId.from(id),
        new Address(host, port),
        null,
        null,
        null,
        new Properties(),
        version,
        System.currentTimeMillis(),
        // the protocol takes the instance ID of its own run, not the seed member's
        SwimMembershipProtocol.UNKNOWN_INSTANCE_ID);
  }

  @Before
  @SuppressWarnings("unchecked")
  public void reset() {
    messagingServiceFactory = new TestMessagingServiceFactory();
    unicastServiceFactory = new TestUnicastServiceFactory();

    member1 = member("1", "localhost", 5001, version1);
    member2 = member("2", "localhost", 5002, version1);
    member3 = member("3", "localhost", 5003, version1);
    members = Arrays.asList(member1, member2, member3);
    nodes = (Collection) members;
    listeners = Maps.newConcurrentMap();
  }

  @After
  public void cleanup() {
    members.forEach(this::stopProtocol);
  }

  @Test
  public void shouldReceiveMemberAddedOnSingleNode() throws Exception {
    // given

    // when
    startProtocol(member1, member1.id().toString());

    // then
    checkEvent(member1, MEMBER_ADDED, member1);
    checkMembers(member1, member1);
  }

  /**
   * The properties of the local member are mutated in place by other components, e.g. by the broker
   * to publish partition roles and health, while listeners are notified asynchronously. A change
   * that is applied and reverted while a listener is being notified of the previous change must
   * still leave that listener with the member's current properties.
   */
  @Test
  public void shouldNotifyListenersOfPropertyChangedWithCorrectValue() throws Exception {
    // given - a listener that is blocked while being notified of a first property change; the
    // gossip interval is long enough that no further metadata check runs while the test changes the
    // properties below
    reset();
    final var protocol =
        startProtocol(
            member1,
            config -> config.setGossipInterval(Duration.ofSeconds(5)),
            member1.id().toString());
    checkEvent(member1, MEMBER_ADDED, member1);

    final var notifying = new CountDownLatch(1);
    final var resumeNotifying = new CountDownLatch(1);
    final List<String> notifiedValues = new CopyOnWriteArrayList<>();
    protocol.addListener(
        event -> {
          if (event.type() != METADATA_CHANGED) {
            return;
          }

          notifying.countDown();
          try {
            resumeNotifying.await(30, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
          notifiedValues.add(event.member().properties().getProperty("foo"));
        });

    // when - the property changes to a new value while the listener is notified of the first
    // change
    member1.properties().setProperty("foo", "published");
    assertThat(notifying.await(30, TimeUnit.SECONDS)).isTrue();
    member1.properties().setProperty("foo", "changed");
    resumeNotifying.countDown();
    Awaitility.await("until the listener was notified")
        .atMost(Duration.ofSeconds(30))
        .until(() -> !notifiedValues.isEmpty());

    // then - the listener ends up knowing the member's current property value
    Awaitility.await("until the listener knows the current property value")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(notifiedValues.getLast()).isEqualTo("published"));
  }

  @Test
  public void shouldReceiveMultipleEventsOnTwoNodeCluster() throws Exception {
    // given
    startProtocol(member1, member1.id().toString());

    // when
    startProtocol(member2, member2.id().toString());

    // then
    checkEvent(member2, MEMBER_ADDED, member2);
    checkEvent(member2, MEMBER_ADDED, member1);
    checkMembers(member2, member1, member2);

    checkEvent(member1, MEMBER_ADDED, member1);
    checkEvent(member1, MEMBER_ADDED, member2);
    checkMembers(member1, member1, member2);
  }

  @Test
  public void shouldReceiveMultipleEventsOnThreeNodeCluster() throws Exception {
    // given
    startProtocol(member1, member1.id().toString());
    startProtocol(member2, member2.id().toString());

    // when
    startProtocol(member3, member3.id().toString());

    // then
    checkEvent(member2, MEMBER_ADDED, member2);
    checkEvent(member2, MEMBER_ADDED);
    checkEvent(member2, MEMBER_ADDED);
    checkMembers(member2, member1, member2, member3);

    checkEvent(member1, MEMBER_ADDED, member1);
    checkEvent(member1, MEMBER_ADDED);
    checkEvent(member1, MEMBER_ADDED);
    checkMembers(member1, member1, member2, member3);

    checkEvent(member3, MEMBER_ADDED, member3);
    checkEvent(member3, MEMBER_ADDED);
    checkEvent(member3, MEMBER_ADDED);
    checkMembers(member3, member1, member2, member3);
  }

  @Test
  public void shouldRemoveNodeOnPartition() throws Exception {
    // Start a node and check its events.
    startProtocol(member1, member1.id().toString());
    startProtocol(member2, member2.id().toString());
    startProtocol(member3, member3.id().toString());

    awaitMembers(member3, member1, member2, member3);
    awaitMembers(member2, member1, member2, member3);
    awaitMembers(member1, member1, member2, member3);

    clearEvents(member1, member2, member3);

    // when Isolate node 3 from the rest of the cluster.
    partition(member3);

    // then
    // Nodes 1 and 2 should see REACHABILITY_CHANGED events and then MEMBER_REMOVED events.
    checkEvent(member1, REACHABILITY_CHANGED, member3);
    checkEvent(member2, REACHABILITY_CHANGED, member3);
    checkEvent(member1, MEMBER_REMOVED, member3);
    checkEvent(member2, MEMBER_REMOVED, member3);
  }

  @Test
  public void testSwimProtocol() throws Exception {
    // Start a node and check its events.
    startProtocol(member1, member1.id().toString());
    startProtocol(member2, member2.id().toString());
    startProtocol(member3, member3.id().toString());

    awaitMembers(member3, member1, member2, member3);
    awaitMembers(member2, member1, member2, member3);
    awaitMembers(member1, member1, member2, member3);

    clearEvents(member1, member2, member3);

    // Isolate node 3 from the rest of the cluster.
    partition(member3);

    // Verify that node 3 was removed from nodes 1 and 2.
    awaitMembers(member2, member1, member2);
    awaitMembers(member1, member1, member2);
    clearEvents(member1, member2);

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

    // Heal the partition.
    heal(member1, member2);

    // Update node 1's metadata.
    member1.properties().put("foo", "bar");

    // Verify the metadata change is propagated throughout the cluster.
    checkEvent(member1, METADATA_CHANGED, member1);
    checkEvent(member2, METADATA_CHANGED, member1);
    checkEvent(member3, METADATA_CHANGED, member1);
  }

  @Test
  public void shouldRemoveOldMemberVersions() throws InterruptedException {
    // given
    startProtocol(member1, member1.id().toString());
    startProtocol(member2, member2.id().toString());

    awaitMembers(member2, member1, member2);
    awaitMembers(member1, member1, member2);

    clearEvents(member1, member2);

    // when - starting a member with new version
    stopProtocol(member2);
    final var member =
        member(member2.id().id(), member2.address().host(), member2.address().port(), version2);
    startProtocol(member, "member2-" + member2.id().id());

    // then - verify that version 1 is removed and version 2 is added.
    Awaitility.await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> checkEvent(member1, MEMBER_REMOVED, member2));
    checkEvent(member1, MEMBER_ADDED, member);
  }

  @Test
  public void shouldRemovePreviousRunOfRestartedMember() throws InterruptedException {
    // given
    startProtocol(member1, member1.id().toString());
    startProtocol(member2, member2.id().toString());

    awaitMembers(member2, member1, member2);
    awaitMembers(member1, member1, member2);

    clearEvents(member1, member2);

    // when - member 2 restarts with every property unchanged, and fast enough that member 1 never
    // reports it as failed. startProtocol stops the previous run without gossiping anything, which
    // is what a non-graceful kill looks like to the rest of the cluster.
    startProtocol(member2, "member2-restarted");

    // then - member 1 sees the previous run leave and the new one join, so that anything it holds
    // per member is rebuilt for the new run
    Awaitility.await("Previous run of member 2 removed")
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> checkEvent(member1, MEMBER_REMOVED, member2));
    checkEvent(member1, MEMBER_ADDED, member2);
  }

  @Test
  public void shouldKeepRestartedMemberWhichReportsNoInstanceId() throws InterruptedException {
    // given - a member on a version predating the instance ID

    startProtocol(member1, member1.id().toString());
    startProtocolWithoutInstanceId(member2, member2.id().toString());

    awaitMembers(member2, member1, member2);
    awaitMembers(member1, member1, member2);

    clearEvents(member1, member2);

    // when - it restarts, still reporting no instance ID
    startProtocolWithoutInstanceId(member2, "member2-restarted");

    // then - member 1 cannot tell the two runs apart, so it keeps the member it has rather than
    // churning through a removal for every update it receives
    checkNoEvent(member1, Duration.ofSeconds(2));
  }

  @Test
  public void shouldKeepMemberWhenAnUpdateStopsReportingItsInstanceId()
      throws InterruptedException {
    // given - a member which reports a instance ID

    startProtocol(member1, member1.id().toString());
    startProtocol(member2, member2.id().toString());

    awaitMembers(member2, member1, member2);
    awaitMembers(member1, member1, member2);

    clearEvents(member1, member2);

    // when - the same member is next heard of without one, as happens while a rolling update is in
    // progress and its updates are relayed through a member that drops the field
    startProtocolWithoutInstanceId(member2, "member2-without-boot-id");

    // then - the missing instance ID is read as no information rather than as a different run
    checkNoEvent(member1, Duration.ofSeconds(2));
  }

  /**
   * A member which relays an update it received re-encodes it, and one running a version predating
   * the instance ID drops the field while doing so. The stripped update and the authoritative one
   * describe the same run of the member, so they carry the same incarnation number: the restart has
   * to be recognised then, not only once a later update raises that number, or the streams held for
   * the previous run outlive it for as long as the member's incarnation number does not change.
   */
  @Test
  public void shouldDetectRestartRevealedAfterRelayedUpdateStrippedTheInstanceId()
      throws InterruptedException {
    // given - a member known to be running the instance it last reported
    final var protocol = startIsolatedProtocol(member1);
    final var incarnationNumber = System.currentTimeMillis();
    protocol.updateState(aliveUpdate(member2, incarnationNumber - 1, PREVIOUS_INSTANCE_ID));
    // events are posted asynchronously, so wait for the ones the setup produces instead of
    // clearing the queue underneath them
    checkEvent(member1, MEMBER_ADDED, member1);
    checkEvent(member1, MEMBER_ADDED, member2);

    // when - it restarts, and the news reaches us first through a member which drops the instance
    // ID, and only then from the member itself
    protocol.updateState(aliveUpdate(member2, incarnationNumber, UNKNOWN_INSTANCE_ID));
    checkNoEvent(member1, Duration.ofMillis(500));
    protocol.updateState(aliveUpdate(member2, incarnationNumber, CURRENT_INSTANCE_ID));

    // then - the previous run is removed and the new one added, without waiting for the member's
    // incarnation number to change again
    checkEvent(member1, MEMBER_REMOVED, member2);
    checkEvent(member1, MEMBER_ADDED, member2);
    assertThat(trackedInstanceId(protocol, member2)).isEqualTo(CURRENT_INSTANCE_ID);
  }

  @Test
  public void shouldDetectRestartWhenTheStrippedUpdateArrivesLast() throws InterruptedException {
    // given - a member known to be running the instance it last reported
    final var protocol = startIsolatedProtocol(member1);
    final var incarnationNumber = System.currentTimeMillis();
    protocol.updateState(aliveUpdate(member2, incarnationNumber - 1, PREVIOUS_INSTANCE_ID));
    // events are posted asynchronously, so wait for the ones the setup produces instead of
    // clearing the queue underneath them
    checkEvent(member1, MEMBER_ADDED, member1);
    checkEvent(member1, MEMBER_ADDED, member2);

    // when - the member itself reports the restart before the relayed update reaches us
    protocol.updateState(aliveUpdate(member2, incarnationNumber, CURRENT_INSTANCE_ID));
    checkEvent(member1, MEMBER_REMOVED, member2);
    checkEvent(member1, MEMBER_ADDED, member2);
    protocol.updateState(aliveUpdate(member2, incarnationNumber, UNKNOWN_INSTANCE_ID));

    // then - the update which lost the instance ID does not undo what we already know
    checkNoEvent(member1, Duration.ofMillis(500));
    assertThat(trackedInstanceId(protocol, member2)).isEqualTo(CURRENT_INSTANCE_ID);
  }

  /**
   * Passing on the previous run's instance ID alongside an incarnation number we never saw it with
   * would make every member which receives that gossip attribute that number to the previous run,
   * and none of them would recognise the restart afterwards.
   */
  @Test
  public void shouldNotGossipPreviousRunsInstanceIdAfterRelayedUpdateDroppedIt()
      throws InterruptedException {
    // given - a member known to be running the instance it last reported
    final var protocol = startIsolatedProtocol(member1);
    final var incarnationNumber = System.currentTimeMillis();
    protocol.updateState(aliveUpdate(member2, incarnationNumber - 1, PREVIOUS_INSTANCE_ID));
    // events are posted asynchronously, so wait for the ones the setup produces instead of
    // clearing the queue underneath them
    checkEvent(member1, MEMBER_ADDED, member1);
    checkEvent(member1, MEMBER_ADDED, member2);

    // when - the next update we receive about it lost the instance ID in relaying
    protocol.updateState(aliveUpdate(member2, incarnationNumber, UNKNOWN_INSTANCE_ID));

    // then - we report the member as one whose instance ID we do not know
    assertThat(trackedInstanceId(protocol, member2)).isEqualTo(UNKNOWN_INSTANCE_ID);
  }

  @Test
  public void shouldKeepMemberWhenTheStrippedUpdateIsFollowedByTheSameInstanceId()
      throws InterruptedException {
    // given - a member known to be running the instance it last reported
    final var protocol = startIsolatedProtocol(member1);
    final var incarnationNumber = System.currentTimeMillis();
    protocol.updateState(aliveUpdate(member2, incarnationNumber - 1, PREVIOUS_INSTANCE_ID));
    // events are posted asynchronously, so wait for the ones the setup produces instead of
    // clearing the queue underneath them
    checkEvent(member1, MEMBER_ADDED, member1);
    checkEvent(member1, MEMBER_ADDED, member2);

    // when - the member did not restart, and its instance ID was only lost in relaying
    protocol.updateState(aliveUpdate(member2, incarnationNumber, UNKNOWN_INSTANCE_ID));
    protocol.updateState(aliveUpdate(member2, incarnationNumber, PREVIOUS_INSTANCE_ID));

    // then - the member is kept, and the update confirms it is still the run we already knew
    checkNoEvent(member1, Duration.ofMillis(500));
    assertThat(trackedInstanceId(protocol, member2)).isEqualTo(PREVIOUS_INSTANCE_ID);
  }

  @Test
  public void shouldStillReportUnreachableWhenTheSameUpdateConfirmsTheInstanceId()
      throws InterruptedException {
    // given - a member whose most recent update lost its instance ID in relaying
    final var protocol = startIsolatedProtocol(member1);
    final var incarnationNumber = System.currentTimeMillis();
    protocol.updateState(aliveUpdate(member2, incarnationNumber - 1, PREVIOUS_INSTANCE_ID));
    // events are posted asynchronously, so wait for the ones the setup produces instead of
    // clearing the queue underneath them
    checkEvent(member1, MEMBER_ADDED, member1);
    checkEvent(member1, MEMBER_ADDED, member2);
    protocol.updateState(aliveUpdate(member2, incarnationNumber, UNKNOWN_INSTANCE_ID));

    // when - a single update carries both the missing instance ID and a progressed state
    protocol.updateState(
        update(
            member2,
            incarnationNumber,
            PREVIOUS_INSTANCE_ID,
            SwimMembershipProtocol.State.SUSPECT));

    // then - confirming the instance ID does not swallow the state the update reports
    checkEvent(member1, REACHABILITY_CHANGED, member2);
    assertThat(trackedInstanceId(protocol, member2)).isEqualTo(PREVIOUS_INSTANCE_ID);
  }

  @Test
  public void shouldSynchronizePeriodically() throws InterruptedException {
    // given
    startProtocol(member1, member1.id().toString());
    startProtocol(member2, member2.id().toString());
    final SwimMembershipProtocol protocol3 = startProtocol(member3, member3.id().toString());

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
    Thread.sleep(GOSSIP_INTERVAL.toMillis());
    heal(member2, member3);
    checkEvent(member2, MEMBER_ADDED, member3);
    checkEvents(member3, new GroupMembershipEvent(MEMBER_ADDED, member2));

    // then
    // wait until member3 has tried to sync
    Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> hasNewProperty(protocol3));
  }

  private boolean hasNewProperty(final SwimMembershipProtocol protocol3) {
    final var memberOne = protocol3.getMember(member1.id());

    if (memberOne != null) {
      final var newProperty = memberOne.properties().get("newProperty");
      return newProperty != null && Integer.parseInt(newProperty.toString()) == 1;
    }
    return false;
  }

  private SwimMembershipProtocol startProtocol(
      final SwimMember member, final String actorSchedulerName) {
    return startProtocol(member, UnaryOperator.identity(), actorSchedulerName);
  }

  /**
   * Starts a member with every periodic activity pushed out of the way, so that a test can drive
   * its view of the cluster through {@link SwimMembershipProtocol#updateState} alone.
   */
  private SwimMembershipProtocol startIsolatedProtocol(final SwimMember member) {
    return startProtocol(
        member,
        config ->
            config
                .setProbeInterval(Duration.ofSeconds(30))
                .setGossipInterval(Duration.ofSeconds(30))
                .setSyncInterval(Duration.ofSeconds(30))
                .setFailureTimeout(Duration.ofSeconds(30)),
        member.id().toString());
  }

  private ImmutableMember aliveUpdate(
      final SwimMember member, final long incarnationNumber, final long instanceId) {
    return update(member, incarnationNumber, instanceId, SwimMembershipProtocol.State.ALIVE);
  }

  private ImmutableMember update(
      final SwimMember member,
      final long incarnationNumber,
      final long instanceId,
      final SwimMembershipProtocol.State state) {
    return new ImmutableMember(
        member.id(),
        member.address(),
        member.zone(),
        member.rack(),
        member.host(),
        member.properties(),
        member.version(),
        member.timestamp(),
        state,
        incarnationNumber,
        instanceId);
  }

  /** The instance ID the member would report to the rest of the cluster for its current run. */
  private long trackedInstanceId(final SwimMembershipProtocol protocol, final Member member) {
    return ((SwimMember) protocol.getMember(member.id())).instanceId();
  }

  /**
   * Starts a member which reports no instance ID at all, i.e. one running a version predating the
   * field.
   */
  private SwimMembershipProtocol startProtocolWithoutInstanceId(
      final SwimMember member, final String actorSchedulerName) {
    return startProtocol(
        member,
        UnaryOperator.identity(),
        actorSchedulerName,
        SwimMembershipProtocol.UNKNOWN_INSTANCE_ID);
  }

  private SwimMembershipProtocol startProtocol(
      final SwimMember member,
      final UnaryOperator<SwimMembershipProtocolConfig> configurator,
      final String actorSchedulerName) {
    return startProtocol(member, configurator, actorSchedulerName, null);
  }

  private SwimMembershipProtocol startProtocol(
      final SwimMember member,
      final UnaryOperator<SwimMembershipProtocolConfig> configurator,
      final String actorSchedulerName,
      final Long instanceId) {
    final SwimMembershipProtocol protocol =
        startSwimMembershipProtocol(member, configurator, actorSchedulerName, instanceId);
    final var previous = protocols.put(member.id(), protocol);
    // stops previous one
    if (previous != null) {
      previous.leave(member);
    }
    return protocol;
  }

  // starts new version of the protocol for the same member id without stopping the previous one
  private SwimMembershipProtocol startSwimMembershipProtocol(
      final SwimMember member,
      final UnaryOperator<SwimMembershipProtocolConfig> configurator,
      final String actorSchedulerName,
      final Long instanceId) {
    final var config =
        configurator.apply(
            new SwimMembershipProtocolConfig()
                .setGossipInterval(GOSSIP_INTERVAL)
                .setProbeInterval(PROBE_INTERVAL)
                .setProbeTimeout(PROBE_TIMEOUT)
                .setFailureTimeout(FAILURE_INTERVAL)
                .setSyncInterval(SYNC_INTERVAL));
    final SwimMembershipProtocol protocol =
        new SwimMembershipProtocol(config, actorSchedulerName, meterRegistry, instanceId);
    final TestGroupMembershipEventListener listener = new TestGroupMembershipEventListener();
    listeners.put(member.id(), listener);
    protocol.addListener(listener);
    final BootstrapService bootstrap =
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(member.address()).start().join(),
            unicastServiceFactory.newUnicastService(member.address()).start().join());
    final NodeDiscoveryProvider provider = new BootstrapDiscoveryProvider(nodes);
    provider.join(bootstrap, member).join();
    final NodeDiscoveryService discovery =
        new DefaultNodeDiscoveryService(bootstrap, member, provider).start().join();
    protocol.join(bootstrap, discovery, member).join();
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
    assertThat(protocol.getMembers()).isEqualTo(Sets.newHashSet(members));
  }

  private void awaitMembers(final Member member, final Member... members) {
    final SwimMembershipProtocol protocol = protocols.get(member.id());
    final var expectedMembers = Sets.newHashSet(members);

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> expectedMembers.equals(protocol.getMembers()));
  }

  private void clearEvents(final Member... members) {
    for (final Member member : members) {
      listeners.get(member.id()).clear();
    }
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
    assertThat(event).isNotNull();
    assertThat(event.type()).isEqualTo(type);
    if (value != null) {
      assertThat(event.member()).isEqualTo(value);
    }
  }

  private void checkNoEvent(final Member member, final Duration within)
      throws InterruptedException {
    assertThat(listeners.get(member.id()).nextEvent(within))
        .describedAs("Member %s observed no membership event", member.id())
        .isNull();
  }

  private GroupMembershipEvent nextEvent(final Member member) throws InterruptedException {
    final TestGroupMembershipEventListener listener = listeners.get(member.id());
    return listener != null ? listener.nextEvent() : null;
  }

  private static final class TestGroupMembershipEventListener
      implements GroupMembershipEventListener {

    private final BlockingDeque<GroupMembershipEvent> queue = new LinkedBlockingDeque<>(100);

    @Override
    public void event(final GroupMembershipEvent event) {
      queue.add(event);
    }

    GroupMembershipEvent nextEvent() throws InterruptedException {
      return nextEvent(Duration.ofSeconds(10));
    }

    GroupMembershipEvent nextEvent(final Duration timeout) throws InterruptedException {
      return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void clear() {
      queue.clear();
    }
  }
}
