/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.raft.cluster.impl;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftCluster;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.storage.system.Configuration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Manages the persistent state of the Raft cluster from the perspective of a single server. */
public final class RaftClusterContext implements RaftCluster, AutoCloseable {

  private final RaftContext raft;
  private final DefaultRaftMember member;
  private final Map<MemberId, RaftMemberContext> membersMap = new ConcurrentHashMap<>();
  private final Set<RaftMember> members = new CopyOnWriteArraySet<>();
  private final List<RaftMemberContext> remoteMembers = new CopyOnWriteArrayList<>();
  private final Map<RaftMember.Type, List<RaftMemberContext>> memberTypes = new HashMap<>();
  private volatile Configuration configuration;
  private volatile CompletableFuture<Void> bootstrapFuture;

  public RaftClusterContext(final MemberId localMemberId, final RaftContext raft) {
    final Instant time = Instant.now();
    member = new DefaultRaftMember(localMemberId, RaftMember.Type.PASSIVE, time).setCluster(this);
    this.raft = checkNotNull(raft, "context cannot be null");

    // If a configuration is stored, use the stored configuration, otherwise configure the server
    // with the user provided configuration.
    configuration = raft.getMetaStore().loadConfiguration();

    // Iterate through members in the new configuration and add remote members.
    if (configuration != null) {
      final Instant updateTime = Instant.ofEpochMilli(configuration.time());
      for (final RaftMember member : configuration.members()) {
        if (member.equals(this.member)) {
          this.member.setType(member.getType());
          members.add(this.member);
        } else {
          // If the member state doesn't already exist, create it.
          final RaftMemberContext state =
              new RaftMemberContext(
                  new DefaultRaftMember(member.memberId(), member.getType(), updateTime),
                  this,
                  raft.getMaxAppendsPerFollower());
          state.resetState(raft.getLog());
          members.add(state.getMember());
          remoteMembers.add(state);
          membersMap.put(member.memberId(), state);

          // Add the member to a type specific map.
          List<RaftMemberContext> memberType = memberTypes.get(member.getType());
          if (memberType == null) {
            memberType = new CopyOnWriteArrayList<>();
            memberTypes.put(member.getType(), memberType);
          }
          memberType.add(state);
        }
      }
    }
  }

  @Override
  public void addLeaderElectionListener(final Consumer<RaftMember> callback) {
    raft.addLeaderElectionListener(callback);
  }

  @Override
  public void removeLeaderElectionListener(final Consumer<RaftMember> listener) {
    raft.removeLeaderElectionListener(listener);
  }

  @Override
  public DefaultRaftMember getMember(final MemberId id) {
    if (member.memberId().equals(id)) {
      return member;
    }
    return getRemoteMember(id);
  }

  @Override
  public CompletableFuture<Void> bootstrap(final Collection<MemberId> cluster) {
    if (bootstrapFuture != null) {
      return bootstrapFuture;
    }

    bootstrapFuture = new CompletableFuture<>();
    final var isOnBoostrapCluster = configuration == null;
    if (isOnBoostrapCluster) {
      member.setType(Type.ACTIVE);
      createInitialConfig(cluster);
    } else if (member.getType() == Type.BOOTSTRAP) {
      member.setType(
          Type.ACTIVE); // bootstrap is deprecated, but might be persisted on previous started
      // cluster
    }

    raft.getThreadContext()
        .execute(
            () -> {
              // Transition the server to the appropriate state for the local member type.
              raft.transition(member.getType());
              if (member.getType() == Type.BOOTSTRAP) {
                // RaftMember.Type.BOOTSTRAP is deprecated, but might be persisted on a previous
                // started cluster
                member.setType(Type.ACTIVE);
              }

              if (isOnBoostrapCluster) {
                // commit configuration and transition
                commit();
              }
              completeBootstrapFuture();
            });

    return bootstrapFuture.whenComplete((result, error) -> bootstrapFuture = null);
  }

  private void createInitialConfig(final Collection<MemberId> cluster) {
    // Create a set of active members.
    final Set<RaftMember> activeMembers =
        cluster.stream()
            .filter(m -> !m.equals(member.memberId()))
            .map(m -> new DefaultRaftMember(m, Type.ACTIVE, member.getLastUpdated()))
            .collect(Collectors.toSet());

    // Add the local member to the set of active members.
    activeMembers.add(member);

    // Create a new configuration and store it on disk to ensure the cluster can fall back to the
    // configuration.
    configure(new Configuration(0, 0, member.getLastUpdated().toEpochMilli(), activeMembers));
  }

  @Override
  public RaftMember getLeader() {
    return raft.getLeader();
  }

