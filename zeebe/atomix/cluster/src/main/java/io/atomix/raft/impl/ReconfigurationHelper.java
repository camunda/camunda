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
import io.atomix.utils.concurrent.ThreadContext;
import java.net.ConnectException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
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

  public CompletableFuture<Void> join(final Collection<MemberId> clusterMembers) {
    final var result = new CompletableFuture<Void>();
    threadContext.execute(
        () -> {
          final var joining =
              new DefaultRaftMember(
                  raftContext.getCluster().getLocalMember().memberId(), Type.ACTIVE, Instant.now());
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
          threadContext.execute(() -> joinWithRetry(joining, assistingMembers, result));
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

    final var receiver = assistingMembers.poll();
    if (receiver == null) {
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
                  threadContext.execute(() -> joinWithRetry(joining, assistingMembers, result));
                } else {
                  LOGGER.error("Join request failed with an unexpected error, not retrying", error);
                  result.completeExceptionally(error);
                }
              } else if (response.status() == Status.OK) {
                LOGGER.debug("Join request accepted");
                result.complete(null);
              } else if (response.error().type() == RaftError.Type.NO_LEADER
                  || response.error().type() == RaftError.Type.UNAVAILABLE) {
                LOGGER.debug("Join request failed, retrying", response.error().createException());
                threadContext.execute(() -> joinWithRetry(joining, assistingMembers, result));
              } else {
                final var errorAsException = response.error().createException();
                LOGGER.error("Join request rejected, not retrying", errorAsException);
                result.completeExceptionally(errorAsException);
              }
            },
            threadContext);
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
            .orElseThrow();
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
