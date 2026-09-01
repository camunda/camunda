/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoRemoteHandler;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftException.ProtocolException;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.impl.RaftContext.State;
import io.atomix.raft.protocol.ForceConfigureRequest;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.raft.utils.ForceConfigureQuorum;
import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.ThreadContext;
import java.net.ConnectException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReconfigurationHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReconfigurationHelper.class);

  private final ThreadContext threadContext;
  private final RaftContext raftContext;

  public ReconfigurationHelper(final RaftContext raftContext) {
    threadContext = raftContext.getThreadContext();
    this.raftContext = raftContext;
  }

  public CompletableFuture<Void> join(final Type type, final Collection<MemberId> clusterMembers) {
    final var result = new CompletableFuture<Void>();
    if (type == Type.INACTIVE) {
      result.completeExceptionally(
          new IllegalArgumentException(
              "Cannot join cluster as %s, must join as %s, %s or %s"
                  .formatted(type, Type.PASSIVE, Type.PROMOTABLE, Type.ACTIVE)));
      return result;
    }
    threadContext.execute(
        () -> {
          // If the previous join was partially or fully completed, i.e. committed the first
          // configuration with joint consensus or committed the final configuration, then in the
          // next retry, this member can already start with that configuration. This is mainly
          // required if the member is joining a single member cluster, making the quorum 2 out of
          // 2. If this member did not re-start in the ACTIVE state when the other node has already
          // included it in the quorum, then the other member cannot become leader and continue the
          // reconfiguration step.
          try {
            raftContext.getCluster().reloadConfigurationFromLog();
          } catch (final Exception e) {
            LOGGER.warn("Failed to join cluster, could not reload configuration from log", e);
            result.completeExceptionally(e);
            return;
          }

          final var storedType = raftContext.getCluster().getLocalMember().getType();
          final var startType =
              switch (storedType) {
                // No configuration at all: the member may replicate and catch up but must not
                // enter the voting follower role, and it must not stay INACTIVE either, which
                // accepts no appends and would prevent the join from completing, particularly
                // when joining a single-member cluster where this new node is already required
                // for quorum. ACTIVE joins keep starting as PASSIVE: starting them as PROMOTABLE
                // would satisfy both constraints too, but would surface the PROMOTABLE role on
                // the production one-shot join path before the dynamic-config orchestration
                // adopts two-phase joins (#57392); the leader's configuration promotes them
                // either way.
                case INACTIVE -> type == Type.ACTIVE ? Type.PASSIVE : type;
                // Never start above the requested type: non-voting members persist the leader's
                // uncommitted tail, so the stored type may come from a configuration entry that a
                // later leader has since truncated - a PROMOTABLE joiner must not enter the
                // voting follower role on the strength of such an entry while the leader still
                // counts it as non-voting.
                default -> Collections.min(List.of(storedType, type));
              };
          raftContext.transition(startType);

          // We don't know if the latest configuration loaded from the log is valid. So we will
          // retry join any way.
          final var joining =
              new DefaultRaftMember(
                  raftContext.getCluster().getLocalMember().memberId(), type, Instant.now());
          final var knownAssistingMembers =
              clusterMembers.stream()
                  .filter(memberId -> !memberId.equals(joining.memberId()))
                  .toList();
          if (knownAssistingMembers.isEmpty()) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Cannot join cluster, because there are no other members in the cluster."));
            return;
          }
          final var assistingMembers = new LinkedBlockingQueue<>(knownAssistingMembers);
          final var deadline = Instant.now().plus(raftContext.getConfigurationChangeTimeout());
          threadContext.execute(
              () ->
                  joinWithRetry(
                      joining, knownAssistingMembers, assistingMembers, result, deadline));
        });
    return result;
  }

  /**
   * Repeatedly tries to join the cluster until it succeeds or the deadline passes. When sending a
   * join request to an assisting member fails because the member is currently not known, or it is
   * known but not ready to receive join request, try again with a different assisting member. Once
   * every known member failed, start over with all of them after a delay.
   *
   * <p>Retrying helps in cases where the cluster is in flux and not all members are online and
   * ready.
   *
   * @param joining the new member joining
   * @param knownAssistingMembers all members that can assist the join, used to refill the queue
   * @param assistingMembers a queue of members that we will send a join request to.
   * @param result a future to complete when joining succeeds or fails
   * @param deadline until when the join is retried
   */
  private void joinWithRetry(
      final RaftMember joining,
      final Collection<MemberId> knownAssistingMembers,
      final Queue<MemberId> assistingMembers,
      final CompletableFuture<Void> result,
      final Instant deadline) {

    final var receiver = assistingMembers.poll();
    if (receiver == null) {
      if (Instant.now().isBefore(deadline)) {
        // Don't fail while the deadline still has budget: the reasons a member fails to accept a
        // join request are transient, and there may be only a single assisting member - the
        // production shape when scaling a partition from one to two replicas - so a single
        // transport error can drain the queue within milliseconds. Refill it and start over after
        // a delay, giving the cluster time to make progress.
        LOGGER.debug(
            "Join request failed on all known members {}, retrying after {}",
            knownAssistingMembers,
            raftContext.getElectionTimeout());
        assistingMembers.addAll(knownAssistingMembers);
        threadContext.schedule(
            raftContext.getElectionTimeout(),
            () ->
                joinWithRetry(joining, knownAssistingMembers, assistingMembers, result, deadline));
        return;
      }
      result.completeExceptionally(
          new IllegalStateException(
              "Sent join request to all known members, but all failed. No more members left."));
      return;
    }
    raftContext
        .getProtocol()
        .join(receiver, JoinRequest.builder().withJoiningMember(joining).build())
        .whenCompleteAsync(
            (response, error) -> {
              if (error != null) {
                final var cause = error.getCause();
                if (cause instanceof NoSuchMemberException
                    || cause instanceof NoRemoteHandler
                    || cause instanceof TimeoutException
                    || cause instanceof ConnectException) {
                  LOGGER.debug("Join request was not acknowledged, retrying", cause);
                  threadContext.execute(
                      () ->
                          joinWithRetry(
                              joining, knownAssistingMembers, assistingMembers, result, deadline));
                } else {
                  LOGGER.error("Join request failed with an unexpected error, not retrying", error);
                  result.completeExceptionally(error);
                }
              } else if (response.status() == Status.OK) {
                // The leader may have committed the configuration entry admitting this member
                // without this member's participation at all - e.g. a PROMOTABLE joiner is in no
                // quorum, so the old side of a joint configuration can commit on the strength of
                // the existing members alone. Wait for this node's own commit index to reach the
                // entry before declaring the join done: RaftContext#setCommitIndex clamps to what
                // this node has itself persisted, so reaching the index implies the entry is
                // already durable here too - exactly what a restart needs, see
                // RaftClusterContext#reloadConfigurationFromLog - and it recovers this join's
                // outcome instead of falling back to treating this node as a fresh, unconfigured
                // one. A crash before that needs no special handling: the join future then never
                // completes, so the caller retries it.
                LOGGER.debug(
                    "Join request accepted at index {}, waiting for the configuration entry to"
                        + " commit locally",
                    response.index());
                awaitLocalCommit(response.index(), result);
              } else if (response.error().type() == RaftError.Type.NO_LEADER
                  || response.error().type() == RaftError.Type.CONFIGURATION_ERROR) {
                if (Instant.now().isBefore(deadline)) {
                  LOGGER.debug(
                      "Join request failed, retrying after {}",
                      raftContext.getElectionTimeout(),
                      response.error().createException());
                  // The member is reachable but doesn't know a leader yet, or the leader cannot
                  // make configuration changes yet. Re-offer it so that, after falling over to the
                  // remaining members, it is retried once the cluster made progress. That progress
                  // may depend on this join attempt: while the join is in flight, this server is a
                  // passive member that answers polls and votes and accepts appends. For example,
                  // a leader that recovered an uncommitted joint configuration cannot commit its
                  // initialization entry - and thus rejects configuration changes - until the
                  // joining member acknowledged it.
                  assistingMembers.offer(receiver);
                  threadContext.schedule(
                      raftContext.getElectionTimeout(),
                      () ->
                          joinWithRetry(
                              joining, knownAssistingMembers, assistingMembers, result, deadline));
                } else {
                  LOGGER.error(
                      "Join request failed, not retrying because the join did not complete within {}",
                      raftContext.getConfigurationChangeTimeout(),
                      response.error().createException());
                  result.completeExceptionally(response.error().createException());
                }
              } else if (response.error().type() == RaftError.Type.UNAVAILABLE) {
                LOGGER.debug("Join request failed, retrying", response.error().createException());
                threadContext.execute(
                    () ->
                        joinWithRetry(
                            joining, knownAssistingMembers, assistingMembers, result, deadline));
              } else {
                final var errorAsException = response.error().createException();
                LOGGER.error("Join request rejected, not retrying", errorAsException);
                result.completeExceptionally(errorAsException);
              }
            },
            threadContext);
  }

  /**
   * Completes {@code result} once this node's own commit index reaches {@code index}, using {@link
   * RaftContext#addCommitListener} rather than polling. {@link RaftContext#setCommitIndex} clamps
   * to what this node has itself persisted, so waiting for the local commit index - not the
   * leader's - is what guarantees the entry is durable here too.
   *
   * <p>The wait runs on its own {@link RaftContext#getJoinCatchUpTimeout()} budget rather than what
   * remains of the join-request deadline, which may be mostly spent on retries by the time the join
   * is accepted: reaching the accepted index is paced by replication - potentially a snapshot
   * install plus a log replay - not by request round-trips.
   *
   * @param index the index of the configuration entry admitting this member
   * @param result the future to complete once the entry commits locally
   */
  private void awaitLocalCommit(final long index, final CompletableFuture<Void> result) {
    if (raftContext.getCommitIndex() >= index) {
      LOGGER.debug("Configuration entry at index {} is already committed locally", index);
      result.complete(null);
      return;
    }

    // Self-removing: whichever of the commit notification or the timeout fires first cancels the
    // other. Needs a mutable field for the timeout handle because the timeout's callback captures
    // the listener, so the listener must exist before the handle does.
    final var listener =
        new RaftCommitListener() {
          Scheduled timeout;

          @Override
          public void onCommit(final long committedIndex) {
            if (committedIndex < index) {
              return;
            }
            raftContext.removeCommitListener(this);
            timeout.cancel();
            LOGGER.debug("Configuration entry at index {} committed locally", index);
            result.complete(null);
          }
        };
    listener.timeout =
        threadContext.schedule(
            raftContext.getJoinCatchUpTimeout(),
            () -> {
              raftContext.removeCommitListener(listener);
              result.completeExceptionally(
                  new TimeoutException(
                      "Join request was accepted at index %d, but that configuration entry did"
                          + " not commit locally within the join catch-up timeout of %s"
                              .formatted(index, raftContext.getJoinCatchUpTimeout())));
            });
    raftContext.addCommitListener(listener);
  }

  public CompletableFuture<Void> leave() {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    threadContext.execute(() -> leaveInternal(future));
    return future;
  }

  private void leaveInternal(final CompletableFuture<Void> future) {
    final var leaving = raftContext.getCluster().getLocalMember();
    final var receiver =
        Optional.ofNullable(raftContext.getLeader())
            .map(DefaultRaftMember::memberId)
            .or(
                () ->
                    raftContext.getCluster().getVotingMembers().stream()
                        .map(RaftMember::memberId)
                        .findAny())
            .orElse(null);
    if (receiver == null) {
      // The local member is the last voting member left but has not elected itself leader yet, as
      // when the second-to-last member just left. Fail with the same error a member without a
      // known leader would respond with so that the caller retries after the election. Throwing
      // here instead would crash the raft thread and permanently transition to inactive.
      future.completeExceptionally(
          new RaftError(
                  RaftError.Type.NO_LEADER,
                  "Cannot leave, no leader is known and there is no other voting member to receive the leave request. Retry after a leader is elected.")
              .createException());
      return;
    }
    raftContext
        .getProtocol()
        .leave(receiver, LeaveRequest.builder().withLeavingMember(leaving).build())
        .whenCompleteAsync(
            (response, error) -> {
              if (error != null) {
                future.completeExceptionally(error);
              } else if (response.status() == Status.OK) {
                future.complete(null);
                raftContext.updateState(State.LEFT);
              } else {
                future.completeExceptionally(response.error().createException());
              }
            },
            threadContext);
  }

  /**
   * Force configuration works as follows. Assume current members are 0,1,3,4, and we want to force
   * remove 2 and 3.
   *
   * <pre>
   *
   *   External                        Raft 0 (follower)                     Raft 1 (follower)             Raft 2/3
   *      |                                 |                                     |                        (Members to be removed)
   *      |    forceConfigure([0,1])        |                                     |                                    |
   *      |-------------------------------->|                                     |                                    |
   *      |                                 |                                     |                                    |
   *      |               Configuration={   |                                     |                                    |
   *      |                newMembers=[0,1],|                                     |                                    |
   *      |                oldMembers=[]    |                                     |                                    |
   *      |                force=TRUE       |                                     |                                    |
   *      |               Commit new config |   ForceConfigureRequest(newMembers) |                                    |
   *      |                                 |------------------------------------>|                                    |
   *      |                                 |               OK                    |Commit new Configuration            |
   *      |         OK                      |<------------------------------------|                                    |
   *      |<--------------------------------|                                     |        Poll/Vote/Append            |
   *      |                                 |                                     |<-----------------------------------|
   *      |                        election |             poll/vote               |----------------------------------->|
   *      |                        timeout  ------------------------------------->|     Reject because Force==TRUE     |
   *      |                                 |               OK                    |                                    |
   *      |                                 |<------------------------------------|                                    |
   *      |                    Become leader|                                     |                                    |
   *      |                                 |                                     |                                    |
   *      |             Append InitialEntry |                                     |                                    |
   *      |       Append ConfigurationEntry |                                     |                                    |
   *      |               Configuration={   |           AppendEntry               |                                    |
   *      |                newMembers=[0,1] |------------------------------------>|                                    |
   *      |                force=FALSE      |<------------------------------------|                                    |
   *      |               }                 |                                     |                                    |
   *      |                                 |------------------------------------>|                                    |
   *      |                                 |<------------------------------------|                                    |
   *      |                                 |                                     |                                    |
   *      |                Commit new config|            AppendEntry              |On commitIndex update               |
   *      |                                 |------------------------------------>|Commit new config                   |
   *      |                                 |                                     |                                    |
   *      |                                 |                                     |      Poll/Vote                     |
   *      |                                 |                                     |<-----------------------------------|
   *      |                                 |                                     |  Reject because log not uptodate   |
   *      |                                 |                                     |----------------------------------->|
   *      |                                 |                                     |                                    |
   * </pre>
   */
  public CompletableFuture<Void> forceConfigure(final Map<MemberId, Type> newMembersIds) {
    final CompletableFuture<Void> future = new CompletableFuture<>();

    threadContext.execute(() -> triggerForceConfigure(newMembersIds, future));
    return future;
  }

  private void triggerForceConfigure(
      final Map<MemberId, Type> newMembersIds, final CompletableFuture<Void> future) {
    final var currentConfiguration = raftContext.getCluster().getConfiguration();
    final Set<RaftMember> newMembers =
        newMembersIds.entrySet().stream()
            .map(
                memberEntry ->
                    new DefaultRaftMember(
                        memberEntry.getKey(), memberEntry.getValue(), Instant.now()))
            .collect(Collectors.toSet());

    if (currentConfiguration == null || !currentConfiguration.force()) {
      // No need to overwrite if it is already in force configure and this is a retry
      if (raftContext.getRaftRole().role() == Role.LEADER) {
        // Optimization: If the current configuration is already the same as new forced, we
        // can skip reconfiguring. It is most likely a retry of a previous force request,
        // which was interpreted as failure because of a request timeout.
        raftContext.transition(Role.FOLLOWER);
      }

      LOGGER.info(
          "Current configuration is '{}'. Forcing configuration with members '{}'",
          currentConfiguration,
          newMembers);
      final var newConfiguration =
          new Configuration(
              raftContext.getCurrentConfigurationIndex() + 1,
              raftContext.getTerm(),
              Instant.now().toEpochMilli(),
              newMembers,
              Set.of(),
              true);

      raftContext.getCluster().configure(newConfiguration);
    } else if (!(currentConfiguration.allMembers().equals(newMembers))) {
      // This is not expected. When force configuration is retried, we expect that they are
      // retried with the same state. If this is not the case, it is likely that there are two
      // force configuration requested at the same time.
      // Reject the request. There is possibly no way out to recover from this.
      future.completeExceptionally(
          new IllegalStateException(
              String.format(
                  "Expected to force configure with members '%s', but the member is already in force configuration with a different set of members '%s'",
                  newMembers, currentConfiguration.allMembers())));
      return;
    }

    sendForceConfigureRequestToAllMembers(future);
  }

  private void sendForceConfigureRequestToAllMembers(final CompletableFuture<Void> future) {
    final Configuration configuration = raftContext.getCluster().getConfiguration();
    final var otherMembers =
        configuration.newMembers().stream()
            .map(RaftMember::memberId)
            .filter(m -> !m.equals(raftContext.getCluster().getLocalMember().memberId()))
            .collect(Collectors.toSet());

    if (otherMembers.isEmpty()) {
      future.complete(null);
      return;
    }

    final var quorum =
        new ForceConfigureQuorum(
            success -> {
              if (Boolean.TRUE.equals(success)) {
                future.complete(null);
              } else {
                future.completeExceptionally(
                    new ProtocolException(
                        "Failed to force configure because not all members acknowledged the request."));
              }
            },
            otherMembers);

    final ForceConfigureRequest request =
        ForceConfigureRequest.builder()
            .withTerm(configuration.term())
            .withIndex(configuration.index())
            .withTime(configuration.time())
            // Beware that using ImmutableCollections can break Kryo serialization
            .withNewMembers(new HashSet<>(configuration.newMembers()))
            .from(raftContext.getCluster().getLocalMember().memberId())
            .build();

    otherMembers.forEach(memberId -> sendForceConfigurationRequest(memberId, request, quorum));
  }

  private void sendForceConfigurationRequest(
      final MemberId memberId,
      final ForceConfigureRequest request,
      final ForceConfigureQuorum quorum) {
    LOGGER.trace("Sending '{}' request to member '{}'", request, memberId);

    raftContext
        .getProtocol()
        .forceConfigure(memberId, request)
        .whenCompleteAsync(
            (response, error) -> {
              if (error != null) {
                LOGGER.warn(
                    "Failed to send force configure request to member '{}'", memberId, error);
                quorum.fail(memberId);
              } else if (response.status() == Status.OK) {
                LOGGER.debug("Successfully sent force configure request to member '{}'", memberId);
                quorum.succeed(memberId);
              } else {
                LOGGER.warn(
                    "Failed to send force configure request to member '{}': {}",
                    memberId,
                    response.error());
                quorum.fail(memberId);
              }
            },
            threadContext);
  }

  /** Attempts to become the leader. */
  public CompletableFuture<Void> anoint() {
    if (raftContext.getRaftRole().role() == Role.LEADER) {
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<Void> future = new CompletableFuture<>();
    threadContext.execute(() -> anointInternal(future));
    return future;
  }

  private void anointInternal(final CompletableFuture<Void> future) {
    // Register a leader election listener to wait for the election of this node.
    final Consumer<RaftMember> electionListener =
        new Consumer<>() {
          @Override
          public void accept(final RaftMember member) {
            if (member.memberId().equals(raftContext.getCluster().getLocalMember().memberId())) {
              future.complete(null);
            } else {
              future.completeExceptionally(new ProtocolException("Failed to transfer leadership"));
            }
            raftContext.removeLeaderElectionListener(this);
          }
        };
    raftContext.addLeaderElectionListener(electionListener);

    // If a leader already exists, request a leadership transfer from it. Otherwise,
    // transition to the candidate
    // state and attempt to get elected.
    final RaftMember member = raftContext.getCluster().getLocalMember();
    final RaftMember leader = raftContext.getLeader();
    if (leader != null) {
      raftContext
          .getProtocol()
          .transfer(
              leader.memberId(), TransferRequest.builder().withMember(member.memberId()).build())
          .whenCompleteAsync(
              (response, error) -> {
                if (error != null) {
                  future.completeExceptionally(error);
                } else if (response.status() == Status.ERROR) {
                  future.completeExceptionally(response.error().createException());
                } else {
                  raftContext.transition(Role.CANDIDATE);
                }
              },
              threadContext);
    } else {
      raftContext.transition(Role.CANDIDATE);
    }
  }
}
