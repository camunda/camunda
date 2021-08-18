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
import io.camunda.zeebe.broker.system.partitions.PartitionStartupAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.nio.ByteBuffer;

public class LogStoragePartitionStep implements PartitionStep {
  private static final String WRONG_TERM_ERROR_MSG =
      "Expected that current term '%d' is same as raft term '%d', but was not. Failing installation of 'LogStoragePartitionStep' on partition %d.";

  @Override
  public ActorFuture<Void> open(final PartitionStartupAndTransitionContextImpl context) {
    final CompletableActorFuture<Void> openFuture = new CompletableActorFuture<>();

    final var logStorageOrException = buildAtomixLogStorage(context);

    if (logStorageOrException.isRight()) {
      final var logStorage = logStorageOrException.get();
      context.setLogStorage(logStorage);
      context.getRaftPartition().getServer().addCommitListener(logStorage);
      openFuture.complete(null);
    } else {
      openFuture.completeExceptionally(logStorageOrException.getLeft());
    }

    return openFuture;
  }

  @Override
  public ActorFuture<Void> close(final PartitionStartupAndTransitionContextImpl context) {
    final var logStorage = context.getLogStorage();
    if (logStorage != null) {
      context.getRaftPartition().getServer().removeCommitListener(logStorage);
      context.setLogStorage(null);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "logstorage";
  }

  private Either<Exception, AtomixLogStorage> buildAtomixLogStorage(
      final PartitionStartupAndTransitionContextImpl context) {
    final var server = context.getRaftPartition().getServer();

    if (context.getCurrentRole() == Role.LEADER) {
      return createWritableLogStorage(context, server);
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
      final PartitionStartupAndTransitionContextImpl context, final RaftPartitionServer server) {
    final var appenderOptional = server.getAppender();
    return appenderOptional
        .map(logAppender -> checkAndCreateAtomixLogStorage(context, server, logAppender))
        .orElseGet(
            () ->
                left(
                    new IllegalStateException(
                        "Not leader of partition " + context.getPartitionId())));
  }

  private Either<Exception, AtomixLogStorage> checkAndCreateAtomixLogStorage(
      final PartitionStartupAndTransitionContextImpl context,
      final RaftPartitionServer server,
      final ZeebeLogAppender logAppender) {
    final var raftTerm = server.getTerm();

    if (raftTerm != context.getCurrentTerm()) {
      return left(buildWrongTermException(context, raftTerm));
    } else {
      final var logStorage = AtomixLogStorage.ofPartition(server::openReader, logAppender);
      return right(logStorage);
    }
  }

  private IllegalStateException buildWrongTermException(
      final PartitionStartupAndTransitionContextImpl context, final long raftTerm) {
    return new IllegalStateException(
        String.format(
            WRONG_TERM_ERROR_MSG, context.getCurrentTerm(), raftTerm, context.getPartitionId()));
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
