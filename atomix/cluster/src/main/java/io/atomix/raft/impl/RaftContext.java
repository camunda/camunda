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
package io.atomix.raft.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.atomix.utils.concurrent.Threads.namedThreads;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoRemoteHandler;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.atomix.raft.ElectionTimer;
import io.atomix.raft.RaftApplicationEntryCommittedPositionListener;
import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftException.ProtocolException;
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.RaftThreadContextFactory;
import io.atomix.raft.SnapshotReplicationListener;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.cluster.impl.RaftClusterContext;
import io.atomix.raft.metrics.RaftReplicationMetrics;
import io.atomix.raft.metrics.RaftRoleMetrics;
import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.partition.RaftElectionConfig;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.ProtocolVersionHandler;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.RaftServerProtocol;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.roles.ActiveRole;
import io.atomix.raft.roles.CandidateRole;
import io.atomix.raft.roles.FollowerRole;
import io.atomix.raft.roles.InactiveRole;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.roles.PassiveRole;
import io.atomix.raft.roles.PromotableRole;
import io.atomix.raft.roles.RaftRole;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.StorageException;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.system.MetaStore;
import io.atomix.raft.utils.StateUtil;
import io.atomix.raft.zeebe.EntryValidator;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.net.ConnectException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * Manages the volatile state and state transitions of a Raft server.
 *
 * <p>This class is the primary vehicle for managing the state of a server. All state that is shared
 * across roles (i.e. follower, candidate, leader) is stored in the cluster state. This includes
 * Raft-specific state like the current leader and term, the log, and the cluster configuration.
 */
public class RaftContext implements AutoCloseable, HealthMonitorable {

  /**
   * Configuration index returned when no configuration is available, i.e. configuration is null .
   */
  private static final long NO_CONFIGURATION_INDEX = -1L;

  protected final String name;
  protected final ThreadContext threadContext;
  protected final ClusterMembershipService membershipService;
  protected final RaftClusterContext cluster;
  protected final RaftServerProtocol protocol;
  protected final RaftStorage storage;
  private final Logger log;
  private final RaftElectionConfig electionConfig;
  private final Set<RaftRoleChangeListener> roleChangeListeners = new CopyOnWriteArraySet<>();
  private final Set<Consumer<State>> stateChangeListeners = new CopyOnWriteArraySet<>();
  private final Set<Consumer<RaftMember>> electionListeners = new CopyOnWriteArraySet<>();
  private final Set<RaftCommitListener> commitListeners = new CopyOnWriteArraySet<>();
  private final Set<RaftApplicationEntryCommittedPositionListener> committedEntryListeners =
      new CopyOnWriteArraySet<>();
  private final Set<SnapshotReplicationListener> snapshotReplicationListeners =
      new CopyOnWriteArraySet<>();
  private final Set<FailureListener> failureListeners = new CopyOnWriteArraySet<>();
  private final RaftRoleMetrics raftRoleMetrics;
  private final RaftReplicationMetrics replicationMetrics;
  private final MetaStore meta;
  private final RaftLog raftLog;
  private final ReceivableSnapshotStore persistedSnapshotStore;
  private final LogCompactor logCompactor;
  private volatile State state = State.ACTIVE;
  // Some fields are read by external threads. To ensure thread-safe access, we can use the lock for
  // synchronizing write and reads on such fields.
  private final Object externalAccessLock = new Object();
  private RaftRole role = new InactiveRole(this);
  private volatile MemberId leader;
  private volatile long term;
  private MemberId lastVotedFor;
  private long commitIndex;
  private long firstCommitIndex;
  private volatile boolean started;
  private EntryValidator entryValidator;
  // Used for randomizing election timeout
  private final Random random;
  private PersistedSnapshot currentSnapshot;

  private boolean ongoingTransition = false;
  // Keeps track of snapshot replication to notify new listeners about missed events
  private MissedSnapshotReplicationEvents missedSnapshotReplicationEvents =
      MissedSnapshotReplicationEvents.NONE;

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport health = HealthReport.healthy(this);

  private long lastHeartbeat;
  private final RaftPartitionConfig partitionConfig;
  private final int partitionId;

