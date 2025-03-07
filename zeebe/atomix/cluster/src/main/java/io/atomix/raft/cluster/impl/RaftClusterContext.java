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
import io.atomix.raft.impl.ReconfigurationHelper;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.raft.utils.JointConsensusVoteQuorum;
import io.atomix.raft.utils.SimpleVoteQuorum;
import io.atomix.raft.utils.VoteQuorum;
import java.time.Instant;
import java.util.ArrayList;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Manages the persistent state of the Raft cluster from the perspective of a single server. */
public final class RaftClusterContext implements RaftCluster, AutoCloseable {
  private final RaftContext raft;
  private final DefaultRaftMember localMember;
  private final Map<MemberId, RaftMemberContext> remoteMemberContexts = new HashMap<>();
  private final Set<RaftMemberContext> replicationTargets = new HashSet<>();
  private final Set<RaftMemberContext> remoteActiveMembers = new HashSet<>();
  private boolean hasRemoteActiveMembers = false;
  private Configuration configuration;

  public RaftClusterContext(final MemberId localMemberId, final RaftContext raft) {
    final Instant time = Instant.now();
    localMember =
        new DefaultRaftMember(localMemberId, RaftMember.Type.PASSIVE, time).setCluster(this);
    this.raft = checkNotNull(raft, "context cannot be null");
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("server", raft.getName()).toString();
  }

  @Override
  public CompletableFuture<Void> bootstrap(final Collection<MemberId> cluster) {
    final var bootstrapFuture = new CompletableFuture<Void>();
    raft.getThreadContext()
        .execute(
            () -> {
              // If a configuration is stored, use the stored configuration, otherwise configure the
              // server
              // with the user provided configuration.
              final var storedConfiguration = raft.getMetaStore().loadConfiguration();
              if (storedConfiguration != null) {
                updateConfiguration(storedConfiguration);
              } else {
                createInitialConfig(cluster);
              }
              raft.transition(localMember.getType());
              bootstrapFuture.complete(null);
            });

    return bootstrapFuture;
  }

  @Override
  public CompletableFuture<Void> join(final Collection<MemberId> cluster) {
    return new ReconfigurationHelper(raft)
        .join(cluster)
        // Usually the transition is triggered by `onConfigure` when the leader sends the updated
        // configuration. If the join is attempted again, it can be accepted without a configuration
        // change and nothing triggers the transition.
        // To avoid this, always transition to the configured role when joining completes
        // successfully. If this is the first join attempt, it's likely that this transition is from
        // inactive to inactive and the actual transition to the active role will happen when the
        // leader sends the updated configuration.
        .thenRunAsync(() -> raft.transition(localMember.getType()), raft.getThreadContext());
  }

  @Override
  public DefaultRaftMember getMember(final MemberId id) {
    if (localMember.memberId().equals(id)) {
      return localMember;
    }
    final var context = remoteMemberContexts.get(id);
    return context != null ? context.getMember() : null;
  }

  @Override
  public RaftMember getLocalMember() {
    return localMember;
  }

  @Override
  public Collection<RaftMember> getMembers() {
    return configuration != null ? configuration.allMembers() : null;
  }

  private void createInitialConfig(final Collection<MemberId> cluster) {
    localMember.setType(Type.ACTIVE);

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
    final var initialConfiguration =
        new Configuration(0, 0, localMember.getLastUpdated().toEpochMilli(), activeMembers);
    configure(initialConfiguration);
    commitCurrentConfiguration();
  }

  /** Returns the context for a given member. */
  public RaftMemberContext getMemberContext(final MemberId id) {
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
    final var contexts = new ArrayList<>(remoteActiveMembers);

    if (configuration.requiresJointConsensus()) {
      final var oldMembers = configuration.oldMembers();
      final var newMembers = configuration.newMembers();

      final var oldContexts =
          contexts.stream()
              .filter(context -> oldMembers.contains(context.getMember()))
              .collect(Collectors.toCollection(ArrayList::new));
      final var newContexts =
          contexts.stream()
              .filter(context -> newMembers.contains(context.getMember()))
              .collect(Collectors.toCollection(ArrayList::new));

      final var oldQuorum =
          getQuorumFor(oldContexts, calculateMemberValue, oldMembers.contains(localMember));
      final var newQuorum =
          getQuorumFor(newContexts, calculateMemberValue, newMembers.contains(localMember));
      if (oldQuorum.isPresent() && newQuorum.isPresent()) {
        return Optional.of(Comparators.min(oldQuorum.get(), newQuorum.get()));
      } else if (oldQuorum.isPresent()) {
        return oldQuorum;
      } else {
        return newQuorum;
      }
    }

    return getQuorumFor(
        contexts, calculateMemberValue, configuration.newMembers().contains(localMember));
  }

  private <T extends Comparable<T>> Optional<T> getQuorumFor(
      final List<RaftMemberContext> contexts,
      final Function<RaftMemberContext, T> calculateMemberValue,
      final boolean includeLocalMemberInQuorum) {
    if (contexts.isEmpty()) {
      return Optional.empty();
    }
    contexts.sort(Comparator.comparing(calculateMemberValue).reversed());

    final var remoteActiveMembers = contexts.size();
    final int includeLocalMember = includeLocalMemberInQuorum ? 1 : 0;
    final var totalActiveMembers = remoteActiveMembers + includeLocalMember;
    final var quorum = (totalActiveMembers / 2) + 1;

    final var remoteQuorumIndex = quorum - 1 - includeLocalMember;
    final var context = contexts.get(remoteQuorumIndex);
    return Optional.of(calculateMemberValue.apply(context));
  }

