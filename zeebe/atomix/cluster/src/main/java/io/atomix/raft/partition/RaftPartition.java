/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.raft.partition;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Abstract partition. */
public final class RaftPartition implements Partition, HealthMonitorable {
  public static final String PARTITION_NAME_FORMAT = "%s-partition-%d";
  private static final Logger LOG = LoggerFactory.getLogger(RaftPartition.class);
  private static final String PARTITION_COMPONENT_NAME_FORMAT = "RaftPartition-%d";

  private final PartitionId partitionId;
  private final RaftPartitionConfig config;
  private final File dataDirectory;
  private final MeterRegistry meterRegistry;
  private final Set<RaftRoleChangeListener> deferredRoleChangeListeners =
      new CopyOnWriteArraySet<>();
  private final PartitionMetadata partitionMetadata;
  private RaftPartitionServer server;

  public RaftPartition(
      final PartitionMetadata partitionMetadata,
      final RaftPartitionConfig config,
      final File dataDirectory,
      final MeterRegistry meterRegistry) {
    partitionId = partitionMetadata.id();
    this.partitionMetadata = partitionMetadata;
    this.config = config;
    this.dataDirectory = dataDirectory;
    this.meterRegistry = meterRegistry;
  }

  public void addRoleChangeListener(final RaftRoleChangeListener listener) {
    if (server == null) {
      deferredRoleChangeListeners.add(listener);
    } else {
      server.addRoleChangeListener(listener);
    }
  }

  public void removeRoleChangeListener(final RaftRoleChangeListener listener) {
    deferredRoleChangeListeners.remove(listener);
    server.removeRoleChangeListener(listener);
  }

  /**
   * Returns the partition data directory.
   *
   * @return the partition data directory
   */
  public File dataDirectory() {
    return dataDirectory;
  }

  /** Bootstraps a partition. */
  public CompletableFuture<RaftPartition> bootstrap(
      final PartitionManagementService managementService,
      final ReceivableSnapshotStore snapshotStore) {
    if (partitionMetadata
        .members()
        .contains(managementService.getMembershipService().getLocalMember().id())) {
      initServer(managementService, snapshotStore);
      return server.bootstrap().thenApply(v -> this);
    }
    return CompletableFuture.completedFuture(this);
  }

  public CompletableFuture<RaftPartition> join(
      final PartitionManagementService managementService,
      final ReceivableSnapshotStore snapshotStore) {
    initServer(managementService, snapshotStore);
    return server.join().thenApply(v -> this);
  }

  public CompletableFuture<RaftPartition> leave() {
    return server.leave().thenApply(v -> this);
  }

  private void initServer(
      final PartitionManagementService managementService,
      final ReceivableSnapshotStore snapshotStore) {
    server = createServer(managementService, snapshotStore);

    if (!deferredRoleChangeListeners.isEmpty()) {
      deferredRoleChangeListeners.forEach(server::addRoleChangeListener);
      deferredRoleChangeListeners.clear();
    }
  }

  /** Creates a Raft server. */
  private RaftPartitionServer createServer(
      final PartitionManagementService managementService,
      final ReceivableSnapshotStore snapshotStore) {
    return new RaftPartitionServer(
        this,
        config,
        managementService.getMembershipService().getLocalMember().id(),
        managementService.getMembershipService(),
        managementService.getMessagingService(),
        snapshotStore,
        partitionMetadata,
        meterRegistry);
  }

  /**
   * Returns the partition name.
   *
   * @return the partition name
   */
  public String name() {
    return String.format(PARTITION_NAME_FORMAT, partitionId.group(), partitionId.id());
  }

  @Override
  public String componentName() {
    return String.format(PARTITION_COMPONENT_NAME_FORMAT, partitionId.id());
  }

  @Override
  public HealthReport getHealthReport() {
    // name must be overridden otherwise it equals to name()
    return server.getHealthReport().withName(componentName());
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    server.addFailureListener(failureListener);
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    server.removeFailureListener(failureListener);
  }

  /** Closes the partition. */
  public CompletableFuture<Void> close() {
    return closeServer()
        .exceptionally(
            error -> {
              LOG.error("Error on shutdown partition: {}.", partitionId, error);
              return null;
            });
  }

  private CompletableFuture<Void> closeServer() {
    if (server != null) {
      return server.stop();
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Deletes the partition.
   *
   * @return future to be completed once the partition has been deleted
   */
  public CompletableFuture<Void> delete() {
    return server
        .stop()
        .thenRun(
            () -> {
              if (server != null) {
                server.delete();
              }
            });
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("partitionId", id()).toString();
  }

  @Override
  public PartitionId id() {
    return partitionId;
  }

  @Override
  public long term() {
    return server != null ? server.getTerm() : 0;
  }

  @Override
  public Collection<MemberId> members() {
    final var membersFromServer = server != null ? server.getMembers() : null;
    if (membersFromServer != null) {
      // Use members from server if available. This will reflect changes when members leave or join.
      return membersFromServer.stream().map(RaftMember::memberId).collect(Collectors.toSet());
    } else {
      // Fall back to static partition metadata so that we can still get the members of a partition
      // that hasn't been started yet. This is necessary for bootstrap.
      return partitionMetadata != null ? partitionMetadata.members() : Collections.emptyList();
    }
  }

  @Override
  public PartitionMetadata partitionMetadata() {
    return partitionMetadata;
  }

  public Role getRole() {
    return server != null ? server.getRole() : null;
  }

  public RaftPartitionServer getServer() {
    return server;
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  public CompletableFuture<Void> stepDown() {
    return server.stepDown();
  }

  /**
   * Tries to step down if the following conditions are met:
   *
   * <ul>
   *   <li>priority election is enabled
   *   <li>the partition distributor determined a primary node
   *   <li>this node is not the primary
   * </ul>
   */
  public CompletableFuture<Void> stepDownIfNotPrimary() {
    if (shouldStepDown()) {
      LOG.info(
          "Decided that {} should step down as {} from partition {} because {} is primary",
          server.getMemberId(),
          server.getRole(),
          partitionMetadata.id(),
          partitionMetadata.getPrimary().orElse(null));
      return stepDown();
    } else {
      return CompletableFuture.completedFuture(null);
    }
  }

  @VisibleForTesting
  public boolean shouldStepDown() {
    final var primary = partitionMetadata.getPrimary();
    return server != null
        && config.isPriorityElectionEnabled()
        && primary.isPresent()
        && !primary.get().equals(server.getMemberId());
  }

  public CompletableFuture<Void> stop() {
    return server.stop();
  }

  public RaftPartitionConfig getPartitionConfig() {
    return config;
  }
}