  public RaftContext(
      final String name,
      final int partitionId,
      final MemberId localMemberId,
      final ClusterMembershipService membershipService,
      final RaftServerProtocol protocol,
      final RaftStorage storage,
      final RaftThreadContextFactory threadContextFactory,
      final Supplier<Random> randomFactory,
      final RaftElectionConfig electionConfig,
      final RaftPartitionConfig partitionConfig) {
    this.name = checkNotNull(name, "name cannot be null");
    this.membershipService = checkNotNull(membershipService, "membershipService cannot be null");
    this.protocol = checkNotNull(protocol, "protocol cannot be null");
    this.storage = checkNotNull(storage, "storage cannot be null");
    random = randomFactory.get();
    this.partitionId = partitionId;

    raftRoleMetrics = new RaftRoleMetrics(name);

    log =
        ContextualLoggerFactory.getLogger(
            getClass(), LoggerContext.builder(RaftServer.class).addValue(name).build());
    this.electionConfig = electionConfig;
    if (electionConfig.isPriorityElectionEnabled()) {
      log.debug(
          "Priority election is enabled with target priority {} and node priority {}",
          electionConfig.getInitialTargetPriority(),
          electionConfig.getNodePriority());
    }

    // Lock the storage directory.
    if (!storage.lock(localMemberId.id())) {
      throw new StorageException(
          "Failed to acquire storage lock; ensure each Raft server is configured with a distinct storage directory");
    }

    threadContext =
        createThreadContext("raft-server", partitionId, threadContextFactory, localMemberId.id());

    // Open the metadata store.
    meta = storage.openMetaStore();

    // Load the current term and last vote from disk.
    term = meta.loadTerm();
    lastVotedFor = meta.loadVote();

    // Construct the core log, reader, writer, and compactor.
    raftLog =
        storage.openLog(
            meta,
            () ->
                createThreadContext(
                    "raft-log", partitionId, threadContextFactory, localMemberId.id()));

    // Open the snapshot store.
    persistedSnapshotStore = storage.getPersistedSnapshotStore();
    persistedSnapshotStore.addSnapshotListener(this::onNewPersistedSnapshot);
    // Update the current snapshot because the listener only notifies when a new snapshot is
    // created.
    persistedSnapshotStore
        .getLatestSnapshot()
        .ifPresent(persistedSnapshot -> currentSnapshot = persistedSnapshot);
    StateUtil.verifySnapshotLogConsistent(
        partitionId,
        getCurrentSnapshotIndex(),
        raftLog.getFirstIndex(),
        raftLog.isEmpty(),
        raftLog::reset,
        log);

    logCompactor =
        new LogCompactor(
            threadContext,
            raftLog,
            partitionConfig.getPreferSnapshotReplicationThreshold(),
            new RaftServiceMetrics(name),
            ContextualLoggerFactory.getLogger(
                LogCompactor.class, LoggerContext.builder(getClass()).addValue(name).build()));

    this.partitionConfig = partitionConfig;
    cluster = new RaftClusterContext(localMemberId, this);

    replicationMetrics = new RaftReplicationMetrics(name);
    replicationMetrics.setAppendIndex(raftLog.getLastIndex());
    lastHeartbeat = System.currentTimeMillis();

    // Register protocol listeners.
    registerHandlers(protocol);
    started = true;

    addCommitListener(new AwaitingReadyCommitListener());

    if (!raftLog.isEmpty() && term == 0) {
      // This will only happen when metastore is empty because the node has just restored from a
      // backup. Backup only contains the logs. Other case, where this can happen is when the meta
      // file was manually deleted to recover from an unexpected bug.
      // In both cases, we should not restart the term from 0 because the assumption in raft is that
      // the term always increase. After restore, it is safe to restart the term at the last log's
      // term. During the first election, the term will be incremented by 1.
      // In the second case, it is possible that the actual term is higher. But it is still safe to
      // set it to last log's term because the actual term will be set when this node gets the first
      // message from other healthy replicas.
      setTerm(raftLog.getLastEntry().term());
    }
  }

  private ThreadContext createThreadContext(
      final String name,
      final int partitionId,
      final RaftThreadContextFactory threadContextFactory,
      final String localMemberId) {
    final var context =
        threadContextFactory.createContext(
            namedThreads("%s-%s-%d".formatted(name, localMemberId, partitionId), log),
            this::onUncaughtException);
    // in order to set the partition id once in the raft thread
    context.execute(
        () -> {
          MDC.put("partitionId", String.valueOf(partitionId));
          MDC.put("actor-name", name + "-" + partitionId);
          MDC.put("actor-scheduler", "Broker-" + localMemberId);
        });
    return context;
  }

  private void onNewPersistedSnapshot(final PersistedSnapshot persistedSnapshot) {
    threadContext.execute(this::updateCurrentSnapshot);
  }

  private void onUncaughtException(final Throwable error) {
    log.error("An uncaught exception occurred, transition to inactive role", error);
    try {
      // to prevent further operations submitted to the threadcontext to execute
      transition(Role.INACTIVE);
    } catch (final Exception e) {
      log.error("An error occurred when transitioning to inactive, closing the raft context", e);
      close();
    }

    notifyFailureListeners(error);
  }

  private void notifyFailureListeners(final Throwable error) {
    try {
      if (error instanceof UnrecoverableException) {
        health = HealthReport.dead(this).withIssue(error);
        failureListeners.forEach((l) -> l.onUnrecoverableFailure(health));
      } else {
        health = HealthReport.unhealthy(this).withIssue(error);
        failureListeners.forEach((l) -> l.onFailure(health));
      }
    } catch (final Exception e) {
      log.error("Could not notify failure listeners", e);
    }
  }

