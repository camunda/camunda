/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static io.camunda.zeebe.util.Either.left;
import static io.camunda.zeebe.util.Either.right;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.logstreams.rdbms.LogBlockRepository;
import io.camunda.zeebe.broker.logstreams.rdbms.LogBlockRepositoryException;
import io.camunda.zeebe.broker.logstreams.rdbms.RdbmsLogStorage;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.RecoverablePartitionTransitionException;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LogStoragePartitionTransitionStep implements PartitionTransitionStep {
  private static final Logger LOG =
      LoggerFactory.getLogger(LogStoragePartitionTransitionStep.class);
  private static final Duration SCHEMA_POLL_INTERVAL = Duration.ofSeconds(1);
  private static final Duration INITIAL_CLAIM_RETRY_BACKOFF = Duration.ofMillis(500);
  private static final Duration MAX_CLAIM_RETRY_BACKOFF = Duration.ofSeconds(10);

  /**
   * Bumped whenever a new Raft role is announced, so that an RDBMS log storage installation still
   * waiting for the database gives way to the transition that follows it.
   */
  private final AtomicLong roleAnnouncements = new AtomicLong();

  @Override
  public void onNewRaftRole(final PartitionTransitionContext context, final Role newRole) {
    roleAnnouncements.incrementAndGet();
  }

  private static final String WRONG_TERM_ERROR_MSG =
      "Expected that current term '%d' is same as raft term '%d', but was not. Failing installation of 'LogStoragePartitionStep' on partition %d.";

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {

    final var logStorage = context.getLogStorage();
    if (logStorage != null
        && (shouldInstallOnTransition(targetRole, context.getCurrentRole())
            || targetRole == Role.INACTIVE)) {
      if (logStorage instanceof final AtomixLogStorage atomixLogStorage) {
        final var server = context.getRaftPartition().getServer();
        server.removeCommitListener(atomixLogStorage);
        server.removeCommittedEntryListener(atomixLogStorage.committedPositionNotifier());
      } else if (logStorage instanceof final RdbmsLogStorage rdbmsLogStorage) {
        rdbmsLogStorage.close();
      }
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

      final var repository = context.getLogBlockRepository();
      if (repository != null) {
        new RdbmsLogStorageInstallation(context, repository, term, targetRole, openFuture)
            .attempt();
        return openFuture;
      }

      final var logStorageOrException = buildAtomixLogStorage(context, term, targetRole);

      if (logStorageOrException.isRight()) {
        final var logStorage = logStorageOrException.get();
        context.setLogStorage(logStorage);
        final var server = context.getRaftPartition().getServer();
        server.addCommitListener(logStorage);
        server.addCommittedEntryListener(logStorage.committedPositionNotifier());
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
            server::openUncommittedReader,
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
                    new NotLeaderException(
                        "Expected to get writable log storage, but the node is not the leader for the partition anymore. Failing installation of 'LogStoragePartitionStep'.")));
  }

  private Either<Exception, AtomixLogStorage> checkAndCreateAtomixLogStorage(
      final PartitionTransitionContext context,
      final RaftPartitionServer server,
      final ZeebeLogAppender logAppender,
      final long targetTerm) {
    final var raftTerm = server.getTerm();

    if (raftTerm != targetTerm) {
      return left(
          new NotLeaderException(
              String.format(WRONG_TERM_ERROR_MSG, targetTerm, raftTerm, context.getPartitionId())));
    } else {
      final var logStorage =
          AtomixLogStorage.ofPartition(
              server::openReader, server::openUncommittedReader, logAppender);
      return right(logStorage);
    }
  }

  /**
   * A leader claims the partition in the database for its Raft term before it may append, so that a
   * deposed leader's appends are rejected. Raft still elects the leader; the database only enforces
   * that a newer term's claim wins.
   */
  private Either<Exception, RdbmsLogStorage> buildRdbmsLogStorage(
      final PartitionTransitionContext context,
      final LogBlockRepository repository,
      final long targetTerm,
      final Role targetRole) {
    if (targetRole != Role.LEADER) {
      return right(RdbmsLogStorage.readOnly(repository, RdbmsLogStorage.Config.DEFAULT));
    }

    final var raftPartition = context.getRaftPartition();
    final var raftTerm = raftPartition.getServer().getTerm();
    if (raftTerm != targetTerm) {
      return left(
          new NotLeaderException(
              String.format(WRONG_TERM_ERROR_MSG, targetTerm, raftTerm, context.getPartitionId())));
    }

    try {
      if (!repository.claimTerm(targetTerm)) {
        return left(
            new NotLeaderException(
                "Expected to claim partition %d for term %d in the database, but a higher term already holds it. Failing installation of 'LogStoragePartitionStep'."
                    .formatted(context.getPartitionId(), targetTerm)));
      }
    } catch (final LogBlockRepositoryException e) {
      return left(e);
    }

    final var partitionId = context.getPartitionId();
    return right(
        RdbmsLogStorage.writable(
            "rdbms-log-writer-" + partitionId,
            repository,
            targetTerm,
            RdbmsLogStorage.Config.DEFAULT,
            context.getPartitionTransitionMeterRegistry(),
            error -> {
              // Raft keeps this node leader while the database fails, so step down explicitly;
              // unless a newer term has begun, which this failure says nothing about
              if (raftPartition.term() == targetTerm) {
                LOG.warn(
                    "RDBMS log storage of partition {} failed in term {}, stepping down",
                    partitionId,
                    targetTerm);
                raftPartition.stepDown();
              }
            }));
  }

  /**
   * Installs the RDBMS log storage without blocking the partition: waits for the schema to exist,
   * which on a fresh database happens only after the broker has started, and retries a leader's
   * claim that failed on the database. Gives way once a newer transition is announced.
   */
  private final class RdbmsLogStorageInstallation {

    private final PartitionTransitionContext context;
    private final LogBlockRepository repository;
    private final long term;
    private final Role targetRole;
    private final CompletableActorFuture<Void> future;
    private final long roleAnnouncement = roleAnnouncements.get();
    private Duration claimRetryBackoff = INITIAL_CLAIM_RETRY_BACKOFF;
    private boolean loggedWaitingForSchema;

    private RdbmsLogStorageInstallation(
        final PartitionTransitionContext context,
        final LogBlockRepository repository,
        final long term,
        final Role targetRole,
        final CompletableActorFuture<Void> future) {
      this.context = context;
      this.repository = repository;
      this.term = term;
      this.targetRole = targetRole;
      this.future = future;
    }

    private void attempt() {
      if (roleAnnouncements.get() != roleAnnouncement) {
        future.completeExceptionally(
            new TransitionSupersededException(
                "Stopped installing the RDBMS log storage of partition %d for %s in term %d, a newer transition was announced"
                    .formatted(context.getPartitionId(), targetRole, term)));
        return;
      }

      if (!repository.isReady()) {
        if (!loggedWaitingForSchema) {
          LOG.info(
              "Waiting for the RDBMS schema before installing the log storage of partition {}",
              context.getPartitionId());
          loggedWaitingForSchema = true;
        }
        context.getConcurrencyControl().schedule(SCHEMA_POLL_INTERVAL, this::attempt);
        return;
      }

      final var logStorageOrException = buildRdbmsLogStorage(context, repository, term, targetRole);
      if (logStorageOrException.isRight()) {
        context.setLogStorage(logStorageOrException.get());
        future.complete(null);
      } else if (logStorageOrException.getLeft() instanceof final LogBlockRepositoryException e) {
        LOG.warn(
            "Failed to claim partition {} for term {} in the database, retrying in {}",
            context.getPartitionId(),
            term,
            claimRetryBackoff,
            e);
        context.getConcurrencyControl().schedule(claimRetryBackoff, this::attempt);
        claimRetryBackoff = min(claimRetryBackoff.multipliedBy(2), MAX_CLAIM_RETRY_BACKOFF);
      } else {
        future.completeExceptionally(logStorageOrException.getLeft());
      }
    }

    private static Duration min(final Duration a, final Duration b) {
      return a.compareTo(b) <= 0 ? a : b;
    }
  }

  public static final class TransitionSupersededException
      extends RecoverablePartitionTransitionException {

    private TransitionSupersededException(final String message) {
      super(message);
    }
  }

  public static final class NotLeaderException extends RecoverablePartitionTransitionException {

    private NotLeaderException(final String message) {
      super(message);
    }
  }

  private static final class LogAppenderForReadOnlyStorage implements ZeebeLogAppender {

    @Override
    public void appendEntry(final ApplicationEntry entry, final AppendListener appendListener) {
      throw new UnsupportedOperationException(
          String.format(
              "Expect to append entry (positions %d - %d), but was in Follower role. Followers must not append entries to the log storage",
              entry.lowestPosition(), entry.highestPosition()));
    }
  }
}
