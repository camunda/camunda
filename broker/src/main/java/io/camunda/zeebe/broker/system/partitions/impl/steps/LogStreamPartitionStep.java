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

import io.camunda.zeebe.broker.system.partitions.PartitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class LogStreamPartitionStep implements PartitionStep {
  private static final String WRONG_TERM_ERROR_MSG =
      "Expected that current term '%d' is same as raft term '%d', but was not. Failing installation of 'AtomixLogStoragePartitionStep' on partition %d.";

  @Override
  public ActorFuture<Void> open(final PartitionContext context) {
    final CompletableActorFuture<Void> openFuture = new CompletableActorFuture<>();

    final var logStorageOrException = buildAtomixLogStorage(context);

    if (logStorageOrException.isRight()) {
      buildLogstream(context, logStorageOrException.get())
          .onComplete(
              ((logStream, err) -> {
                if (err == null) {
                  context.setLogStream(logStream);

                  if (context.getDeferredCommitPosition() > 0) {
                    context.getLogStream().setCommitPosition(context.getDeferredCommitPosition());
                    context.setDeferredCommitPosition(-1);
                  }
                  context
                      .getComponentHealthMonitor()
                      .registerComponent(logStream.getLogName(), logStream);
                  openFuture.complete(null);
                } else {
                  openFuture.completeExceptionally(err);
                }
              }));
    } else {
      openFuture.completeExceptionally(logStorageOrException.getLeft());
    }

    return openFuture;
  }

  @Override
  public ActorFuture<Void> close(final PartitionContext context) {
    context.getComponentHealthMonitor().removeComponent(context.getLogStream().getLogName());
    final ActorFuture<Void> future = context.getLogStream().closeAsync();
    context.setLogStream(null);
    return future;
  }

  @Override
  public String getName() {
    return "logstream";
  }

  private Either<? extends Exception, AtomixLogStorage> buildAtomixLogStorage(
      final PartitionContext context) {

    final Either<? extends Exception, AtomixLogStorage> result;
    final var server = context.getRaftPartition().getServer();

    final var appenderOptional = server.getAppender();
    return (Either<? extends Exception, AtomixLogStorage>)
        appenderOptional
            .map(
                logAppender -> {
                  final var raftTerm = server.getTerm();

                  if (raftTerm != context.getCurrentTerm()) {
                    return left(buildWrongTermException(context, raftTerm));
                  } else {
                    return right(AtomixLogStorage.ofPartition(server::openReader, logAppender));
                  }
                })
            .orElseGet(
                () ->
                    left(
                        new IllegalStateException(
                            "Not leader of partition " + context.getPartitionId())));
  }

  private IllegalStateException buildWrongTermException(
      final PartitionContext context, final long raftTerm) {
    return new IllegalStateException(
        String.format(
            WRONG_TERM_ERROR_MSG, context.getCurrentTerm(), raftTerm, context.getPartitionId()));
  }

  private ActorFuture<LogStream> buildLogstream(
      final PartitionContext context, final AtomixLogStorage atomixLogStorage) {
    return LogStream.builder()
        .withLogStorage(atomixLogStorage)
        .withLogName("logstream-" + context.getRaftPartition().name())
        .withNodeId(context.getNodeId())
        .withPartitionId(context.getRaftPartition().id().id())
        .withMaxFragmentSize(context.getMaxFragmentSize())
        .withActorScheduler(context.getScheduler())
        .buildAsync();
  }
}