  /** Registers server handlers on the configured protocol. */
  private void registerHandlers(final RaftServerProtocol protocol) {
    protocol.registerConfigureHandler(request -> runOnContext(() -> role.onConfigure(request)));
    protocol.registerInstallHandler(request -> runOnContext(() -> role.onInstall(request)));
    protocol.registerReconfigureHandler(request -> runOnContext(() -> role.onReconfigure(request)));
    protocol.registerJoinHandler(request -> runOnContext(() -> role.onJoin(request)));
    protocol.registerLeaveHandler(request -> runOnContext(() -> role.onLeave(request)));
    protocol.registerTransferHandler(request -> runOnContext(() -> role.onTransfer(request)));
    protocol.registerAppendV1Handler(
        request -> runOnContext(() -> role.onAppend(ProtocolVersionHandler.transform(request))));
    protocol.registerAppendV2Handler(
        request -> runOnContext(() -> role.onAppend(ProtocolVersionHandler.transform(request))));
    protocol.registerPollHandler(request -> runOnContext(() -> role.onPoll(request)));
    protocol.registerVoteHandler(request -> runOnContext(() -> role.onVote(request)));
  }

  private <R extends RaftResponse> CompletableFuture<R> runOnContext(
      final Supplier<CompletableFuture<R>> function) {
    final CompletableFuture<R> future = new CompletableFuture<>();
    threadContext.execute(
        () ->
            function
                .get()
                .whenComplete(
                    (response, error) -> {
                      if (error == null) {
                        future.complete(response);
                      } else {
                        future.completeExceptionally(error);
                      }
                    }));
    return future;
  }

  public int getMaxAppendBatchSize() {
    return partitionConfig.getMaxAppendBatchSize();
  }

  public int getMaxAppendsPerFollower() {
    return partitionConfig.getMaxAppendsPerFollower();
  }

  /**
   * Adds a role change listener. If there isn't currently a transition ongoing the listener is
   * called immediately after adding the listener.
   *
   * @param listener The role change listener.
   */
  public void addRoleChangeListener(final RaftRoleChangeListener listener) {
    threadContext.execute(
        () -> {
          roleChangeListeners.add(listener);

          // When a transition is currently ongoing, then the given
          // listener will be called when the transition completes.
          if (!ongoingTransition) {
            // Otherwise, the listener will called directly for the last
            // completed transition.
            listener.onNewRole(getRole(), getTerm());
          }
        });
  }

  /**
   * Removes a role change listener.
   *
   * @param listener The role change listener.
   */
  public void removeRoleChangeListener(final RaftRoleChangeListener listener) {
    roleChangeListeners.remove(listener);
  }

  /**
   * Adds a state change listener.
   *
   * @param listener The state change listener.
   */
  public void addStateChangeListener(final Consumer<State> listener) {
    listener.accept(state);
    stateChangeListeners.add(listener);
  }

  /**
   * Removes a state change listener.
   *
   * @param listener The state change listener.
   */
  public void removeStateChangeListener(final Consumer<State> listener) {
    stateChangeListeners.remove(listener);
  }

  /**
   * Adds a new commit listener, which will be notified whenever the commit position changes. Note
   * that it will be called on the Raft thread, and as such should not perform any heavy
   * computation.
   *
   * @param commitListener the listener to add
   */
  public void addCommitListener(final RaftCommitListener commitListener) {
    commitListeners.add(commitListener);
  }

  /**
   * Removes registered commit listener
   *
   * @param commitListener the listener to remove
   */
  public void removeCommitListener(final RaftCommitListener commitListener) {
    commitListeners.remove(commitListener);
  }

  /**
   * Adds a new committed entry listener, which will be notified when the Leader commits a new
   * entry. If RAFT runs currently in a Follower role this listeners are not called.
   *
   * <p>Note that it will be called on the Raft thread, and as such should not perform any heavy
   * computation.
   *
   * @param raftApplicationEntryCommittedPositionListener the listener to add
   */
  public void addCommittedEntryListener(
      final RaftApplicationEntryCommittedPositionListener
          raftApplicationEntryCommittedPositionListener) {
    committedEntryListeners.add(raftApplicationEntryCommittedPositionListener);
  }

  /**
   * Removes registered committedEntryListener
   *
   * @param raftApplicationEntryCommittedPositionListener the listener to remove
   */
  public void removeCommittedEntryListener(
      final RaftApplicationEntryCommittedPositionListener
          raftApplicationEntryCommittedPositionListener) {
    committedEntryListeners.remove(raftApplicationEntryCommittedPositionListener);
  }

  /**
   * Notifies all listeners of the latest entry.
   *
   * @param lastCommitIndex index of the most recently committed entry
   */
  public void notifyCommitListeners(final long lastCommitIndex) {
    commitListeners.forEach(listener -> listener.onCommit(lastCommitIndex));
  }

  /**
   * Notifies all listeners of the latest entry.
   *
   * @param committedEntry the most recently committed entry
   */
  public void notifyApplicationEntryCommittedPositionListeners(final long committedEntry) {
    committedEntryListeners.forEach(listener -> listener.onCommit(committedEntry));
  }