  /**
   * @return true if the cluster has no remote active members and only the local member is active.
   */
  public boolean isSingleMemberCluster() {
    return !hasRemoteActiveMembers;
  }

  /**
   * @return A list remote members which participate in voting, i.e. are active.
   */
  public Set<RaftMember> getVotingMembers() {
    return remoteActiveMembers.stream()
        .map(RaftMemberContext::getMember)
        .collect(Collectors.toSet());
  }

  /**
   * @return A list of remote members that a leader should replicate to.
   */
  public Set<RaftMemberContext> getReplicationTargets() {
    return replicationTargets;
  }

  /**
   * @return true if the given member is part of the cluster, false otherwise
   */
  public boolean isMember(final MemberId memberId) {
    return localMember.memberId().equals(memberId) || remoteMemberContexts.containsKey(memberId);
  }

  /**
   * @return true if the current configuration is a join consensus configuration.
   */
  public boolean inJointConsensus() {
    return configuration.requiresJointConsensus();
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public RaftContext getContext() {
    return raft;
  }

  public VoteQuorum getVoteQuorum(final Consumer<Boolean> callback) {
    final VoteQuorum quorum;
    if (configuration.requiresJointConsensus()) {
      quorum =
          new JointConsensusVoteQuorum(
              callback,
              configuration.oldMembers().stream()
                  .map(RaftMember::memberId)
                  .collect(Collectors.toSet()),
              configuration.newMembers().stream()
                  .map(RaftMember::memberId)
                  .collect(Collectors.toSet()));
    } else {
      quorum =
          new SimpleVoteQuorum(
              callback,
              configuration.newMembers().stream()
                  .map(RaftMember::memberId)
                  .collect(Collectors.toSet()));
    }
    quorum.succeed(localMember.memberId());
    return quorum;
  }

  /**
   * Resets the cluster state to the persisted state.
   *
   * @return The cluster state.
   */
  public RaftClusterContext reset() {
    final var storedConfiguration = raft.getMetaStore().loadConfiguration();
    if (storedConfiguration != null) {
      configure(storedConfiguration);
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

    final var initialType = localMember.getType();
    updateConfiguration(configuration);
    final var newType = localMember.getType();
    if (initialType.ordinal() < newType.ordinal()) {
      raft.transition(localMember.getType());
    }

    // Store the configuration if it's already committed.
    if (raft.getCommitIndex() >= configuration.index()) {
      commitCurrentConfiguration();
    }
  }

  private void updateConfiguration(final Configuration configuration) {
    final var time = Instant.ofEpochMilli(configuration.time());

    final var membersInNewConfiguration = configuration.allMembers();

    // Update the local member's type if it has changed
    if (!membersInNewConfiguration.contains(localMember)) {
      localMember.update(Type.INACTIVE, time);
    }

    // Close and remove contexts which are not needed anymore
    final var membersToRemove =
        remoteMemberContexts.values().stream()
            .map(RaftMemberContext::getMember)
            .filter(Predicate.not(membersInNewConfiguration::contains))
            .toList();
    for (final var member : membersToRemove) {
      removeMemberContext(member);
    }

    // Add or update contexts for members in the new configuration
    for (final var member : membersInNewConfiguration) {
      updateMemberContext(member, time);
    }

    this.configuration = configuration;
  }

  private void removeMemberContext(final RaftMember member) {
    final var memberId = member.memberId();
    final var context = remoteMemberContexts.get(memberId);
    if (context != null) {
      context.close();
      remoteMemberContexts.remove(memberId);
      remoteActiveMembers.remove(context);
      replicationTargets.remove(context);
      hasRemoteActiveMembers = !remoteActiveMembers.isEmpty();
    }
  }

  private void updateMemberContext(final RaftMember member, final Instant time) {
    if (member.equals(localMember)) {
      localMember.update(member.getType(), time);
      return;
    }

    // Lookup context or create a new one.
    final var context =
        remoteMemberContexts.computeIfAbsent(
            member.memberId(),
            memberId ->
                new RaftMemberContext(
                    new DefaultRaftMember(memberId, member.getType(), time),
                    this,
                    raft.getMaxAppendsPerFollower()));

    // If the member type has changed, update the member type and reset its state.
    if (context.getMember().getType() != member.getType()) {
      context.getMember().update(member.getType(), time);
      context.resetState(raft.getLog());
    }

    if (member.getType() == Type.ACTIVE) {
      remoteActiveMembers.add(context);
      hasRemoteActiveMembers = true;
    } else if (remoteActiveMembers.remove(context)) {
      hasRemoteActiveMembers = !remoteActiveMembers.isEmpty();
    }

    if (member.getType() != Type.INACTIVE) {
      replicationTargets.add(context);
    }
  }

  /** Commit the current configuration to disk. */
  public void commitCurrentConfiguration() {
    // If the local stored configuration is older than the committed configuration, overwrite it.
    final var storedConfiguration = raft.getMetaStore().loadConfiguration();
    if (storedConfiguration == null || storedConfiguration.index() < configuration.index()) {
      raft.getMetaStore().storeConfiguration(configuration);
    }

    // Apply the configuration to the local server state.
    raft.transition(localMember.getType());
  }

  @Override
  public void close() {
    remoteMemberContexts.values().forEach(RaftMemberContext::close);
    localMember.close();
  }
}
