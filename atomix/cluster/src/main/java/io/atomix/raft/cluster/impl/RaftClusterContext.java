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

import com.google.common.collect.Comparators;
import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftCluster;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.raft.utils.JointConsensusVoteQuorum;
import io.atomix.raft.utils.SimpleVoteQuorum;
import io.atomix.raft.utils.VoteQuorum;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Manages the persistent state of the Raft cluster from the perspective of a single server. */
public final class RaftClusterContext implements RaftCluster, AutoCloseable {

  private final RaftContext raft;
  private final DefaultRaftMember localMember;
  private final Map<MemberId, RaftMemberContext> remoteMemberContexts = new HashMap<>();
  private Configuration configuration;
  private CompletableFuture<Void> bootstrapFuture;

  public RaftClusterContext(final MemberId localMemberId, final RaftContext raft) {
    final Instant time = Instant.now();
    localMember =
        new DefaultRaftMember(localMemberId, RaftMember.Type.PASSIVE, time).setCluster(this);
    this.raft = checkNotNull(raft, "context cannot be null");

    // If a configuration is stored, use the stored configuration, otherwise configure the server
    // with the user provided configuration.
    configuration = raft.getMetaStore().loadConfiguration();

    // Iterate through members in the new configuration and add remote members.
    if (configuration != null) {
      final Instant updateTime = Instant.ofEpochMilli(configuration.time());
      final var configuredMembers = new ArrayList<>(configuration.oldMembers());
      configuredMembers.addAll(configuration.members());
      for (final RaftMember member : configuredMembers) {
        if (member.equals(localMember)) {
          localMember.setType(member.getType());
        } else {
          // If the member state doesn't already exist, create it.
          remoteMemberContexts.put(
              member.memberId(),
              new RaftMemberContext(
                  new DefaultRaftMember(member.memberId(), member.getType(), updateTime),
                  this,
                  raft.getMaxAppendsPerFollower()));
        }
      }

      remoteMemberContexts
          .values()
          .forEach(
              context -> {
                context.resetState(raft.getLog());
              });
    }
  }

  @Override
  public DefaultRaftMember getMember(final MemberId id) {
    if (localMember.memberId().equals(id)) {
      return localMember;
    }
    return getRemoteMember(id);
  }

  @Override
  public CompletableFuture<Void> bootstrap(final Collection<MemberId> cluster) {
    if (bootstrapFuture != null) {
      return bootstrapFuture;
    }

    ensureConfigurationIsConsistent(cluster);

    bootstrapFuture = new CompletableFuture<>();
    final var isOnBoostrapCluster = configuration == null;
    if (isOnBoostrapCluster) {
      localMember.setType(Type.ACTIVE);
      createInitialConfig(cluster);
    }

    raft.getThreadContext()
        .execute(
            () -> {
              // Transition the server to the appropriate state for the local member type.
              raft.transition(localMember.getType());

              if (isOnBoostrapCluster) {
                // commit configuration and transition
                commit();
              }

              completeBootstrapFuture();
            });

    return bootstrapFuture.whenComplete((result, error) -> bootstrapFuture = null);
  }

  @Override
  public RaftMember getLeader() {
    return raft.getLeader();
  }

  @Override
  public RaftMember getLocalMember() {
    return localMember;
  }

  @Override
  public Collection<RaftMember> getMembers() {
    final var allMembers =
        remoteMemberContexts.values().stream()
            .map((context) -> (RaftMember) context.getMember())
            .collect(Collectors.toCollection(ArrayList::new));
    allMembers.add(localMember);
    return allMembers;
  }

  @Override
  public long getTerm() {
    return raft.getTerm();
  }

  private void ensureConfigurationIsConsistent(final Collection<MemberId> cluster) {
    final var hasPersistedConfiguration = configuration != null;
    if (hasPersistedConfiguration) {
      final var newClusterSize = cluster.size();
      final var persistedClusterSize = configuration.members().size();

      if (persistedClusterSize != newClusterSize) {
        throw new IllegalStateException(
            String.format(
                "Expected that persisted cluster size '%d' is equal to given one '%d', but was different. "
                    + "Persisted configuration '%s' is different then given one, new given member id's are: '%s'. Changing the configuration is not supported. "
                    + "Please restart with the same configuration or recreate a new cluster after deleting persisted data.",
                persistedClusterSize,
                newClusterSize,
                configuration,
                Arrays.toString(cluster.toArray())));
      }

      final var persistedMembers = configuration.members();
      for (final MemberId memberId : cluster) {
        final var noMatch =
            persistedMembers.stream()
                .map(RaftMember::memberId)
                .noneMatch(persistedMemberId -> persistedMemberId.equals(memberId));
        if (noMatch) {
          throw new IllegalStateException(
              String.format(
                  "Expected to find given node id '%s' in persisted members '%s', but was not found. "
                      + "Persisted configuration is different then given one. Changing the configuration is not supported. "
                      + "Please restart with the same configuration or recreate a new cluster after deleting persisted data.",
                  memberId, Arrays.toString(persistedMembers.toArray())));
        }
      }
    }
  }