  /**
   * Sets the commit index.
   *
   * @param commitIndex The commit index.
   * @return the previous commit index
   */
  public long setCommitIndex(long commitIndex) {
    checkArgument(commitIndex >= 0, "commitIndex must be positive");

    // Do not set the commitIndex to be greater than the events we persisted.
    // In case of a heartbeat, the leader sends its commitIndex which may be greater than what's
    // persisted in case we failed to persist a previous AppendEntries
    commitIndex = Math.min(commitIndex, raftLog.getLastIndex());

    final long previousCommitIndex = this.commitIndex;
    if (commitIndex > previousCommitIndex) {
      this.commitIndex = commitIndex;
      raftLog.setCommitIndex(Math.min(commitIndex, raftLog.getLastIndex()));
      if (isLeader()) {
        // leader counts itself in quorum, so in order to commit the leader must persist
        raftLog.flush();
      }
<<<<<<< HEAD:atomix/cluster/src/main/java/io/atomix/raft/impl/RaftContext.java
      final long configurationIndex = cluster.getConfiguration().index();
      if (configurationIndex > previousCommitIndex && configurationIndex <= commitIndex) {
        cluster.commitCurrentConfiguration();
=======
      raftLog.setCommitIndex(commitIndex);
      this.commitIndex = commitIndex;
      meta.storeCommitIndex(commitIndex);
      final var clusterConfig = cluster.getConfiguration();
      if (clusterConfig != null) {
        final long configurationIndex = clusterConfig.index();
        if (configurationIndex > previousCommitIndex && configurationIndex <= commitIndex) {
          cluster.commitCurrentConfiguration();
        }
>>>>>>> b494f4b4 (refactor: merge checks on commitIndex/raftLog.getLastIndex()):zeebe/atomix/cluster/src/main/java/io/atomix/raft/impl/RaftContext.java
      }
      replicationMetrics.setCommitIndex(commitIndex);
      notifyCommitListeners(commitIndex);
    }
    return previousCommitIndex;
  }

  /**
   * Adds a new snapshot replication listener, which will be notified before and after a new
   * snapshot is received from a leader. Note that it will be called on the Raft thread, and hence
   * should not perform any heavy computation.
   *
   * @param snapshotReplicationListener the listener to add
   */
  public void addSnapshotReplicationListener(
      final SnapshotReplicationListener snapshotReplicationListener) {
    threadContext.execute(
        () -> {
          snapshotReplicationListeners.add(snapshotReplicationListener);
          // Notify listener immediately if it registered during an ongoing replication.
          // This is to prevent missing necessary state transitions.
          if (role.role() == Role.FOLLOWER) {
            switch (missedSnapshotReplicationEvents) {
              case STARTED -> snapshotReplicationListener.onSnapshotReplicationStarted();
              case COMPLETED -> {
                snapshotReplicationListener.onSnapshotReplicationStarted();
                snapshotReplicationListener.onSnapshotReplicationCompleted(term);
              }
              default -> {}
            }
          }
        });
  }

  /**
   * Removes registered snapshot replication listener
   *
   * @param snapshotReplicationListener the listener to remove
   */
  public void removeSnapshotReplicationListener(
      final SnapshotReplicationListener snapshotReplicationListener) {
    threadContext.execute(() -> snapshotReplicationListeners.remove(snapshotReplicationListener));
  }

  public void notifySnapshotReplicationStarted() {
    threadContext.execute(
        () -> {
          missedSnapshotReplicationEvents = MissedSnapshotReplicationEvents.STARTED;
          snapshotReplicationListeners.forEach(
              SnapshotReplicationListener::onSnapshotReplicationStarted);
        });
  }

  public void notifySnapshotReplicationCompleted() {
    threadContext.execute(
        () -> {
          snapshotReplicationListeners.forEach(l -> l.onSnapshotReplicationCompleted(term));
          missedSnapshotReplicationEvents = MissedSnapshotReplicationEvents.COMPLETED;
        });
  }

  /**
   * Ensures everything written to the log until this point, is flushed to disk. If default raft
   * flush is enabled, then this will not flush because the logs are flushed when necessary to
   * achieve expected consistency guarantees.
   *
   * @return a future to be completed once the log is flushed to disk
   */
  public CompletableFuture<Void> flushLog() {
    // If flush operations are synchronous on the Raft thread, then the log is guaranteed to be
    // flushed by before committing. Hence, there is no need to flush them again here. This is an
    // optimization to ensure we are not unnecessarily blocking raft thread to do an i/o.
    if (raftLog.flushesDirectly()) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.runAsync(raftLog::forceFlush, threadContext);
  }

  /** Attempts to become the leader. */
  public CompletableFuture<Void> anoint() {
    if (role.role() == Role.LEADER) {
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<Void> future = new CompletableFuture<>();

    threadContext.execute(
        () -> {
          // Register a leader election listener to wait for the election of this node.
          final Consumer<RaftMember> electionListener =
              new Consumer<>() {
                @Override
                public void accept(final RaftMember member) {
                  if (member.memberId().equals(cluster.getLocalMember().memberId())) {
                    future.complete(null);
                  } else {
                    future.completeExceptionally(
                        new ProtocolException("Failed to transfer leadership"));
                  }
                  removeLeaderElectionListener(this);
                }
              };
          addLeaderElectionListener(electionListener);

          // If a leader already exists, request a leadership transfer from it. Otherwise,
          // transition to the candidate
          // state and attempt to get elected.
          final RaftMember member = getCluster().getLocalMember();
          final RaftMember leader = getLeader();
          if (leader != null) {
            protocol
                .transfer(
                    leader.memberId(),
                    TransferRequest.builder().withMember(member.memberId()).build())
                .whenCompleteAsync(
                    (response, error) -> {
                      if (error != null) {
                        future.completeExceptionally(error);
                      } else if (response.status() == Status.ERROR) {
                        future.completeExceptionally(response.error().createException());
                      } else {
                        transition(Role.CANDIDATE);
                      }
                    },
                    threadContext);
          } else {
            transition(Role.CANDIDATE);
          }
        });
    return future;
  }

  public CompletableFuture<Void> join(final Collection<MemberId> clusterMembers) {
    final var result = new CompletableFuture<Void>();
    threadContext.execute(
        () -> {
          final var joining =
              new DefaultRaftMember(
                  cluster.getLocalMember().memberId(), Type.ACTIVE, Instant.now());
          final var assistingMembers =
              clusterMembers.stream()
                  .filter(memberId -> !memberId.equals(joining.memberId()))
                  .collect(Collectors.toCollection(LinkedBlockingQueue::new));
          if (assistingMembers.isEmpty()) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Cannot join cluster, because there are no other members in the cluster."));
            return;
          }
          joinWithRetry(joining, assistingMembers, result);
        });
    return result;
  }

  /**
   * Repeatedly tries to join the cluster until it succeeds or there are no more members to try.
   * When sending a join request to an assisting member fails because the member is currently not
   * known, or it is known but not ready to receive join request, try again with a different
   * assisting member.
   *
   * <p>Retrying helps in cases where the cluster is in flux and not all members are online and
   * ready.
   *
   * @param joining the new member joining
   * @param assistingMembers a queue of members that we will send a join request to.
   * @param result a future to complete when joining succeeds or fails
   */
  private void joinWithRetry(
      final RaftMember joining,
      final Queue<MemberId> assistingMembers,
      final CompletableFuture<Void> result) {
    threadContext.execute(
        () -> {
          final var receiver = assistingMembers.poll();
          if (receiver == null) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Sent join request to all known members, but all failed. No more members left."));
            return;
          }
          protocol
              .join(receiver, JoinRequest.builder().withJoiningMember(joining).build())
              .whenCompleteAsync(
                  (response, error) -> {
                    if (error != null) {
                      final var cause = error.getCause();
                      if (cause instanceof NoSuchMemberException
                          || cause instanceof NoRemoteHandler
                          || cause instanceof TimeoutException
                          || cause instanceof ConnectException) {
                        log.debug("Join request was not acknowledged, retrying", cause);
                        joinWithRetry(joining, assistingMembers, result);
                      } else {
                        log.error(
                            "Join request failed with an unexpected error, not retrying", error);
                        result.completeExceptionally(error);
                      }
                    } else if (response.status() == Status.OK) {
                      log.debug("Join request accepted");
                      result.complete(null);
                    } else if (response.error().type() == RaftError.Type.NO_LEADER
                        || response.error().type() == RaftError.Type.UNAVAILABLE) {
                      log.debug(
                          "Join request failed, retrying", response.error().createException());
                      joinWithRetry(joining, assistingMembers, result);
                    } else {
                      final var errorAsException = response.error().createException();
                      log.error("Join request rejected, not retrying", errorAsException);
                      result.completeExceptionally(errorAsException);
                    }
                  },
                  threadContext);
        });
  }

