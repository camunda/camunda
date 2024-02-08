/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoRemoteHandler;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftException.ProtocolException;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.impl.RaftContext.State;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import java.net.ConnectException;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class ReconfigurationHelper {

  private final ThreadContext threadContext;
  private final RaftContext raftContext;
  private final Logger logger;

  public ReconfigurationHelper(final RaftContext raftContext) {
    threadContext = raftContext.getThreadContext();
    this.raftContext = raftContext;
    logger =
        ContextualLoggerFactory.getLogger(
            getClass(),
            LoggerContext.builder(RaftServer.class).addValue(raftContext.getName()).build());
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
                        logger.debug("Join request was not acknowledged, retrying", cause);
                        joinWithRetry(joining, assistingMembers, result);
                      } else {
                        logger.error(
                            "Join request failed with an unexpected error, not retrying", error);
                        result.completeExceptionally(error);
                      }
                    } else if (response.status() == Status.OK) {
                      logger.debug("Join request accepted");
                      result.complete(null);
                    } else if (response.error().type() == RaftError.Type.NO_LEADER
                        || response.error().type() == RaftError.Type.UNAVAILABLE) {
                      logger.debug(
                          "Join request failed, retrying", response.error().createException());
                      joinWithRetry(joining, assistingMembers, result);
                    } else {
                      final var errorAsException = response.error().createException();
                      logger.error("Join request rejected, not retrying", errorAsException);
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
        });
    return future;
  }

  /** Attempts to become the leader. */
  public CompletableFuture<Void> anoint() {
    if (raftContext.getRaftRole().role() == Role.LEADER) {
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
                  if (member
                      .memberId()
                      .equals(raftContext.getCluster().getLocalMember().memberId())) {
                    future.complete(null);
                  } else {
                    future.completeExceptionally(
                        new ProtocolException("Failed to transfer leadership"));
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
                    leader.memberId(),
                    TransferRequest.builder().withMember(member.memberId()).build())
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
        });
    return future;
  }
}
