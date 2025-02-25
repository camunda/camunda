/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.partitioning.startup.steps.MetricsStep;
import io.camunda.zeebe.broker.partitioning.startup.steps.PartitionDirectoryStep;
import io.camunda.zeebe.broker.partitioning.startup.steps.PartitionRegistrationStep;
import io.camunda.zeebe.broker.partitioning.startup.steps.RaftBootstrapStep;
import io.camunda.zeebe.broker.partitioning.startup.steps.RaftJoinStep;
import io.camunda.zeebe.broker.partitioning.startup.steps.SnapshotStoreStep;
import io.camunda.zeebe.broker.partitioning.startup.steps.ZeebePartitionStep;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupProcess;
import io.camunda.zeebe.util.FileUtil;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the combination of a Raft and Zeebe partition. For now, the only way to construct
 * partitions is through the {@link #bootstrapping(PartitionStartupContext)} method.
 *
 * <pre>
 *   final var context = new PartitionStartupContext(...);
 *   final var partition = Partition.bootstrapping(context);
 *
 *   partition.start().join();
 *   partition.stop().join();
 * </pre>
 */
final class Partition {
  private static final Logger LOGGER = LoggerFactory.getLogger(Partition.class);

  private final PartitionStartupContext context;
  private final StartupProcess<PartitionStartupContext> startupProcess;

  private Partition(
      final PartitionStartupContext context,
      final StartupProcess<PartitionStartupContext> startupProcess) {
    this.context = context;
    this.startupProcess = startupProcess;
  }

  /**
   * Creates a partition that uses the bootstrapping process when started. Bootstrapping assumes
   * that this broker is already part of the replication group for this partition. If the broker
   * does not have the partition configuration stored locally, the initial configuration is derived
   * from the static broker configuration.
   *
   * @param context a populated context that the partition can use.
   * @return a partition that can be started.
   */
  static Partition bootstrapping(final PartitionStartupContext context) {
    return new Partition(
        context,
        new StartupProcess<>(
            LOGGER,
            List.of(
                new MetricsStep(),
                new PartitionDirectoryStep(),
                new SnapshotStoreStep(),
                new RaftBootstrapStep(),
                new ZeebePartitionStep(),
                new PartitionRegistrationStep())));
  }

  static Partition joining(final PartitionStartupContext context) {
    return new Partition(
        context,
        new StartupProcess<>(
            List.of(
                new MetricsStep(),
                new PartitionDirectoryStep(),
                new SnapshotStoreStep(),
                new RaftJoinStep(),
                new ZeebePartitionStep(),
                new PartitionRegistrationStep())));
  }

  ActorFuture<Partition> start() {
    final var concurrencyControl = context.concurrencyControl();
    final var result = concurrencyControl.<Partition>createFuture();
    concurrencyControl.run(
        () -> {
          final var start = startupProcess.startup(concurrencyControl, context);
          concurrencyControl.runOnCompletion(
              start,
              (ok, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                } else {
                  result.complete(this);
                }
              });
        });
    return result;
  }

  ActorFuture<Partition> stop() {
    final var concurrencyControl = context.concurrencyControl();
    final var result = concurrencyControl.<Partition>createFuture();
    concurrencyControl.run(
        () -> {
          final var start = startupProcess.shutdown(concurrencyControl, context);
          concurrencyControl.runOnCompletion(
              start,
              (ok, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                } else {
                  result.complete(this);
                }
              });
        });
    return result;
  }

  /** Requests to leave the partition and shuts down on success. Partition data is not deleted. */
  ActorFuture<Partition> leave() {
    final var concurrencyControl = context.concurrencyControl();
    final var result = concurrencyControl.<Partition>createFuture();
    concurrencyControl.run(
        () -> {
          final var raftPartition = raftPartition();
          if (raftPartition == null) {
            result.completeExceptionally(errorPartitionNotAvailable("leave"));
            return;
          }
          raftPartition
              .leave()
              .whenComplete(
                  (leaveOk, leaveError) ->
                      concurrencyControl.run(() -> onPartitionLeaveCompleted(leaveError, result)));
        });

    return result;
  }

  private void onPartitionLeaveCompleted(
      final Throwable leaveError, final ActorFuture<Partition> result) {
    if (leaveError != null) {
      result.completeExceptionally(leaveError);
      return;
    }

    final var concurrencyControl = context.concurrencyControl();
    final var partitionDirectory = context.partitionDirectory();
    concurrencyControl.runOnCompletion(
        startupProcess.shutdown(concurrencyControl, context),
        (shutdownOk, shutdownError) -> {
          if (shutdownError != null) {
            result.completeExceptionally(shutdownError);
            return;
          }
          try {
            FileUtil.deleteFolderIfExists(partitionDirectory);
          } catch (final Exception e) {
            LOGGER.warn(
                "Failed to delete partition directory {} after leaving. Data will remain until manually removed.",
                partitionDirectory,
                e);
          }
          result.complete(this);
        });
  }

  ActorFuture<Void> reconfigurePriority(final int newPriority) {
    final var concurrencyControl = context.concurrencyControl();
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          final var raftPartition = raftPartition();
          if (raftPartition == null) {
            result.completeExceptionally(errorPartitionNotAvailable("reconfigure priority of"));
            return;
          }
          raftPartition
              .getServer()
              .reconfigurePriority(newPriority)
              .whenComplete(
                  (configureOk, configureError) -> {
                    if (configureError != null) {
                      result.completeExceptionally(configureError);
                    } else {
                      result.complete(null);
                    }
                  });
        });

    return result;
  }

  ActorFuture<Void> forceReconfigure(final Collection<MemberId> members) {
    final var concurrencyControl = context.concurrencyControl();
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          final var raftPartition = raftPartition();
          if (raftPartition == null) {
            result.completeExceptionally(errorPartitionNotAvailable("force reconfigure"));
            return;
          }
          // Here we assume that the members are all active, since we do not support PASSIVE members
          // yet.
          final var membersWithType =
              members.stream().collect(Collectors.toMap(m -> m, m -> Type.ACTIVE));
          raftPartition
              .getServer()
              .forceReconfigure(membersWithType)
              .whenComplete(
                  (configureOk, configureError) -> {
                    if (configureError != null) {
                      result.completeExceptionally(configureError);
                    } else {
                      result.complete(null);
                    }
                  });
        });

    return result;
  }

  private IllegalStateException errorPartitionNotAvailable(final String operation) {
    return new IllegalStateException(
        String.format(
            "Expected to %s partition %s, but raft partition is not available",
            operation, context.partitionMetadata().id().id()));
  }

  ZeebePartition zeebePartition() {
    return context.zeebePartition();
  }

  RaftPartition raftPartition() {
    return context.raftPartition();
  }

  int id() {
    return context.partitionMetadata().id().id();
  }
}
