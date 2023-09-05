/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bootstraps a raft partition and the corresponding Zeebe partition. */
final class PartitionStartup {
  private final ActorSchedulingService schedulingService;
  private final PartitionManagementService partitionManagementService;
  private final RaftPartitionFactory raftPartitionFactory;
  private final ZeebePartitionFactory zeebePartitionFactory;

  PartitionStartup(
      final ActorSchedulingService schedulingService,
      final PartitionManagementService partitionManagementService,
      final RaftPartitionFactory raftPartitionFactory,
      final ZeebePartitionFactory zeebePartitionFactory) {
    this.schedulingService = schedulingService;
    this.partitionManagementService = partitionManagementService;
    this.raftPartitionFactory = raftPartitionFactory;
    this.zeebePartitionFactory = zeebePartitionFactory;
  }

  /**
   * Bootstraps the partition with the given id. If the partition does not already exist locally, it
   * will be initialized from static configuration. If either raft or Zeebe partition fails during
   * startup, both will be stopped to avoid half-running partitions or leaked resources.
   *
   * @return future that completes successfully with the started partition when it is ready to use
   *     or exceptionally if the partition fails to start.
   */
  CompletableFuture<StartedPartition> bootstrap(final PartitionMetadata partitionMetadata) {
    final var bootstrap = new BootstrapActor(partitionMetadata);
    schedulingService.submitActor(bootstrap);
    return bootstrap.result();
  }

  record StartedPartition(RaftPartition raftPartition, ZeebePartition zeebePartition) {}

  /**
   * Orchestrates the bootstrap of a partition. This is a short-lived actor that starts the
   * bootstrap on actor start and stops itself when the bootstrap is complete.
   */
  final class BootstrapActor extends Actor {

    private static final Logger LOG =
        LoggerFactory.getLogger("io.camunda.zeebe.broker.partitioning.bootstrap");
    private final CompletableFuture<StartedPartition> result = new CompletableFuture<>();
    private final PartitionMetadata partitionMetadata;
    private final String name;

    public BootstrapActor(final PartitionMetadata partitionMetadata) {
      name = buildActorName("Bootstrap", partitionMetadata.id().id());
      this.partitionMetadata = partitionMetadata;
    }

    @Override
    protected Map<String, String> createContext() {
      final var context = super.createContext();
      context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionMetadata.id().id()));
      return context;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    protected void onActorStarted() {
      final var partitionId = partitionMetadata.id().id();
      final var raftPartition = raftPartitionFactory.createRaftPartition(partitionMetadata);
      // Call open on another thread because the raft partition will build the snapshot store which
      // needs to wait for the snapshot store actor to be started. Synchronously waiting is not
      // allowed from an actor, so delegate to another thread.
      CompletableFuture.supplyAsync(
              () -> {
                LOG.debug("Starting raft partition {}", partitionId);
                return raftPartition.open(partitionManagementService).join();
              })
          .exceptionally(
              t -> {
                LOG.error("Failed to start raft partition {}", raftPartition.id(), t);
                // close the raft partition in case it was partially started
                raftPartition.close();
                result.completeExceptionally(t);
                return null;
              })
          .thenAccept(
              startedRaftPartition -> actor.call(() -> startZeebePartition(startedRaftPartition)))
          .exceptionally(
              t -> {
                LOG.error("Failed to start zeebe partition {}", partitionId, t);
                // close the raft partition in case it was partially started
                result.completeExceptionally(t);
                return null;
              });
    }

    @Override
    protected void handleFailure(final Throwable failure) {
      result.completeExceptionally(failure);
      closeAsync();
    }

    private void startZeebePartition(final RaftPartition startedRaftPartition) {
      final var zeebePartition = zeebePartitionFactory.constructPartition(startedRaftPartition);
      runOnCompletion(
          schedulingService.submitActor(zeebePartition),
          (v, t) -> {
            if (t == null) {
              LOG.debug("Zeebe partition started successfully");
              result.complete(new StartedPartition(startedRaftPartition, zeebePartition));
            } else {
              LOG.debug("Zeebe partition failed to start");
              startedRaftPartition.close();
              zeebePartition.closeAsync();
              result.completeExceptionally(t);
            }
            closeAsync();
          });
    }

    CompletableFuture<StartedPartition> result() {
      return result;
    }
  }
}
