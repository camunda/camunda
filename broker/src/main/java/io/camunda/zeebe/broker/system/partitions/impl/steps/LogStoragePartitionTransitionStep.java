/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static io.camunda.zeebe.util.Either.left;
import static io.camunda.zeebe.util.Either.right;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.nio.ByteBuffer;

public final class LogStoragePartitionTransitionStep implements PartitionTransitionStep {
  private static final String WRONG_TERM_ERROR_MSG =
      "Expected that current term '%d' is same as raft term '%d', but was not. Failing installation of 'LogStoragePartitionStep' on partition %d.";

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {

    final var logStorage = context.getLogStorage();
    if (logStorage != null
        && (shouldInstallOnTransition(targetRole, context.getCurrentRole())
            || targetRole == Role.INACTIVE)) {
      context.getRaftPartition().getServer().removeCommitListener(logStorage);
      context.setLogStorage(null);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (shouldInstallOnTransition(targetRole, context.getCurrentRole())
        || (context.getLogStorage() == null && targetRole != Role.INACTIVE)) {
      final CompletableActorFuture<Void> openFuture = new CompletableActorFuture<>();

      final var logStorageOrException = buildAtomixLogStorage(context, term, targetRole);

      if (logStorageOrException.isRight()) {
        final var logStorage = logStorageOrException.get();
        context.setLogStorage(logStorage);
        context.getRaftPartition().getServer().addCommitListener(logStorage);
        openFuture.complete(null);
      } else {
        openFuture.completeExceptionally(logStorageOrException.getLeft());
      }

      return openFuture;
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  public String getName() {
    return "LogStorage";
  }

  private boolean shouldInstallOnTransition(final Role newRole, final Role currentRole) {
    return newRole == Role.LEADER
        || (newRole == Role.FOLLOWER && currentRole != Role.CANDIDATE)
        || (newRole == Role.CANDIDATE && currentRole != Role.FOLLOWER);
  }

  private Either<Exception, AtomixLogStorage> buildAtomixLogStorage(
      final PartitionTransitionContext context, final long targetTerm, final Role targetRole) {
    final var server = context.getRaftPartition().getServer();

    if (targetRole == Role.LEADER) {
      return createWritableLogStorage(context, server, targetTerm);
    } else {
      return createReadOnlyStorage(server);
    }
  }

  private Either<Exception, AtomixLogStorage> createReadOnlyStorage(
      final RaftPartitionServer server) {

    return right(
        new AtomixLogStorage(
            server::openReader,
            // Prevent followers from writing new events
            new LogAppenderForReadOnlyStorage()));
  }

  private Either<Exception, AtomixLogStorage> createWritableLogStorage(
      final PartitionTransitionContext context,
      final RaftPartitionServer server,
      final long targetTerm) {
    final var appenderOptional = server.getAppender();
    return appenderOptional
        .map(
            logAppender -> checkAndCreateAtomixLogStorage(context, server, logAppender, targetTerm))
        .orElseGet(
            () ->
                left(
                    new IllegalStateException(
                        "Not leader of partition " + context.getPartitionId())));
  }

  private Either<Exception, AtomixLogStorage> checkAndCreateAtomixLogStorage(
      final PartitionTransitionContext context,
      final RaftPartitionServer server,
      final ZeebeLogAppender logAppender,
      final long targetTerm) {
    final var raftTerm = server.getTerm();

    if (raftTerm != targetTerm) {
      return left(
          new IllegalStateException(
              String.format(WRONG_TERM_ERROR_MSG, targetTerm, raftTerm, context.getPartitionId())));
    } else {
      final var logStorage = AtomixLogStorage.ofPartition(server::openReader, logAppender);
      return right(logStorage);
    }
  }

  private static class LogAppenderForReadOnlyStorage implements ZeebeLogAppender {
    @Override
    public void appendEntry(
        final long lowestPosition,
        final long highestPosition,
        final ByteBuffer data,
        final AppendListener appendListener) {
      throw new UnsupportedOperationException(
          String.format(
              "Expect to append entry (positions %d - %d), but was in Follower role. Followers must not append entries to the log storage",
              lowestPosition, highestPosition));
    }
  }
}
