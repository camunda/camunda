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

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.atomix.utils.concurrent.Threads.namedThreads;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.NodeDiscoveryEvent;
import io.atomix.cluster.discovery.NodeDiscoveryEventListener;
import io.atomix.cluster.discovery.NodeDiscoveryService;
import io.atomix.cluster.impl.AddressSerializer;
import io.atomix.utils.Version;
import io.atomix.utils.event.AbstractListenerManager;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SWIM group membership protocol implementation. */
public class SwimMembershipProtocol
    extends AbstractListenerManager<GroupMembershipEvent, GroupMembershipEventListener>
    implements GroupMembershipProtocol {

  public static final Type TYPE = new Type();
  private static final Logger LOGGER = LoggerFactory.getLogger(SwimMembershipProtocol.class);
  private static final String MEMBERSHIP_SYNC = "atomix-membership-sync";
  private static final String MEMBERSHIP_GOSSIP = "atomix-membership-gossip";
  private static final String MEMBERSHIP_PROBE = "atomix-membership-probe";
  private static final String MEMBERSHIP_PROBE_REQUEST = "atomix-membership-probe-request";
  private static final Serializer SERIALIZER =
      Serializer.using(
          Namespace.builder()
              .register(Namespaces.BASIC)
              .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
              .register(MemberId.class)
              .register(new AddressSerializer(), Address.class)
              .register(ImmutableMember.class)
              .register(State.class)
              .register(ImmutablePair.class)
              .build("ClusterMembershipService"));
  private final SwimMembershipProtocolConfig config;
  private NodeDiscoveryService discoveryService;
  private BootstrapService bootstrapService;
  private final AtomicBoolean started = new AtomicBoolean();
  private SwimMember localMember;
  private volatile Properties localProperties = new Properties();
  private final Map<MemberId, SwimMember> members = Maps.newConcurrentMap();
  private final List<SwimMember> randomMembers = Lists.newCopyOnWriteArrayList();
  private final Map<MemberId, ImmutableMember> updates = new LinkedHashMap<>();
  private final List<SwimMember> syncMembers = new ArrayList<>();
  private final ScheduledExecutorService swimScheduler =
      Executors.newSingleThreadScheduledExecutor(
          namedThreads("atomix-cluster-heartbeat-sender", LOGGER));
  private final BiFunction<Address, byte[], CompletableFuture<byte[]>> probeRequestHandler =
      (address, payload) ->
          handleProbeRequest(SERIALIZER.decode(payload)).thenApply(SERIALIZER::encode);
  private final NodeDiscoveryEventListener discoveryEventListener = this::handleDiscoveryEvent;
  private final ExecutorService eventExecutor =
      Executors.newSingleThreadExecutor(namedThreads("atomix-cluster-events", LOGGER));
  private final BiFunction<Address, byte[], byte[]> syncHandler =
      (address, payload) -> SERIALIZER.encode(handleSync(SERIALIZER.decode(payload)));
  private final BiFunction<Address, byte[], byte[]> probeHandler =
      (address, payload) -> SERIALIZER.encode(handleProbe(SERIALIZER.decode(payload)));
  private final BiConsumer<Address, byte[]> gossipListener =
      (address, payload) -> handleGossipUpdates(SERIALIZER.decode(payload));
  private ScheduledFuture<?> gossipFuture;
  private ScheduledFuture<?> probeFuture;
  private ScheduledFuture<?> syncFuture;
  private final AtomicInteger probeCounter = new AtomicInteger();

  SwimMembershipProtocol(final SwimMembershipProtocolConfig config) {
    this.config = config;
  }

  /**
   * Creates a new bootstrap provider builder.
   *
   * @return a new bootstrap provider builder
   */
  public static SwimMembershipProtocolBuilder builder() {
    return new SwimMembershipProtocolBuilder();
  }

  @Override
  public SwimMembershipProtocolConfig config() {
    return config;
  }

  @Override
  public Set<Member> getMembers() {
    return ImmutableSet.copyOf(members.values());
  }

  @Override
  public Member getMember(final MemberId memberId) {
    return members.get(memberId);
  }

  @Override
  public CompletableFuture<Void> join(
      final BootstrapService bootstrap, final NodeDiscoveryService discovery, final Member member) {
    if (started.compareAndSet(false, true)) {
      this.bootstrapService = bootstrap;
      this.discoveryService = discovery;
      this.localMember =
          new SwimMember(
              member.id(),
              member.address(),
              member.zone(),
              member.rack(),
              member.host(),
              member.properties(),
              member.version(),
              System.currentTimeMillis());
      this.localProperties.putAll(localMember.properties());
      discoveryService.addListener(discoveryEventListener);

      LOGGER.info("{} - Member activated: {}", localMember.id(), localMember);
      localMember.setState(State.ALIVE);
      members.put(localMember.id(), localMember);
      post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_ADDED, localMember));

      registerHandlers();
      gossipFuture =
          swimScheduler.scheduleAtFixedRate(
              this::gossip, 0, config.getGossipInterval().toMillis(), TimeUnit.MILLISECONDS);
      probeFuture =
          swimScheduler.scheduleAtFixedRate(
              this::probe, 0, config.getProbeInterval().toMillis(), TimeUnit.MILLISECONDS);
      swimScheduler.execute(this::syncAll);
      syncFuture =
          swimScheduler.scheduleAtFixedRate(
              this::sync, 0, config.getSyncInterval().toMillis(), TimeUnit.MILLISECONDS);
      LOGGER.info("Started");
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> leave(final Member member) {
    if (started.compareAndSet(true, false)) {
      discoveryService.removeListener(discoveryEventListener);
      gossipFuture.cancel(false);
      probeFuture.cancel(false);
      syncFuture.cancel(false);
      swimScheduler.shutdownNow();
      eventExecutor.shutdownNow();
      LOGGER.info("{} - Member deactivated: {}", localMember.id(), localMember);
      localMember.setState(State.DEAD);
      members.clear();
      unregisterHandlers();
      LOGGER.info("Stopped");
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  protected void post(final GroupMembershipEvent event) {
    eventExecutor.execute(() -> super.post(event));
  }

  /** Checks the local member metadata for changes. */
  private void checkMetadata() {
    if (!localMember.properties().equals(localProperties)) {
      localProperties = new Properties();
      localProperties.putAll(localMember.properties());
      LOGGER.debug("{} - Detected local properties change {}", localMember.id(), localProperties);
      localMember.setIncarnationNumber(localMember.getIncarnationNumber() + 1);
      post(new GroupMembershipEvent(GroupMembershipEvent.Type.METADATA_CHANGED, localMember));
      recordUpdate(localMember.copy());
    }
  }

  /**
   * Updates the state for the given member.
   *
   * @param member the member for which to update the state
   * @return whether the state for the member was updated
   */
  private boolean updateState(final ImmutableMember member) {
    // If the member matches the local member, ignore the update.
    if (member.id().equals(localMember.id())) {
      return false;
    }

    SwimMember swimMember = members.get(member.id());

    // If the local member is not present, add the member in the ALIVE state.
    if (swimMember == null) {
      if (member.state() == State.ALIVE) {
        swimMember = new SwimMember(member);
        members.put(swimMember.id(), swimMember);
        randomMembers.add(swimMember);
        Collections.shuffle(randomMembers);
        LOGGER.debug("{} - Member added {}", this.localMember.id(), swimMember);
        swimMember.setState(State.ALIVE);
        post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_ADDED, swimMember.copy()));
        recordUpdate(swimMember.copy());
        return true;
      }
      return false;
    }
    // If the term has been increased, update the member and record a gossip event.
    else if (member.incarnationNumber() > swimMember.getIncarnationNumber()) {
      // If the member's version has changed, remove the old member and add the new member.
      if (!Objects.equals(member.version(), swimMember.version())) {
        members.remove(member.id());
        randomMembers.remove(swimMember);
        post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_REMOVED, swimMember.copy()));
        swimMember = new SwimMember(member);
        swimMember.setState(State.ALIVE);
        members.put(member.id(), swimMember);
        randomMembers.add(swimMember);
        Collections.shuffle(randomMembers);
        LOGGER.debug("{} - Evicted member for new version {}", this.localMember.id(), swimMember);
        post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_ADDED, swimMember.copy()));
        recordUpdate(swimMember.copy());
      } else {
        // Update the term for the local member.
        swimMember.setIncarnationNumber(member.incarnationNumber());

        // If the state has been changed to ALIVE, trigger a REACHABILITY_CHANGED event and then
        // update metadata.
        if (member.state() == State.ALIVE && swimMember.getState() != State.ALIVE) {
          triggerReachabilityChangedEventOnAlive(member, swimMember);
        }
        // If the state has been changed to SUSPECT, update metadata and then trigger a
        // REACHABILITY_CHANGED event.
        else if (member.state() == State.SUSPECT && swimMember.getState() != State.SUSPECT) {
          triggerReachibilityEventOnSuspect(member, swimMember);
        }
        // If the state has been changed to DEAD, trigger a REACHABILITY_CHANGED event if necessary
        // and then remove
        // the member from the members list and trigger a MEMBER_REMOVED event.
        else if (member.state() == State.DEAD && swimMember.getState() != State.DEAD) {
          triggerReachabilityEventOnDeath(swimMember);
        } else if (!Objects.equals(member.properties(), swimMember.properties())) {
          swimMember.properties().putAll(member.properties());
          LOGGER.debug("{} - Member metadata changed {}", this.localMember.id(), swimMember);
          post(
              new GroupMembershipEvent(
                  GroupMembershipEvent.Type.METADATA_CHANGED, swimMember.copy()));
        }

        // Always enqueue an update for gossip when the term changes.
        recordUpdate(swimMember.copy());
        return true;
      }
    }
    // If the term remained the same but the state has progressed, update the state and trigger
    // events.
    else if (member.incarnationNumber() == swimMember.getIncarnationNumber()
        && member.state().ordinal() > swimMember.getState().ordinal()) {
      swimMember.setState(member.state());

      // If the updated state is SUSPECT, post a REACHABILITY_CHANGED event and record an update.
      if (member.state() == State.SUSPECT) {
        LOGGER.debug("{} - Member unreachable {}", this.localMember.id(), swimMember);
        post(
            new GroupMembershipEvent(
                GroupMembershipEvent.Type.REACHABILITY_CHANGED, swimMember.copy()));
        if (config.isNotifySuspect()) {
          gossip(swimMember, Lists.newArrayList(swimMember.copy()));
        }
      }
      // If the updated state is DEAD, post a REACHABILITY_CHANGED event if necessary, then post a
      // MEMBER_REMOVED
      // event and record an update.
      else if (member.state() == State.DEAD) {
        members.remove(swimMember.id());
        randomMembers.remove(swimMember);
        Collections.shuffle(randomMembers);
        LOGGER.debug("{} - Member removed {}", this.localMember.id(), swimMember);
        post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_REMOVED, swimMember.copy()));
      }
      recordUpdate(swimMember.copy());
      return true;
    }
    return false;
  }

  private void triggerReachabilityEventOnDeath(final SwimMember swimMember) {
    if (swimMember.getState() == State.ALIVE) {
      swimMember.setState(State.SUSPECT);
      LOGGER.debug("{} - Member unreachable {}", this.localMember.id(), swimMember);
      post(
          new GroupMembershipEvent(
              GroupMembershipEvent.Type.REACHABILITY_CHANGED, swimMember.copy()));
    }
    swimMember.setState(State.DEAD);
    members.remove(swimMember.id());
    randomMembers.remove(swimMember);
    Collections.shuffle(randomMembers);
    LOGGER.debug("{} - Member removed {}", this.localMember.id(), swimMember);
    post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_REMOVED, swimMember.copy()));
  }

  private void triggerReachibilityEventOnSuspect(
      final ImmutableMember member, final SwimMember swimMember) {
    if (!Objects.equals(member.properties(), swimMember.properties())) {
      swimMember.properties().putAll(member.properties());
      LOGGER.debug("{} - Member metadata changed {}", this.localMember.id(), swimMember);
      post(new GroupMembershipEvent(GroupMembershipEvent.Type.METADATA_CHANGED, swimMember.copy()));
    }
    swimMember.setState(State.SUSPECT);
    LOGGER.debug("{} - Member unreachable {}", this.localMember.id(), swimMember);
    post(
        new GroupMembershipEvent(
            GroupMembershipEvent.Type.REACHABILITY_CHANGED, swimMember.copy()));
    if (config.isNotifySuspect()) {
      gossip(swimMember, Lists.newArrayList(swimMember.copy()));
    }
  }

  private void triggerReachabilityChangedEventOnAlive(
      final ImmutableMember member, final SwimMember swimMember) {
    swimMember.setState(State.ALIVE);
    LOGGER.debug("{} - Member reachable {}", this.localMember.id(), swimMember);
    post(
        new GroupMembershipEvent(
            GroupMembershipEvent.Type.REACHABILITY_CHANGED, swimMember.copy()));
    if (!Objects.equals(member.properties(), swimMember.properties())) {
      swimMember.properties().putAll(member.properties());
      LOGGER.debug("{} - Member metadata changed {}", this.localMember.id(), swimMember);
      post(new GroupMembershipEvent(GroupMembershipEvent.Type.METADATA_CHANGED, swimMember.copy()));
    }
  }

  /**
   * Records an update as an immutable member.
   *
   * @param member the updated member
   */
  private void recordUpdate(final ImmutableMember member) {
    updates.put(member.id(), member);
  }

  /** Checks suspect nodes for failures. */
  private void checkFailures() {
    for (final SwimMember member : members.values()) {
      if (member.getState() == State.SUSPECT
          && System.currentTimeMillis() - member.getUpdated()
              > config.getFailureTimeout().toMillis()) {
        member.setState(State.DEAD);
        members.remove(member.id());
        randomMembers.remove(member);
        Collections.shuffle(randomMembers);
        LOGGER.debug("{} - Member removed {}", this.localMember.id(), member);
        post(new GroupMembershipEvent(GroupMembershipEvent.Type.MEMBER_REMOVED, member.copy()));
        recordUpdate(member.copy());
      }
    }
  }

  /** Synchronizes the node state with peers. */
  private void syncAll() {
    final List<SwimMember> syncMembers =
        discoveryService.getNodes().stream()
            .map(node -> new SwimMember(MemberId.from(node.id().id()), node.address()))
            .filter(member -> !member.id().equals(localMember.id()))
            .collect(Collectors.toList());
    for (final SwimMember member : syncMembers) {
      sync(member.copy());
    }
  }

  /**
   * Synchronizes the node state with the given peer.
   *
   * @param member the peer with which to synchronize the node state
   */
  private void sync(final ImmutableMember member) {
    LOGGER.debug("{} - Synchronizing membership with {}", localMember.id(), member);
    bootstrapService
        .getMessagingService()
        .sendAndReceive(
            member.address(),
            MEMBERSHIP_SYNC,
            SERIALIZER.encode(localMember.copy()),
            false,
            config.getProbeTimeout())
        .whenCompleteAsync(
            (response, error) -> {
              if (error == null) {
                final Collection<ImmutableMember> members = SERIALIZER.decode(response);
                LOGGER.debug(
                    "{} - Synchronized membership with {}, received: {}",
                    localMember.id(),
                    member,
                    members);
                members.forEach(this::updateState);
              } else {
                LOGGER.debug(
                    "{} - Failed to synchronize membership with {}", localMember.id(), member);
              }
            },
            swimScheduler);
  }

  private void sync() {
    if (syncMembers.isEmpty()) {
      syncMembers.addAll(members.values());
      syncMembers.remove(localMember);
      Collections.shuffle(syncMembers);
    }

    if (!syncMembers.isEmpty()) {
      final SwimMember member = syncMembers.remove(0);
      if (member != null) {
        sync(member.copy());
      }
    }
  }

  /**
   * Handles a synchronize request from a peer.
   *
   * @param member the peer from which to handle the request
   */
  private Collection<ImmutableMember> handleSync(final ImmutableMember member) {
    updateState(member);
    return new ArrayList<>(
        members.values().stream().map(SwimMember::copy).collect(Collectors.toList()));
  }

  /** Sends probes to all members or to the next member in round robin fashion. */
  private void probe() {
    // First get a sorted list of discovery service nodes that are not present in the SWIM members.
    // This is necessary to ensure we attempt to probe all nodes that are provided by the discovery
    // provider.
    final List<SwimMember> probeMembers =
        Lists.newArrayList(
            discoveryService.getNodes().stream()
                .map(node -> new SwimMember(MemberId.from(node.id().id()), node.address()))
                .filter(member -> !members.containsKey(member.id()))
                .filter(member -> !member.id().equals(localMember.id()))
                .sorted(Comparator.comparing(Member::id))
                .collect(Collectors.toList()));

    // Then add the randomly sorted list of SWIM members.
    probeMembers.addAll(randomMembers);

    // If there are members to probe, select the next member to probe using a counter for round
    // robin probes.
    if (!probeMembers.isEmpty()) {
      final SwimMember probeMember =
          probeMembers.get(Math.abs(probeCounter.incrementAndGet() % probeMembers.size()));
      probe(probeMember.copy());
    }
  }

  /**
   * Probes the given member.
   *
   * @param member the member to probe
   */
  private void probe(final ImmutableMember member) {
    LOGGER.trace("{} - Probing {}", localMember.id(), member);
    bootstrapService
        .getMessagingService()
        .sendAndReceive(
            member.address(),
            MEMBERSHIP_PROBE,
            SERIALIZER.encode(Pair.of(localMember.copy(), member)),
            false,
            config.getProbeTimeout())
        .whenCompleteAsync(
            (response, error) -> {
              if (error == null) {
                updateState(SERIALIZER.decode(response));
              } else {
                LOGGER.debug("{} - Failed to probe {}", this.localMember.id(), member, error);
                // Verify that the local member term has not changed and request probes from peers.
                final SwimMember swimMember = members.get(member.id());
                if (swimMember != null
                    && swimMember.getIncarnationNumber() == member.incarnationNumber()) {
                  requestProbes(swimMember.copy());
                }
              }
            },
            swimScheduler);
  }

  /**
   * Handles a probe from another peer.
   *
   * @param members the probing member and local member info
   * @return the current term
   */
  private ImmutableMember handleProbe(final Pair<ImmutableMember, ImmutableMember> members) {
    final ImmutableMember remoteMember = members.getLeft();
    final ImmutableMember localMember = members.getRight();

    LOGGER.trace(
        "{} - Received probe {} from {}", this.localMember.id(), localMember, remoteMember);

    // If the probe indicates a term greater than the local term, update the local term, increment
    // and respond.
    if (localMember.incarnationNumber() > this.localMember.getIncarnationNumber()) {
      this.localMember.setIncarnationNumber(localMember.incarnationNumber() + 1);
      if (config.isBroadcastDisputes()) {
        broadcast(this.localMember.copy());
      }
    }
    // If the probe indicates this member is suspect, increment the local term and respond.
    else if (localMember.state() == State.SUSPECT) {
      this.localMember.setIncarnationNumber(this.localMember.getIncarnationNumber() + 1);
      if (config.isBroadcastDisputes()) {
        broadcast(this.localMember.copy());
      }
    }

    // Update the state of the probing member.
    updateState(remoteMember);
    return this.localMember.copy();
  }

  /** Requests probes from n peers. */
  private void requestProbes(final ImmutableMember suspect) {
    final Collection<SwimMember> members =
        selectRandomMembers(config.getSuspectProbes() - 1, suspect);
    if (!members.isEmpty()) {
      final AtomicInteger counter = new AtomicInteger();
      final AtomicBoolean succeeded = new AtomicBoolean();
      for (final SwimMember member : members) {
        requestProbe(member, suspect)
            .whenCompleteAsync(
                (success, error) -> {
                  final int count = counter.incrementAndGet();
                  if (error == null && success) {
                    succeeded.set(true);
                  }
                  // If the count is equal to the number of probe peers and no probe has succeeded,
                  // the node is unreachable.
                  else if (count == members.size() && !succeeded.get()) {
                    failProbes(suspect);
                  }
                },
                swimScheduler);
      }
    } else {
      failProbes(suspect);
    }
  }

  /** Marks the given member suspect after all probes failing. */
  private void failProbes(final ImmutableMember suspect) {
    final SwimMember swimMember = new SwimMember(suspect);
    LOGGER.debug("{} - Failed all probes of {}", localMember.id(), swimMember);
    swimMember.setState(State.SUSPECT);
    if (updateState(swimMember.copy()) && config.isBroadcastUpdates()) {
      broadcast(swimMember.copy());
    }
  }

  /**
   * Requests a probe of the given suspect from the given member.
   *
   * @param member the member to perform the probe
   * @param suspect the suspect member to probe
   */
  private CompletableFuture<Boolean> requestProbe(
      final SwimMember member, final ImmutableMember suspect) {
    LOGGER.debug("{} - Requesting probe of {} from {}", this.localMember.id(), suspect, member);
    return bootstrapService
        .getMessagingService()
        .sendAndReceive(
            member.address(),
            MEMBERSHIP_PROBE_REQUEST,
            SERIALIZER.encode(suspect),
            false,
            config.getProbeTimeout().multipliedBy(2))
        .<Boolean>thenApply(SERIALIZER::decode)
        .<Boolean>exceptionally(e -> false)
        .thenApply(
            succeeded -> {
              LOGGER.debug(
                  "{} - Probe request of {} from {} {}",
                  localMember.id(),
                  suspect,
                  member,
                  succeeded ? "succeeded" : "failed");
              return succeeded;
            });
  }

  /**
   * Selects a set of random members, excluding the local member and a given member.
   *
   * @param count count the number of random members to select
   * @param exclude the member to exclude
   * @return members a set of random members
   */
  private Collection<SwimMember> selectRandomMembers(
      final int count, final ImmutableMember exclude) {
    final List<SwimMember> members =
        this.members.values().stream()
            .filter(
                member ->
                    !member.id().equals(localMember.id()) && !member.id().equals(exclude.id()))
            .collect(Collectors.toList());
    Collections.shuffle(members);
    return members.subList(0, Math.min(members.size(), count));
  }

  /**
   * Handles a probe request.
   *
   * @param member the member to probe
   */
  private CompletableFuture<Boolean> handleProbeRequest(final ImmutableMember member) {
    final CompletableFuture<Boolean> future = new CompletableFuture<>();
    swimScheduler.execute(
        () -> {
          LOGGER.trace("{} - Probing {}", localMember.id(), member);
          bootstrapService
              .getMessagingService()
              .sendAndReceive(
                  member.address(),
                  MEMBERSHIP_PROBE,
                  SERIALIZER.encode(Pair.of(localMember.copy(), member)),
                  false,
                  config.getProbeTimeout())
              .whenCompleteAsync(
                  (response, error) -> {
                    if (error != null) {
                      LOGGER.debug("{} - Failed to probe {}", localMember.id(), member);
                      future.complete(false);
                    } else {
                      future.complete(true);
                    }
                  },
                  swimScheduler);
        });
    return future;
  }

  /**
   * Broadcasts the given update to all peers.
   *
   * @param update the update to broadcast
   */
  private void broadcast(final ImmutableMember update) {
    for (final SwimMember member : members.values()) {
      if (!localMember.id().equals(member.id())) {
        unicast(member, update);
      }
    }
  }

  /**
   * Unicasts the given update to the given member.
   *
   * @param member the member to which to unicast the update
   * @param update the update to unicast
   */
  private void unicast(final SwimMember member, final ImmutableMember update) {
    bootstrapService
        .getUnicastService()
        .unicast(
            member.address(), MEMBERSHIP_GOSSIP, SERIALIZER.encode(Lists.newArrayList(update)));
  }

  /** Gossips pending updates to the cluster. */
  private void gossip() {
    // Check suspect nodes for failure timeouts.
    checkFailures();

    // Check local metadata for changes.
    checkMetadata();

    // Copy and clear the list of pending updates.
    if (!updates.isEmpty()) {
      final List<ImmutableMember> updates = Lists.newArrayList(this.updates.values());
      this.updates.clear();

      // Gossip the pending updates to peers.
      gossip(updates);
    }
  }

  /**
   * Gossips this node's pending updates with a random set of peers.
   *
   * @param updates a collection of updated to gossip
   */
  private void gossip(final Collection<ImmutableMember> updates) {
    // Get a list of available peers. If peers are available, randomize the peer list and select a
    // subset of
    // peers with which to gossip updates.
    final List<SwimMember> members = Lists.newArrayList(randomMembers);
    if (!members.isEmpty()) {
      Collections.shuffle(members);
      for (int i = 0; i < Math.min(members.size(), config.getGossipFanout()); i++) {
        gossip(members.get(i), updates);
      }
    }
  }

  /**
   * Gossips this node's pending updates with the given peer.
   *
   * @param member the peer with which to gossip this node's updates
   * @param updates the updated members to gossip
   */
  private void gossip(final SwimMember member, final Collection<ImmutableMember> updates) {
    LOGGER.trace("{} - Gossipping updates {} to {}", localMember.id(), updates, member);
    bootstrapService
        .getUnicastService()
        .unicast(member.address(), MEMBERSHIP_GOSSIP, SERIALIZER.encode(updates));
  }

  /** Handles a gossip message from a peer. */
  private void handleGossipUpdates(final Collection<ImmutableMember> updates) {
    for (final ImmutableMember update : updates) {
      updateState(update);
    }
  }

  /**
   * Handles a member location event.
   *
   * @param event the member location event
   */
  private void handleDiscoveryEvent(final NodeDiscoveryEvent event) {
    switch (event.type()) {
      case JOIN:
        handleJoinEvent(event.subject());
        break;
      case LEAVE:
        handleLeaveEvent(event.subject());
        break;
      default:
        throw new AssertionError();
    }
  }

  /** Handles a node join event. */
  private void handleJoinEvent(final Node node) {
    final SwimMember member = new SwimMember(MemberId.from(node.id().id()), node.address());
    if (!members.containsKey(member.id())) {
      probe(member.copy());
    }
  }

  /** Handles a node leave event. */
  private void handleLeaveEvent(final Node node) {
    final SwimMember member = members.get(MemberId.from(node.id().id()));
    if (member != null && !member.isActive()) {
      members.remove(member.id());
    }
  }

  /** Registers message handlers for the SWIM protocol. */
  private void registerHandlers() {
    // Register TCP message handlers.
    bootstrapService
        .getMessagingService()
        .registerHandler(MEMBERSHIP_SYNC, syncHandler, swimScheduler);
    bootstrapService
        .getMessagingService()
        .registerHandler(MEMBERSHIP_PROBE, probeHandler, swimScheduler);
    bootstrapService
        .getMessagingService()
        .registerHandler(MEMBERSHIP_PROBE_REQUEST, probeRequestHandler);

    // Register UDP message listeners.
    bootstrapService
        .getUnicastService()
        .addListener(MEMBERSHIP_GOSSIP, gossipListener, swimScheduler);
  }

  /** Unregisters handlers for the SWIM protocol. */
  private void unregisterHandlers() {
    // Unregister TCP message handlers.
    bootstrapService.getMessagingService().unregisterHandler(MEMBERSHIP_SYNC);
    bootstrapService.getMessagingService().unregisterHandler(MEMBERSHIP_PROBE);
    bootstrapService.getMessagingService().unregisterHandler(MEMBERSHIP_PROBE_REQUEST);

    // Unregister UDP message listeners.
    bootstrapService.getUnicastService().removeListener(MEMBERSHIP_GOSSIP, gossipListener);
  }

  /** Bootstrap member location provider type. */
  public static class Type implements GroupMembershipProtocol.Type<SwimMembershipProtocolConfig> {
    private static final String NAME = "swim";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public SwimMembershipProtocolConfig newConfig() {
      return new SwimMembershipProtocolConfig();
    }

    @Override
    public GroupMembershipProtocol newProtocol(final SwimMembershipProtocolConfig config) {
      return new SwimMembershipProtocol(config);
    }
  }

  /** Immutable member. */
  static class ImmutableMember extends Member {
    private final Version version;
    private final long timestamp;
    private final State state;
    private final long incarnationNumber;

    ImmutableMember(
        final MemberId id,
        final Address address,
        final String zone,
        final String rack,
        final String host,
        final Properties properties,
        final Version version,
        final long timestamp,
        final State state,
        final long incarnationNumber) {
      super(id, address, zone, rack, host, properties);
      this.version = version;
      this.timestamp = timestamp;
      this.state = state;
      this.incarnationNumber = incarnationNumber;
    }

    @Override
    public Version version() {
      return version;
    }

    @Override
    public long timestamp() {
      return timestamp;
    }

    @Override
    public String toString() {
      return toStringHelper(Member.class)
          .add("id", id())
          .add("address", address())
          .add("properties", properties())
          .add("version", version())
          .add("timestamp", timestamp())
          .add("state", state())
          .add("incarnationNumber", incarnationNumber())
          .toString();
    }

    /**
     * Returns the member's state.
     *
     * @return the member's state
     */
    State state() {
      return state;
    }

    /**
     * Returns the member's incarnation number.
     *
     * @return the member's incarnation number
     */
    long incarnationNumber() {
      return incarnationNumber;
    }
  }

  /** Swim member. */
  static class SwimMember extends Member {
    private final Version version;
    private final long timestamp;
    private volatile State state;
    private volatile long incarnationNumber;
    private volatile long updated;

    SwimMember(final MemberId id, final Address address) {
      super(id, address);
      this.version = null;
      this.timestamp = 0;
    }

    SwimMember(
        final MemberId id,
        final Address address,
        final String zone,
        final String rack,
        final String host,
        final Properties properties,
        final Version version,
        final long timestamp) {
      super(id, address, zone, rack, host, properties);
      this.version = version;
      this.timestamp = timestamp;
      incarnationNumber = System.currentTimeMillis();
    }

    SwimMember(final ImmutableMember member) {
      super(
          member.id(),
          member.address(),
          member.zone(),
          member.rack(),
          member.host(),
          member.properties());
      this.version = member.version;
      this.timestamp = member.timestamp;
      this.state = member.state;
      this.incarnationNumber = member.incarnationNumber;
    }

    /**
     * Returns the member's state.
     *
     * @return the member's state
     */
    State getState() {
      return state;
    }

    /**
     * Changes the member's state.
     *
     * @param state the member's state
     */
    void setState(final State state) {
      if (this.state != state) {
        this.state = state;
        setUpdated(System.currentTimeMillis());
      }
    }

    @Override
    public boolean isActive() {
      return state.isActive();
    }

    @Override
    public boolean isReachable() {
      return state.isReachable();
    }

    @Override
    public Version version() {
      return version;
    }

    @Override
    public long timestamp() {
      return timestamp;
    }

    /**
     * Returns the member incarnation number.
     *
     * @return the member incarnation number
     */
    long getIncarnationNumber() {
      return incarnationNumber;
    }

    /**
     * Sets the member's incarnation number.
     *
     * @param incarnationNumber the member's incarnation number
     */
    void setIncarnationNumber(final long incarnationNumber) {
      this.incarnationNumber = incarnationNumber;
    }

    /**
     * Returns the wall clock timestamp.
     *
     * @return the wall clock timestamp
     */
    long getUpdated() {
      return updated;
    }

    /**
     * Sets the wall clock timestamp.
     *
     * @param updated the wall clock timestamp
     */
    void setUpdated(final long updated) {
      this.updated = updated;
    }

    /**
     * Copies the member's state to a new object.
     *
     * @return the copied object
     */
    ImmutableMember copy() {
      return new ImmutableMember(
          id(),
          address(),
          zone(),
          rack(),
          host(),
          properties(),
          version(),
          timestamp(),
          state,
          incarnationNumber);
    }
  }

  /** Member states. */
  enum State {
    ALIVE(true, true),
    SUSPECT(true, false),
    DEAD(false, false);

    private final boolean active;
    private final boolean reachable;

    State(final boolean active, final boolean reachable) {
      this.active = active;
      this.reachable = reachable;
    }

    boolean isActive() {
      return active;
    }

    boolean isReachable() {
      return reachable;
    }
  }
}