  @Override
  public RaftMember getMember() {
    return member;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Collection<RaftMember> getMembers() {
    return new ArrayList<>(members);
  }

  @Override
  public long getTerm() {
    return raft.getTerm();
  }

  /**
   * Returns a member by ID.
   *
   * @param id The member ID.
   * @return The member.
   */
  public DefaultRaftMember getRemoteMember(final MemberId id) {
    final RaftMemberContext member = membersMap.get(id);
    return member != null ? member.getMember() : null;
  }

  /**
   * Returns a member state by ID.
   *
   * @param id The member ID.
   * @return The member state.
   */
  public RaftMemberContext getMemberState(final MemberId id) {
    return membersMap.get(id);
  }

  /**
   * Returns a list of active members.
   *
   * @param comparator A comparator with which to sort the members list.
   * @return The sorted members list.
   */
  public List<RaftMemberContext> getActiveMemberStates(
      final Comparator<RaftMemberContext> comparator) {
    final List<RaftMemberContext> activeMembers = new ArrayList<>(getActiveMemberStates());
    activeMembers.sort(comparator);
    return activeMembers;
  }

  /**
   * Returns a list of active members.
   *
   * @return A list of active members.
   */
  public List<RaftMemberContext> getActiveMemberStates() {
    return getRemoteMemberStates(RaftMember.Type.ACTIVE);
  }

  /**
   * Returns a list of member states for the given type.
   *
   * @param type The member type.
   * @return A list of member states for the given type.
   */
  public List<RaftMemberContext> getRemoteMemberStates(final RaftMember.Type type) {
    final List<RaftMemberContext> members = memberTypes.get(type);
    return members != null ? members : List.of();
  }

  private void completeBootstrapFuture() {
    // If the local member is not present in the configuration, fail the future.
    if (!members.contains(member)) {
      bootstrapFuture.completeExceptionally(
          new IllegalStateException("not a member of the cluster"));
    } else if (bootstrapFuture != null) {
      bootstrapFuture.complete(null);
    }
  }

  /**
   * Resets the cluster state to the persisted state.
   *
   * @return The cluster state.
   */
  public RaftClusterContext reset() {
    configure(raft.getMetaStore().loadConfiguration());
    return this;
  }

  /**
   * Configures the cluster state.
   *
   * @param configuration The cluster configuration.
   * @return The cluster state.
   */
  public RaftClusterContext configure(final Configuration configuration) {
    checkNotNull(configuration, "configuration cannot be null");

    // If the configuration index is less than the currently configured index, ignore it.
    // Configurations can be persisted and applying old configurations can revert newer
    // configurations.
    if (this.configuration != null && configuration.index() <= this.configuration.index()) {
      return this;
    }

    final Instant time = Instant.ofEpochMilli(configuration.time());

    // Iterate through members in the new configuration, add any missing members, and update
    // existing members.
    boolean transition = false;
    for (final RaftMember member : configuration.members()) {
      if (member.equals(this.member)) {
        transition = this.member.getType().ordinal() < member.getType().ordinal();
        this.member.update(member.getType(), time);
        members.add(this.member);
      } else {
        // If the member state doesn't already exist, create it.
        RaftMemberContext state = membersMap.get(member.memberId());
        if (state == null) {
          final DefaultRaftMember defaultMember =
              new DefaultRaftMember(member.memberId(), member.getType(), time);
          state = new RaftMemberContext(defaultMember, this, raft.getMaxAppendsPerFollower());
          state.resetState(raft.getLog());
          members.add(state.getMember());
          remoteMembers.add(state);
          membersMap.put(member.memberId(), state);
        }

        // If the member type has changed, update the member type and reset its state.
        if (state.getMember().getType() != member.getType()) {
          state.getMember().update(member.getType(), time);
          state.resetState(raft.getLog());
        }

        // Update the optimized member collections according to the member type.
        for (final List<RaftMemberContext> memberType : memberTypes.values()) {
          memberType.remove(state);
        }

        List<RaftMemberContext> memberType = memberTypes.get(member.getType());
        if (memberType == null) {
          memberType = new CopyOnWriteArrayList<>();
          memberTypes.put(member.getType(), memberType);
        }
        memberType.add(state);
      }
    }

    // Transition the local member only if the member is being promoted and not demoted.
    // Configuration changes that demote the local member are only applied to the local server
    // upon commitment. This ensures that e.g. a leader that's removing itself from the quorum
    // can commit the configuration change prior to shutting down.
    if (transition) {
      raft.transition(member.getType());
    }

    // If the local member was removed from the cluster, remove it from the members list.
    if (!configuration.members().contains(member)) {
      members.remove(member);
    }

    this.configuration = configuration;

    // Store the configuration if it's already committed.
    if (raft.getCommitIndex() >= configuration.index()) {
      raft.getMetaStore().storeConfiguration(configuration);
    }

    return this;
  }

  /**
   * Commit the current configuration to disk.
   *
   * @return The cluster state.
   */
  public RaftClusterContext commit() {
    // Apply the configuration to the local server state.
    raft.transition(member.getType());

    // If the local stored configuration is older than the committed configuration, overwrite it.
    if (raft.getMetaStore().loadConfiguration().index() < configuration.index()) {
      raft.getMetaStore().storeConfiguration(configuration);
    }
    return this;
  }

  @Override
  public void close() {
    for (final RaftMemberContext member : remoteMembers) {
      member.getMember().close();
    }
    member.close();
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("server", raft.getName()).toString();
  }

  /**
   * Returns the cluster configuration.
   *
   * @return The cluster configuration.
   */
  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * Returns the parent context.
   *
   * @return The parent context.
   */
  public RaftContext getContext() {
    return raft;
  }

  /**
   * Returns the remote quorum count.
   *
   * @return The remote quorum count.
   */
  public int getQuorum() {
    return (int) Math.floor((getActiveMemberStates().size() + 1) / 2.0) + 1;
  }

  /**
   * Returns a list of all member states.
   *
   * @return A list of all member states.
   */
  public List<RaftMemberContext> getRemoteMemberStates() {
    return remoteMembers;
  }
}