  public CompletableFuture<Void> leave() {
    final CompletableFuture<Void> future = new CompletableFuture<>();

    threadContext.execute(
        () -> {
          final var leaving = cluster.getLocalMember();
          final var receiver =
              Optional.ofNullable(leader)
                  .or(() -> cluster.getVotingMembers().stream().map(RaftMember::memberId).findAny())
                  .orElseThrow();
          protocol
              .leave(receiver, LeaveRequest.builder().withLeavingMember(leaving).build())
              .whenCompleteAsync(
                  (response, error) -> {
                    if (error != null) {
                      future.completeExceptionally(error);
                    } else if (response.status() == Status.OK) {
                      future.complete(null);
                      updateState(State.LEFT);
                    } else {
                      future.completeExceptionally(response.error().createException());
                    }
                  },
                  threadContext);
        });
    return future;
  }

  /**
   * Adds a leader election listener.
   *
   * @param listener The leader election listener.
   */
  public void addLeaderElectionListener(final Consumer<RaftMember> listener) {
    electionListeners.add(listener);
  }

  /**
   * Removes a leader election listener.
   *
   * @param listener The leader election listener.
   */
  public void removeLeaderElectionListener(final Consumer<RaftMember> listener) {
    electionListeners.remove(listener);
  }

  /**
   * Returns the cluster state.
   *
   * @return The cluster state.
   */
  public RaftClusterContext getCluster() {
    return cluster;
  }

  /**
   * Returns the state leader.
   *
   * @return The state leader.
   */
  public DefaultRaftMember getLeader() {
    // Store in a local variable to prevent race conditions and/or multiple volatile lookups.
    final MemberId leader = this.leader;
    return leader != null ? cluster.getMember(leader) : null;
  }