  private void createInitialConfig(final Collection<MemberId> cluster) {
    // Create a set of active members.
    final Set<RaftMember> activeMembers =
        cluster.stream()
            .filter(m -> !m.equals(localMember.memberId()))
            .map(m -> new DefaultRaftMember(m, Type.ACTIVE, localMember.getLastUpdated()))
            .collect(Collectors.toSet());

    // Add the local member to the set of active members.
    activeMembers.add(localMember);

    // Create a new configuration and store it on disk to ensure the cluster can fall back to the
    // configuration.
    configure(new Configuration(0, 0, localMember.getLastUpdated().toEpochMilli(), activeMembers));
  }

  /**
   * Returns a member by ID.
   *
   * @param id The member ID.
   * @return The member.
   */
  private DefaultRaftMember getRemoteMember(final MemberId id) {
    final RaftMemberContext member = remoteMemberContexts.get(id);
    return member != null ? member.getMember() : null;
  }

  /**
   * Returns a member state by ID.
   *
   * @param id The member ID.
   * @return The member state.
   */
  public RaftMemberContext getMemberState(final MemberId id) {
    return remoteMemberContexts.get(id);
  }

  /**
   * Calculates the smallest value that is reported for a majority of this cluster, assuming that
   * the local node always has the highest value.
   *
   * @param calculateMemberValue a function that calculates a value for a given member. Will be
   *     evaluated at least once for every remote member.
   * @return empty when no remote members are present, otherwise the smallest value that is reported
   *     by enough remote members to form a quorum with the local member.
   */
  public <T extends Comparable<T>> Optional<T> getQuorumFor(
      final Function<RaftMemberContext, T> calculateMemberValue) {
    final var contexts = new ArrayList<>(getRemoteActiveMembers());

    if (configuration.requiresJointConsensus()) {
      final var oldMembers = configuration.oldMembers();
      final var newMembers = configuration.members();

      final var oldContexts =
          contexts.stream()
              .filter(context -> oldMembers.contains(context.getMember()))
              .collect(Collectors.toCollection(ArrayList::new));
      final var newContexts =
          contexts.stream()
              .filter(context -> newMembers.contains(context.getMember()))
              .collect(Collectors.toCollection(ArrayList::new));

      final var oldQuorum = getQuorumFor(oldContexts, calculateMemberValue);
      final var newQuorum = getQuorumFor(newContexts, calculateMemberValue);
      if (oldQuorum.isPresent() && newQuorum.isPresent()) {
        return Optional.of(Comparators.min(oldQuorum.get(), newQuorum.get()));
      } else if (oldQuorum.isPresent()) {
        return oldQuorum;
      } else {
        return newQuorum;
      }
    }

    if (contexts.isEmpty()) {
      return Optional.empty();
    }

    return getQuorumFor(contexts, calculateMemberValue);
  }

  private <T extends Comparable<T>> Optional<T> getQuorumFor(
      final List<RaftMemberContext> contexts,
      final Function<RaftMemberContext, T> calculateMemberValue) {

    contexts.sort(Comparator.comparing(calculateMemberValue).reversed());

    final var remoteActiveMembers = contexts.size();
    final var totalActiveMembers = remoteActiveMembers + 1;
    final var quorum = (totalActiveMembers / 2) + 1;
    final var remoteQuorumIndex = quorum - 1 - 1;
    final var context = contexts.get(remoteQuorumIndex);
    return Optional.of(calculateMemberValue.apply(context));
  }

  /**
   * @return A list of remote, active members.
   */
  public List<RaftMemberContext> getRemoteActiveMembers() {
    return remoteMemberContexts.values().stream()
        .filter(context -> context.getMember().getType() == Type.ACTIVE)
        .toList();
  }

  /**
   * @return A list of remote, active members.
   */
  public List<RaftMemberContext> getReplicationTargets() {
    return remoteMemberContexts.values().stream()
        .filter(context -> context.getMember().getType() != Type.INACTIVE)
        .toList();
  }

  private void completeBootstrapFuture() {
    // If the local member is not present in the configuration, fail the future.
    if (!configuration.members().contains(localMember)) {
      bootstrapFuture.completeExceptionally(
          new IllegalStateException("not a member of the cluster"));
    } else {
      bootstrapFuture.complete(null);
    }
  }

