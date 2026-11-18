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
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the persistent state of the Raft cluster from the perspective of a single server. */
public final class RaftClusterContext implements RaftCluster, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(RaftClusterContext.class);

  private final RaftContext raft;
  private final DefaultRaftMember localMember;
  private final Map<MemberId, RaftMemberContext> remoteMemberContexts = new HashMap<>();
  private final Set<RaftMemberContext> replicationTargets = new HashSet<>();
  private final Set<RaftMemberContext> remoteActiveMembers = new HashSet<>();
  private boolean hasRemoteActiveMembers = false;
  // Volatile: the outbound protocol reads the fence list off the Raft thread, see isFenced
  private volatile Configuration configuration;
  // The configuration in the meta store, loaded on first use; see getStoredConfiguration
  private @Nullable Configuration storedConfiguration;
  private boolean storedConfigurationLoaded;

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
    // An untyped bootstrap makes every member a voter, the local member included even if it is not
    // part of the given members.
    final var members = new HashMap<MemberId, Type>();
    cluster.forEach(member -> members.put(member, Type.ACTIVE));
    members.put(localMember.memberId(), Type.ACTIVE);
    return bootstrap(members);
  }

  @Override
  public CompletableFuture<Void> bootstrap(final Map<MemberId, Type> cluster) {
    final var localType = cluster.get(localMember.memberId());
    if (localType == null || localType == Type.INACTIVE) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException(
              "Cannot bootstrap with members %s: the local member %s must be a member that is not %s"
                  .formatted(cluster, localMember.memberId(), Type.INACTIVE)));
    }
    if (!cluster.containsValue(Type.ACTIVE)) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException(
              "Cannot bootstrap with members %s: at least one member must be %s"
                  .formatted(cluster, Type.ACTIVE)));
    }
    final var members = Map.copyOf(cluster);
    final var bootstrapFuture = new CompletableFuture<Void>();
    raft.getThreadContext()
        .execute(
            () -> {
              // Start from the stored configuration, which reflects the newest committed
              // configuration, and apply any newer configuration entry from the log on top:
              // configurations take effect as soon as they are appended, so an appended but not
              // yet committed configuration must survive a restart. Only if neither source has a
              // configuration, configure the server with the user provided configuration.
              reloadStoredConfiguration();
              reloadConfigurationFromLog();
              if (configuration == null) {
                createInitialConfig(members);
              } else {
                // The incarnation of the local member is part of the configuration, so that a
                // restart keeps it.
                configuration.allMembers().stream()
                    .filter(member -> member.memberId().equals(localMember.memberId()))
                    .findAny()
                    .ifPresent(member -> localMember.setIncarnation(member.getIncarnation()));
                final var stored = raft.getMetaStore().loadConfiguration();
                if (stored != null && stored.isFenced(localMember)) {
                  raft.onFenced("it is on the fence list of its stored configuration");
                  bootstrapFuture.completeExceptionally(
                      new IllegalStateException(
                          "Cannot start, the local member %s with incarnation %d is fenced"
                              .formatted(localMember.memberId(), localMember.getIncarnation())));
                  return;
                }
              }
              raft.transition(localMember.getType());
              bootstrapFuture.complete(null);
            });

    return bootstrapFuture;
  }

  @Override
  public CompletableFuture<Void> startWithoutConfiguration(final long incarnation) {
    if (incarnation == 0 || incarnation == Long.MIN_VALUE) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException(
              "A member without data needs a new incarnation that is not 0 or Long.MIN_VALUE, but got %d"
                  .formatted(incarnation)));
    }
    final var startFuture = new CompletableFuture<Void>();
    raft.getThreadContext()
        .execute(
            () -> {
              reloadStoredConfiguration();
              reloadConfigurationFromLog();
              if (configuration != null) {
                startFuture.completeExceptionally(
                    new IllegalStateException(
                        "Cannot start %s without a configuration, it has the configuration %s; bootstrap with it instead"
                            .formatted(localMember.memberId(), configuration)));
                return;
              }
              localMember.setIncarnation(incarnation);
              localMember.update(Type.INACTIVE, Instant.now());
              raft.transition(Type.INACTIVE);
              startFuture.complete(null);
            });
    return startFuture;
  }

  @Override
  public CompletableFuture<Void> join(final Type type, final Collection<MemberId> cluster) {
    return new ReconfigurationHelper(raft)
        .join(type, cluster)
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

  private void createInitialConfig(final Map<MemberId, Type> members) {
    localMember.setType(members.get(localMember.memberId()));

    final Set<RaftMember> initialMembers =
        members.entrySet().stream()
            .filter(m -> !m.getKey().equals(localMember.memberId()))
            .map(
                m ->
                    (RaftMember)
                        new DefaultRaftMember(
                            m.getKey(), m.getValue(), localMember.getLastUpdated()))
            .collect(Collectors.toSet());
    initialMembers.add(localMember);

    // Create a new configuration and store it on disk to ensure the cluster can fall back to the
    // configuration. The types are part of the stored configuration, so a restart keeps them.
    final var initialConfiguration =
        new Configuration(
            0,
            0,
            localMember.getLastUpdated().toEpochMilli(),
            initialMembers,
            List.of(),
            false,
            List.of(),
            0);
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
      final Function<RaftMemberContext, T> calculateMemberValue, final T absentValue) {
    if (configuration.requiresJointConsensus()) {
      final var oldMembers = configuration.oldMembers();
      final var newMembers = configuration.newMembers();

      final var oldQuorum = getQuorumFor(oldMembers, calculateMemberValue, absentValue);
      final var newQuorum = getQuorumFor(newMembers, calculateMemberValue, absentValue);
      if (oldQuorum.isPresent() && newQuorum.isPresent()) {
        return Optional.of(Comparators.min(oldQuorum.get(), newQuorum.get()));
      } else if (oldQuorum.isPresent()) {
        return oldQuorum;
      } else {
        return newQuorum;
      }
    }

    return getQuorumFor(configuration.newMembers(), calculateMemberValue, absentValue);
  }

  /**
   * @param absentValue the value of a voter that does not count: a voter whose incarnation is not
   *     the incarnation of the member context. In the joint phase of a replacement, the old
   *     incarnation of a broker is a voter of the old members, while the member context follows the
   *     new incarnation (see {@link Configuration#allMembers()}); the answers of the new
   *     incarnation must not count for the old one (R17, Q15). It must not be higher than any value
   *     of a member.
   */
  private <T extends Comparable<T>> Optional<T> getQuorumFor(
      final Collection<RaftMember> members,
      final Function<RaftMemberContext, T> calculateMemberValue,
      final T absentValue) {
    // Under joint consensus, `remoteMemberContexts` is not authoritative, because it contains
    // entries from old and new configurations. So filter for members that actually are in the
    // configuration.
    final var activeMembers =
        members.stream().filter(member -> member.getType() == Type.ACTIVE).toList();
    final var includeLocalMemberInQuorum =
        activeMembers.stream().anyMatch(member -> member.memberId().equals(localMember.memberId()));
    final var values =
        activeMembers.stream()
            .filter(member -> !member.memberId().equals(localMember.memberId()))
            .map(
                member -> {
                  final var context = remoteMemberContexts.get(member.memberId());
                  return context == null
                          || context.getMember().getIncarnation() != member.getIncarnation()
                      ? absentValue
                      : calculateMemberValue.apply(context);
                })
            .collect(Collectors.toCollection(ArrayList::new));

    if (values.isEmpty()) {
      return Optional.empty();
    }
    values.sort(Comparator.<T>naturalOrder().reversed());

    final var remoteActiveMembers = values.size();
    final int includeLocalMember = includeLocalMemberInQuorum ? 1 : 0;
    final var totalActiveMembers = remoteActiveMembers + includeLocalMember;
    final var quorum = (totalActiveMembers / 2) + 1;

    final var remoteQuorumIndex = quorum - 1 - includeLocalMember;
    return Optional.of(values.get(remoteQuorumIndex));
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
   * @return true if the given member is a voter ({@link Type#ACTIVE}) in the new members of the
   *     current configuration
   */
  public boolean isVoter(final MemberId memberId) {
    return configuration != null
        && configuration.newMembers().stream()
            .anyMatch(m -> m.memberId().equals(memberId) && m.getType() == Type.ACTIVE);
  }

  /**
   * @return true if the given member is part of the cluster, false otherwise
   */
  public boolean isMember(final MemberId memberId) {
    // Configuration-derived rather than context-derived: remoteMemberContexts keeps a removed
    // member alive as a replication target until the removal commits (see updateConfiguration),
    // but such a member must not become a valid leadership-transfer target (its only caller,
    // LeaderRole.onTransfer) during that window - membership-blind votes could actually elect it
    // into a configuration that no longer includes it.
    return localMember.memberId().equals(memberId)
        || (configuration != null && configuration.hasMember(memberId));
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

  /**
   * Returns the term that identifies the current configuration in a {@code ReconfigureRequest},
   * which {@code LeaderRole#onReconfigure} validates against the leader's own view of it.
   *
   * <p>{@link Configuration#term()} alone is ambiguous: a member that appended or recovered the
   * configuration entry stores the term the entry was appended in, while a member that learned the
   * configuration from a {@code ConfigureRequest} stores the leadership term it was disseminated
   * under - {@code ConfigureRequest} carries the leader's then-current term, which is also what
   * tracks the leader. Whenever an election happened between appending and disseminating a
   * configuration, the two differ, and a requester and a leader that learned the same configuration
   * in different ways would never agree on its term, so every reconfiguration would be rejected as
   * stale forever. Both sides therefore derive the term from the configuration entry in their own
   * log, which is unambiguous, and only fall back to the stored term when that entry is not
   * available - for the initial configuration, which has no entry, or before the entry arrived.
   * Such a mismatch is still possible, but it is transient and the rejected request is safe to
   * retry.
   */
  public long getConfigurationTerm() {
    final var currentConfiguration = configuration;
    try (final var reader = raft.getLog().openUncommittedReader()) {
      reader.seek(currentConfiguration.index());
      if (reader.hasNext()) {
        final var entry = reader.next();
        if (entry.index() == currentConfiguration.index()
            && entry.entry() instanceof ConfigurationEntry) {
          return entry.term();
        }
      }
    } catch (final Exception e) {
      LOGGER.warn(
          "Failed to read the term of the configuration entry at index {}, falling back to the stored term {}",
          currentConfiguration.index(),
          currentConfiguration.term(),
          e);
    }
    return currentConfiguration.term();
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
                  .filter(member -> member.getType() == Type.ACTIVE)
                  .map(RaftMember::memberId)
                  .collect(Collectors.toSet()),
              configuration.newMembers().stream()
                  .filter(member -> member.getType() == Type.ACTIVE)
                  .map(RaftMember::memberId)
                  .collect(Collectors.toSet()));
    } else {
      quorum =
          new SimpleVoteQuorum(
              callback,
              configuration.newMembers().stream()
                  .filter(member -> member.getType() == Type.ACTIVE)
                  .map(RaftMember::memberId)
                  .collect(Collectors.toSet()));
    }
    quorum.succeed(localMember.memberId());
    return quorum;
  }

  /**
   * Applies the newest configuration entry from the log if it is newer than the current
   * configuration. Configurations take effect as soon as they are appended, so the newest
   * configuration entry in the log is authoritative, whether it is committed or not.
   */
  public void reloadConfigurationFromLog() {
    raft.checkThread();
    final var currentConfiguration = configuration;
    // Entries covered by the current configuration or the commit index can be skipped: the
    // configuration guard ignores older indexes anyway, and a committed configuration entry is
    // always persisted before the commit index that covers it, so it is already reflected in the
    // current configuration. Without a current configuration (the join path and members without a
    // meta store), the whole log has to be searched.
    final var firstIndexToSearch =
        currentConfiguration == null
            ? 0
            : Math.max(currentConfiguration.index(), raft.getCommitIndex()) + 1;
    newestConfigurationEntry(firstIndexToSearch, Long.MAX_VALUE)
        .ifPresent(
            newest -> {
              LOGGER.info(
                  "Reloading configuration {} from log entry at index {}", newest, newest.index());
              configure(newest);
            });
  }

  /**
   * Applies the stored committed configuration if there is no configuration yet, or if the current
   * one is newer and so not committed. The latter happens when the whole log was reset, for example
   * to install a snapshot: no entry remains, so every configuration newer than the stored one has
   * lost its entry, whatever its index is compared to the new first index of the log.
   */
  public void reloadStoredConfiguration() {
    raft.checkThread();
    final var storedConfiguration = raft.getMetaStore().loadConfiguration();
    if (storedConfiguration == null) {
      return;
    }
    if (configuration == null) {
      updateConfiguration(storedConfiguration);
    } else if (configuration.index() > storedConfiguration.index()) {
      revertConfiguration(storedConfiguration);
    }
  }

  /**
   * Drops the current configuration if the log entry it was taken from is gone, for example after
   * the log was truncated. Configurations take effect as soon as they are appended, so a
   * configuration without its entry must be dropped. Otherwise this member keeps a configuration
   * that no log contains, commits it once the commit index passes its index and reports it to the
   * leader, which ignores it and never repairs it.
   *
   * @param lastRetainedIndex the index of the last entry remaining in the log
   */
  public void dropConfigurationsWithoutLogEntry(final long lastRetainedIndex) {
    raft.checkThread();
    if (configuration == null || configuration.index() <= lastRetainedIndex) {
      return;
    }

    // Committed configurations are never truncated, so the stored configuration is always a valid
    // fallback. A newer configuration entry that is still in the log takes precedence.
    final var storedConfiguration = raft.getMetaStore().loadConfiguration();
    // A configuration is stored when it commits, so committed entries do not need to be searched.
    final var firstIndexToSearch = raft.getCommitIndex() + 1;
    final var target =
        firstIndexToSearch <= lastRetainedIndex && !raft.getLog().isEmpty()
            ? newestConfigurationEntry(firstIndexToSearch, lastRetainedIndex)
                .orElse(storedConfiguration)
            : storedConfiguration;
    if (target == null) {
      LOGGER.warn(
          "Cannot drop configuration {}, no stored or logged configuration to fall back to",
          configuration);
      return;
    }
    revertConfiguration(target);
  }

  /**
   * Returns the newest configuration entry in the log between the given indexes, both inclusive. An
   * index of 0 as first index searches from the start of the log.
   */
  private Optional<Configuration> newestConfigurationEntry(
      final long firstIndex, final long lastIndex) {
    // The reader needs to be uncommitted because the configuration entry might not be committed yet
    // on this node, but committed on the leader already.
    try (final var reader = raft.getLog().openUncommittedReader()) {
      if (firstIndex > 0) {
        reader.seek(firstIndex);
      }
      Configuration newest = null;
      while (reader.hasNext()) {
        final var entry = reader.next();
        if (entry.index() > lastIndex) {
          break;
        }
        if (entry.entry() instanceof final ConfigurationEntry configurationEntry) {
          newest =
              new Configuration(
                  entry.index(),
                  entry.term(),
                  configurationEntry.timestamp(),
                  configurationEntry.newMembers(),
                  configurationEntry.oldMembers(),
                  false,
                  configurationEntry.fenced(),
                  configurationEntry.membershipVersion());
        }
      }
      return Optional.ofNullable(newest);
    }
  }

  private void revertConfiguration(final Configuration target) {
    LOGGER.info("Reverting configuration {} to {}", configuration, target);
    final var initialType = localMember.getType();
    updateConfiguration(target);
    if (raft.getCommitIndex() >= target.index()) {
      // Prunes the members of the dropped configuration, applies the local member type and stores
      // the configuration if it is newer than the stored one.
      commitCurrentConfiguration();
    } else {
      // The members of the dropped configuration were kept as replication targets until the outcome
      // of a configuration is decided. For the dropped one it is decided now.
      pruneMembersNotInConfiguration();
      if (initialType != localMember.getType()) {
        raft.transition(localMember.getType());
      }
    }
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
      // Promotions transition at append, unlike demotions/removals which transition at commit
      // (see commitCurrentConfiguration). Delaying PASSIVE->ACTIVE to commit has a liveness
      // counterexample: if the promoted member holds the longest log and the leader dies before
      // C-new commits, C-new's majority may require the promoted member's vote, and the
      // log-up-to-date check (dissertation §4.1) makes every other member unelectable - so it
      // must be able to stand for election under its appended configuration. Likewise, deferring
      // INACTIVE->PASSIVE would stall log catch-up, since only PassiveRole accepts appends.
      raft.transition(localMember.getType());
    }

    // Store the configuration if it's already committed. A forced configuration has no log entry
    // that could commit it, so it is stored right away. Otherwise a restart forgets a force that
    // already succeeded.
    if (configuration.force() || raft.getCommitIndex() >= configuration.index()) {
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

    final var membersToRemove =
        remoteMemberContexts.values().stream()
            .map(RaftMemberContext::getMember)
            .filter(Predicate.not(membersInNewConfiguration::contains))
            .toList();
    if (configuration.force()) {
      // A forced configuration is authoritative without waiting for a commit: the orchestrator
      // that issues it (ReconfigurationHelper.triggerForceConfigure) wins its election before this
      // server would ever reach commitCurrentConfiguration, so deferring pruning to commit would
      // keep presumably-dead members as replication targets of the new leader.
      for (final var member : membersToRemove) {
        removeMemberContext(member);
      }
    } else {
      // raft.pdf §6: servers removed by a reconfiguration may only be shut down once the new
      // configuration commits. Keep their contexts and replication targets alive here - they are
      // fully pruned in commitCurrentConfiguration() once the outcome is decided - so the leader
      // keeps replicating and heartbeating to a removed member while its fate is still undecided,
      // keeping it reachable and its election timer quiet. Only the active-voting bookkeeping,
      // which election/quorum logic reads at append time, is updated immediately.
      for (final var member : membersToRemove) {
        final var context = remoteMemberContexts.get(member.memberId());
        if (context != null && remoteActiveMembers.remove(context)) {
          hasRemoteActiveMembers = !remoteActiveMembers.isEmpty();
        }
      }
    }

    // Add or update contexts for members in the new configuration
    for (final var member : membersInNewConfiguration) {
      updateMemberContext(member, time);
    }
    this.configuration = configuration;
    raft.notifyConfigurationListeners(
        configuration, configuration.force() || raft.getCommitIndex() >= configuration.index());
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
                    new DefaultRaftMember(
                        memberId, member.getType(), time, member.getIncarnation()),
                    this,
                    raft.getMaxAppendsPerFollower()));

    // A member that joined again with a new incarnation has a new, empty log.
    if (context.getMember().getIncarnation() != member.getIncarnation()) {
      context.getMember().setIncarnation(member.getIncarnation());
      context.resetState(raft.getLog());
    }

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

  /**
   * @return the committed configuration that is stored in the meta store, without reading the disk
   *     after the first call; null if none is stored
   */
  public @Nullable Configuration getStoredConfiguration() {
    raft.checkThread();
    if (!storedConfigurationLoaded) {
      storedConfiguration = raft.getMetaStore().loadConfiguration();
      storedConfigurationLoaded = true;
    }
    return storedConfiguration;
  }

  /** Commit the current configuration to disk. */
  public void commitCurrentConfiguration() {
    // If the local stored configuration is older than the committed configuration, overwrite it.
    final var storedConfiguration = raft.getMetaStore().loadConfiguration();
    if (storedConfiguration == null || storedConfiguration.index() < configuration.index()) {
      raft.getMetaStore().storeConfiguration(configuration);
      this.storedConfiguration = configuration;
      storedConfigurationLoaded = true;
    }

    // Members kept alive as replication targets while the configuration was appended but not yet
    // committed (see updateConfiguration) can finally be shut down now (raft.pdf §6). This is
    // idempotent reconciliation: a forced configuration is already pruned by the time this runs,
    // and every caller (the commit-index hook, an already-committed or forced configure(), the
    // initial config, PassiveRole/InactiveRole onConfigure) may invoke this repeatedly for the same
    // configuration.
    pruneMembersNotInConfiguration();

    raft.notifyConfigurationListeners(configuration, true);

    // Apply the configuration to the local server state.
    raft.transition(localMember.getType());

    if (configuration.isFenced(localMember)) {
      raft.onFenced(
          "it is on the fence list of the committed configuration " + configuration.index());
    }
  }

  /**
   * @return true if the given member, with the incarnation it has in the configuration, is on the
   *     fence list of the current configuration
   */
  public boolean isFenced(final RaftMember member) {
    final var current = configuration;
    return current != null && current.isFenced(member);
  }

  /**
   * @return true if the given member id and incarnation are on the fence list of the current
   *     configuration. Safe to call off the Raft thread.
   */
  public boolean isFenced(final MemberId member, final long incarnation) {
    final var current = configuration;
    return current != null && current.isFenced(member, incarnation);
  }

  private void pruneMembersNotInConfiguration() {
    final var membersInConfiguration = configuration.allMembers();
    final var membersToRemove =
        remoteMemberContexts.values().stream()
            .map(RaftMemberContext::getMember)
            .filter(Predicate.not(membersInConfiguration::contains))
            .toList();
    for (final var member : membersToRemove) {
      removeMemberContext(member);
    }
  }

  @Override
  public void close() {
    remoteMemberContexts.values().forEach(RaftMemberContext::close);
    localMember.close();
  }
}