  /** Transition handler. */
  public void transition(final Role role) {
    checkThread();
    checkNotNull(role);

    if (this.role.role() == role) {
      return;
    }

    log.info("Transitioning to {}", role);

    startTransition();

    // Close the old state.
    try {
      this.role.stop().get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new IllegalStateException("failed to close Raft state", e);
    }

    // Force state transitions to occur synchronously in order to prevent race conditions.
    try {
      final RaftRole newRole = createRole(role);
      synchronized (externalAccessLock) {
        // role is accessed by external threads. To ensure thread-safe access, we need to
        // synchronize the udpate.
        this.role = newRole;
      }
      this.role.start().get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new IllegalStateException("failed to initialize Raft state", e);
    }

    if (!this.role.role().active() && role.active()) {
      health = HealthReport.healthy(this);
      failureListeners.forEach(FailureListener::onRecovered);
    }

    if (this.role.role() == role) {
      if (this.role.role() == Role.LEADER) {
        // It is safe to assume that transition to leader is only complete after the initial entries
        // are committed.
        final LeaderRole leaderRole = (LeaderRole) this.role;
        leaderRole.onInitialEntriesCommitted(
            () -> {
              if (this.role == leaderRole) { // ensure no other role change happened in between
                notifyRoleChangeListeners();
                // Transitioning to leader completes
                // once the initial entry gets committed
                completeTransition();
              }
            });
      } else {
        notifyRoleChangeListeners();
        completeTransition();
      }
    }
  }

  private void startTransition() {
    ongoingTransition = true;
  }

  private void completeTransition() {
    missedSnapshotReplicationEvents = MissedSnapshotReplicationEvents.NONE;
    ongoingTransition = false;
  }

  private void notifyRoleChangeListeners() {
    try {
      roleChangeListeners.forEach(l -> l.onNewRole(role.role(), getTerm()));
    } catch (final Exception exception) {
      log.error("Unexpected error on calling role change listeners.", exception);
    }
  }

  /** Checks that the current thread is the state context thread. */
  public void checkThread() {
    threadContext.checkThread();
  }

  /** Creates an internal state for the given state type. */
  private RaftRole createRole(final Role role) {
    switch (role) {
      case INACTIVE:
        raftRoleMetrics.becomingInactive();
        return new InactiveRole(this);
      case PASSIVE:
        return new PassiveRole(this);
      case PROMOTABLE:
        return new PromotableRole(this);
      case FOLLOWER:
        raftRoleMetrics.becomingFollower();
        return new FollowerRole(this, this::createElectionTimer);
      case CANDIDATE:
        raftRoleMetrics.becomingCandidate();
        return new CandidateRole(this);
      case LEADER:
        raftRoleMetrics.becomingLeader();
        return new LeaderRole(this);
      default:
        throw new AssertionError();
    }
  }

  private ElectionTimer createElectionTimer(final Runnable triggerElection, final Logger log) {
    if (electionConfig.isPriorityElectionEnabled()) {
      return new PriorityElectionTimer(
          partitionConfig.getElectionTimeout(),
          threadContext,
          triggerElection,
          log,
          electionConfig.getInitialTargetPriority(),
          electionConfig.getNodePriority());
    } else {
      return new RandomizedElectionTimer(
          partitionConfig.getElectionTimeout(), threadContext, random, triggerElection, log);
    }
  }

  /** Transitions the server to the base state for the given member type. */
  public void transition(final Type type) {
    switch (type) {
      case ACTIVE:
        if (!(role instanceof ActiveRole)) {
          transition(Role.FOLLOWER);
        }
        break;
      case PROMOTABLE:
        if (role.role() != Role.PROMOTABLE) {
          transition(Role.PROMOTABLE);
        }
        break;
      case PASSIVE:
        if (role.role() != Role.PASSIVE) {
          transition(Role.PASSIVE);
        }
        break;
      default:
        if (role.role() != Role.INACTIVE) {
          transition(Role.INACTIVE);
        }
        break;
    }
  }

  @Override
  public void close() {
    raftRoleMetrics.becomingInactive();
    started = false;
    // Unregister protocol listeners.
    unregisterHandlers(protocol);

    // Close the log.
    try {
      raftLog.close();
    } catch (final Exception e) {
      log.error("Failed to close raft log", e);
    }

    // Close the metastore.
    try {
      meta.close();
    } catch (final Exception e) {
      log.error("Failed to close metastore", e);
    }

    // close thread contexts
    threadContext.close();
  }

  /** Unregisters server handlers on the configured protocol. */
  private void unregisterHandlers(final RaftServerProtocol protocol) {
    protocol.unregisterConfigureHandler();
    protocol.unregisterInstallHandler();
    protocol.unregisterReconfigureHandler();
    protocol.unregisterJoinHandler();
    protocol.unregisterLeaveHandler();
    protocol.unregisterTransferHandler();
    protocol.unregisterAppendHandler();
    protocol.unregisterPollHandler();
    protocol.unregisterVoteHandler();
  }

  @Override
  public String toString() {
    return getClass().getCanonicalName();
  }

  /**
   * Returns the commit index.
   *
   * @return The commit index.
   */
  public long getCommitIndex() {
    return commitIndex;
  }