  /**
   * Resets the cluster state to the persisted state.
   *
   * @return The cluster state.
   */
  public RaftClusterContext reset() {
    final var configuration = raft.getMetaStore().loadConfiguration();
    if (configuration != null) {
      configure(configuration);
    }
    return this;
  }

  /**
   * Configures the cluster state.
   *
   * @param configuration The cluster configuration.
   */
  public void configure(final Configuration configuration) {
    checkNotNull(configuration, "configuration cannot be null");

    // If the configuration index is less than the currently configured index, ignore it.
    // Configurations can be persisted and applying old configurations can revert newer
    // configurations.
    final var currentConfig = this.configuration;
    if (currentConfig != null && configuration.index() <= currentConfig.index()) {
      return;
    }

    final Instant time = Instant.ofEpochMilli(configuration.time());

    // Iterate through members in the new configuration, add any missing members, and update
    // existing members.
    final var allMembers = new HashSet<RaftMember>();
    for (final RaftMember member : configuration.oldMembers()) {
      allMembers.remove(member);
      allMembers.add(member);
    }
    for (final RaftMember member : configuration.members()) {
      allMembers.remove(member);
      allMembers.add(member);
    }

    // 1. Remove contexts for members which aren't in the configuration
    remoteMemberContexts.entrySet().stream()
        .filter(entry -> !allMembers.contains(entry.getValue().getMember()))
        .forEach(entry -> entry.getValue().closeReplicationContext());

    remoteMemberContexts
        .entrySet()
        .removeIf(entry -> !allMembers.contains((entry.getValue().getMember())));

    // 2. For all members in configuration, change type or create initial context
    for (final var member : allMembers) {
      updateMember(member, time);
    }

    // Transition the local member only if the member is being promoted and not demoted.
    // Configuration changes that demote the local member are only applied to the local server
    // upon commitment. This ensures that e.g. a leader that's removing itself from the quorum
    // can commit the configuration change prior to shutting down.
    if (wasPromoted(configuration)) {
      raft.transition(localMember.getType());
    }

    this.configuration = configuration;

    // Store the configuration if it's already committed.
    if (raft.getCommitIndex() >= configuration.index()) {
      raft.getMetaStore().storeConfiguration(configuration);
    }
  }

  private boolean wasPromoted(final Configuration configuration) {
    return configuration.members().stream()
        .anyMatch(
            m -> m.equals(localMember) && localMember.getType().ordinal() < m.getType().ordinal());
  }

  private void updateMember(final RaftMember member, final Instant time) {
    if (member.equals(localMember)) {
      localMember.update(member.getType(), time);
      return;
    }

    // If the member state doesn't already exist, create it.
    RaftMemberContext state = remoteMemberContexts.get(member.memberId());
    if (state == null) {
      final DefaultRaftMember defaultMember =
          new DefaultRaftMember(member.memberId(), member.getType(), time);
      state = new RaftMemberContext(defaultMember, this, raft.getMaxAppendsPerFollower());
      state.resetState(raft.getLog());
      remoteMemberContexts.put(member.memberId(), state);
    }

    // If the member type has changed, update the member type and reset its state.
    if (state.getMember().getType() != member.getType()) {
      state.getMember().update(member.getType(), time);
      state.resetState(raft.getLog());
    }
  }

  /** Commit the current configuration to disk. */
  public void commit() {
    // If the local stored configuration is older than the committed configuration, overwrite it.
    if (raft.getMetaStore().loadConfiguration().index() < configuration.index()) {
      raft.getMetaStore().storeConfiguration(configuration);
    }

    // Apply the configuration to the local server state.
    raft.transition(localMember.getType());
  }

  @Override
  public void close() {
    remoteMemberContexts.values().forEach(context -> context.getMember().close());
    localMember.close();
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

  public VoteQuorum getVoteQuorum(final Consumer<Boolean> callback) {
    final VoteQuorum quorum;
    if (configuration.requiresJointConsensus()) {
      quorum =
          new JointConsensusVoteQuorum(
              configuration.oldMembers().stream()
                  .map(RaftMember::memberId)
                  .collect(Collectors.toSet()),
              configuration.members().stream()
                  .map(RaftMember::memberId)
                  .collect(Collectors.toSet()),
              callback);
      return quorum;
    } else {
      quorum =
          new SimpleVoteQuorum(
              callback,
              configuration.members().stream()
                  .map(RaftMember::memberId)
                  .collect(Collectors.toSet()));
    }
    quorum.succeed(localMember.memberId());
    return quorum;
  }

  public boolean isMember(final MemberId memberId) {
    return configuration.members().stream().anyMatch(member -> member.memberId().equals(memberId));
  }

  public boolean inJointConsensus() {
    return configuration.requiresJointConsensus();
  }
}