  /**
   * Returns the election timeout.
   *
   * @return The election timeout.
   */
  public Duration getElectionTimeout() {
    return partitionConfig.getElectionTimeout();
  }

  /**
   * Returns the first commit index.
   *
   * @return The first commit index.
   */
  public long getFirstCommitIndex() {
    return firstCommitIndex;
  }

  /**
   * Sets the first commit index.
   *
   * @param firstCommitIndex The first commit index.
   */
  public void setFirstCommitIndex(final long firstCommitIndex) {
    if (this.firstCommitIndex == 0) {
      this.firstCommitIndex = firstCommitIndex;
      log.info(
          "Setting firstCommitIndex to {}. RaftServer is ready only after it has committed events upto this index",
          firstCommitIndex);
    }
  }

  /**
   * Returns the heartbeat interval.
   *
   * @return The heartbeat interval.
   */
  public Duration getHeartbeatInterval() {
    return partitionConfig.getHeartbeatInterval();
  }

  /**
   * Returns the entry validator to be called when an entry is appended.
   *
   * @return The entry validator.
   */
  public EntryValidator getEntryValidator() {
    return entryValidator;
  }

  /**
   * Sets the entry validator to be called when an entry is appended.
   *
   * @param validator The entry validator.
   */
  public void setEntryValidator(final EntryValidator validator) {
    entryValidator = validator;
  }

  /**
   * Returns the state last voted for candidate.
   *
   * @return The state last voted for candidate.
   */
  public MemberId getLastVotedFor() {
    return lastVotedFor;
  }

  /**
   * Sets the state last voted for candidate.
   *
   * @param candidate The candidate that was voted for.
   */
  public void setLastVotedFor(final MemberId candidate) {
    // If we've already voted for another candidate in this term then the last voted for candidate
    // cannot be overridden.
    checkState(!(lastVotedFor != null && candidate != null), "Already voted for another candidate");
    lastVotedFor = candidate;
    meta.storeVote(lastVotedFor);

    if (candidate != null) {
      log.debug("Voted for {}", candidate);
    } else {
      log.trace("Reset last voted for");
    }
  }

  /**
   * Returns the server log.
   *
   * @return The server log.
   */
  public RaftLog getLog() {
    return raftLog;
  }

  /**
   * Returns the cluster service.
   *
   * @return the cluster service
   */
  public ClusterMembershipService getMembershipService() {
    return membershipService;
  }

  /**
   * Returns the server metadata store.
   *
   * @return The server metadata store.
   */
  public MetaStore getMetaStore() {
    return meta;
  }

  /**
   * Returns the server name.
   *
   * @return The server name.
   */
  @Override
  public String getName() {
    return name;
  }

  @Override
  public HealthReport getHealthReport() {
    return health;
  }

  /** Adds a failure listener which will be invoked when an uncaught exception occurs */
  @Override
  public void addFailureListener(final FailureListener listener) {
    failureListeners.add(listener);
  }

  /** Remove a failure listener */
  @Override
  public void removeFailureListener(final FailureListener listener) {
    failureListeners.remove(listener);
  }

  /**
   * Returns the server protocol.
   *
   * @return The server protocol.
   */
  public RaftServerProtocol getProtocol() {
    return protocol;
  }

  /**
   * Returns the current server state.
   *
   * @return The current server state.
   */
  public RaftRole getRaftRole() {
    // This method is accessed by external threads. To ensure thread-safe access, we need to
    // synchronize access to role.
    synchronized (externalAccessLock) {
      return role;
    }
  }

  public RaftRoleMetrics getRaftRoleMetrics() {
    return raftRoleMetrics;
  }

  /**
   * Returns the current server role.
   *
   * @return The current server role.
   */
  public Role getRole() {
    return getRaftRole().role();
  }

  /**
   * Returns the server state machine.
   *
   * @return The log compactor.
   */
  public LogCompactor getLogCompactor() {
    return logCompactor;
  }

  /**
   * Returns the server snapshot store.
   *
   * @return The server snapshot store.
   */
  public ReceivableSnapshotStore getPersistedSnapshotStore() {
    return persistedSnapshotStore;
  }

  /**
   * Returns the current server state.
   *
   * @return the current server state
   */
  public State getState() {
    return state;
  }

  /**
   * Returns the server storage.
   *
   * @return The server storage.
   */
  public RaftStorage getStorage() {
    return storage;
  }

  /**
   * Returns the state term.
   *
   * @return The state term.
   */
  public long getTerm() {
    return term;
  }

  /**
   * Sets the state term.
   *
   * @param term The state term.
   */
  public void setTerm(final long term) {
    if (term > this.term) {
      this.term = term;
      leader = null;
      lastVotedFor = null;
      meta.storeTerm(this.term);
      meta.storeVote(lastVotedFor);
      log.debug("Set term {}", term);
    }
  }

  /**
   * Returns the execution context.
   *
   * @return The execution context.
   */
  public ThreadContext getThreadContext() {
    return threadContext;
  }

  /**
   * Returns a boolean indicating whether this server is the current leader.
   *
   * @return Indicates whether this server is the leader.
   */
  public boolean isLeader() {
    final MemberId leader = this.leader;
    return leader != null && leader.equals(cluster.getLocalMember().memberId());
  }

  /**
   * Sets the state leader.
   *
   * @param leader The state leader.
   */
  public void setLeader(final MemberId leader) {
    if (!Objects.equals(this.leader, leader)) {
      if (leader == null) {
        this.leader = null;
      } else {
        // If a valid leader ID was specified, it must be a member that's currently a member of the
        // ACTIVE members configuration. Note that we don't throw exceptions for unknown members.
        // It's possible that a failure following a configuration change could result in an unknown
        // leader sending AppendRequest to this server. Simply configure the leader if it's known.
        final DefaultRaftMember member = cluster.getMember(leader);
        if (member != null) {
          this.leader = leader;
          log.info("Found leader {}", member.memberId());
          electionListeners.forEach(l -> l.accept(member));
        }
      }

      log.trace("Set leader {}", this.leader);
    }
  }

  public PersistedSnapshot getCurrentSnapshot() {
    return currentSnapshot;
  }

  public void updateCurrentSnapshot() {
    checkThread();
    // Get the latest snapshot from snapshot store because it might have been updated already before
    // this listener is executed
    currentSnapshot = persistedSnapshotStore.getLatestSnapshot().orElse(null);
    log.trace("Set currentSnapshot to {}", currentSnapshot);
    logCompactor.compactFromSnapshots(persistedSnapshotStore);
  }

  public long getCurrentSnapshotIndex() {
    return currentSnapshot != null ? currentSnapshot.getIndex() : 0L;
  }

  /**
   * @return the current configuration index or -1 if there is no configuration yet.
   */
  public long getCurrentConfigurationIndex() {
    final var configuration = cluster.getConfiguration();
    return configuration != null ? configuration.index() : NO_CONFIGURATION_INDEX;
  }

  public boolean isRunning() {
    return started;
  }

  public RaftReplicationMetrics getReplicationMetrics() {
    return replicationMetrics;
  }

  public Random getRandom() {
    return random;
  }

  public long getLastHeartbeat() {
    return lastHeartbeat;
  }

  public void setLastHeartbeat(final long lastHeartbeat) {
    this.lastHeartbeat = lastHeartbeat;
  }

  public void resetLastHeartbeat() {
    setLastHeartbeat(System.currentTimeMillis());
  }

  public int getMinStepDownFailureCount() {
    return partitionConfig.getMinStepDownFailureCount();
  }

  public Duration getMaxQuorumResponseTimeout() {
    return partitionConfig.getMaxQuorumResponseTimeout();
  }

  public int getPreferSnapshotReplicationThreshold() {
    return partitionConfig.getPreferSnapshotReplicationThreshold();
  }

  public void setPreferSnapshotReplicationThreshold(final int snapshotReplicationThreshold) {
    partitionConfig.setPreferSnapshotReplicationThreshold(snapshotReplicationThreshold);
  }

  public CompletableFuture<Void> reconfigurePriority(final int newPriority) {
    final CompletableFuture<Void> configureFuture = new CompletableFuture<>();
    threadContext.execute(
        () -> {
          electionConfig.setNodePriority(newPriority);
          if (role instanceof final FollowerRole followerRole
              && followerRole.getElectionTimer()
                  instanceof final PriorityElectionTimer priorityElectionTimer) {
            priorityElectionTimer.setNodePriority(newPriority);
          }
          configureFuture.complete(null);
        });
    return configureFuture;
  }

  public int getPartitionId() {
    return partitionId;
  }

  private void updateState(final State newState) {
    if (state != newState) {
      state = newState;
      stateChangeListeners.forEach(l -> l.accept(state));
    }
  }

  public CompletableFuture<Collection<Path>> getTailSegments(final long index) {
    final var fut = new CompletableFuture<Collection<Path>>();
    threadContext.execute(
        () -> {
          final var segments = raftLog.getTailSegments(index);
          fut.complete(segments.values());
        });
    return fut;
  }

  /** Raft server state. */
  public enum State {
    ACTIVE,
    READY,
    LEFT,
  }

  /**
   * Keeps track of potentially missed snapshot replication events to properly notify newly
   * registered listeners.
   */
  private enum MissedSnapshotReplicationEvents {
    NONE,
    STARTED,
    COMPLETED
  }

  /** Commit listener is active only until the server is ready * */
  final class AwaitingReadyCommitListener implements RaftCommitListener {
    private final Logger throttledLogger = new ThrottledLogger(log, Duration.ofSeconds(30));

    @Override
    public void onCommit(final long index) {
      setFirstCommitIndex(index);
      // On start up, set the state to READY after the follower has caught up with the leader
      // https://github.com/zeebe-io/zeebe/issues/4877
      if (index >= firstCommitIndex) {
        log.info("Commit index is {}. RaftServer is ready", index);
        updateState(State.READY);
        removeCommitListener(this);
      } else {
        throttledLogger.info(
            "Commit index is {}. RaftServer is ready only after it has committed events up to index {}",
            commitIndex,
            firstCommitIndex);
      }
    }
  }
}
