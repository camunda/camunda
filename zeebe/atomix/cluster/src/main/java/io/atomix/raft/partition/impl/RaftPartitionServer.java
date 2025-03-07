/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.partition.impl;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.RaftApplicationEntryCommittedPositionListener;
import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.SnapshotReplicationListener;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.metrics.RaftStartupMetrics;
import io.atomix.raft.partition.RaftElectionConfig;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.RaftStorageConfig;
import io.atomix.raft.roles.RaftRole;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.zeebe.CompactionBoundInformer;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import io.atomix.utils.serializer.Serializer;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

/** {@link Partition} server. */
public class RaftPartitionServer implements HealthMonitorable {

  private final Logger log;

  private final MemberId localMemberId;
  private final RaftPartition partition;
  private final RaftPartitionConfig config;
  private final ClusterMembershipService membershipService;
  private final ClusterCommunicationService clusterCommunicator;
  private final PartitionMetadata partitionMetadata;
  private final Duration requestTimeout;
  private final Duration snapshotRequestTimeout;
  private final Duration configurationChangeTimeout;

  private final ReceivableSnapshotStore persistedSnapshotStore;
  private final RaftServer server;
  private final MeterRegistry meterRegistry;

  public RaftPartitionServer(
      final RaftPartition partition,
      final RaftPartitionConfig config,
      final MemberId localMemberId,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService clusterCommunicator,
      final ReceivableSnapshotStore persistedSnapshotStore,
      final PartitionMetadata partitionMetadata,
      final MeterRegistry meterRegistry) {
    this.partition = partition;
    this.config = config;
    this.localMemberId = localMemberId;
    this.membershipService = membershipService;
    this.clusterCommunicator = clusterCommunicator;
    this.meterRegistry = meterRegistry;
    log =
        ContextualLoggerFactory.getLogger(
            getClass(),
            LoggerContext.builder(RaftPartitionServer.class).addValue(partition.name()).build());
    this.persistedSnapshotStore = persistedSnapshotStore;
    this.partitionMetadata = partitionMetadata;
    requestTimeout = config.getRequestTimeout();
    snapshotRequestTimeout = config.getSnapshotRequestTimeout();
    configurationChangeTimeout = config.getConfigurationChangeTimeout();
    server = buildServer(meterRegistry);
  }

  public CompletableFuture<RaftPartitionServer> bootstrap() {
    final RaftStartupMetrics raftStartupMetrics =
        new RaftStartupMetrics(partition.name(), meterRegistry);
    log.info("Server bootstrapping partition {}", partition.id());
    final long bootstrapStartTime = System.currentTimeMillis();
    return server
        .bootstrap(partition.members())
        .whenComplete(
            (r, e) -> {
              if (e == null) {
                final long endTime = System.currentTimeMillis();
                raftStartupMetrics.observeBootstrapDuration(endTime - bootstrapStartTime);
                log.info(
                    "Server successfully bootstrapped partition {} in {}ms",
                    partition.id(),
                    endTime - bootstrapStartTime);
              } else {
                log.warn("Server bootstrap failed for partition {}", partition.id(), e);
              }
            })
        .thenApply(v -> this);
  }

  public CompletableFuture<RaftPartitionServer> join() {
    final var metrics = new RaftStartupMetrics(partition.name(), meterRegistry);
    final long joinStartTime = System.currentTimeMillis();
    log.info("Server joining partition {}", partition.id());
    return server
        .join(partitionMetadata.members())
        .whenComplete(
            (r, e) -> {
              if (e == null) {
                final long endTime = System.currentTimeMillis();
                metrics.observeJoinDuration(endTime - joinStartTime);
                log.info(
                    "Server successfully joined partition {} in {}ms",
                    partition.id(),
                    endTime - joinStartTime);
              } else {
                log.warn("Server join failed for partition {}", partition.id(), e);
              }
            })
        .thenApply(v -> this);
  }

  public CompletableFuture<RaftPartitionServer> leave() {
    return server.leave().thenApply(v -> this);
  }

  public CompletableFuture<RaftPartitionServer> forceReconfigure(
      final Map<MemberId, Type> members) {
    return server.forceConfigure(members).thenApply(v -> this);
  }

  public CompletableFuture<Void> stop() {
    return server != null ? server.shutdown() : CompletableFuture.completedFuture(null);
  }

  public CompletableFuture<Void> reconfigurePriority(final int newPriority) {
    return server.reconfigurePriority(newPriority);
  }

  private RaftServer buildServer(final MeterRegistry meterRegistry) {
    final var partitionId = partition.id().id();
    final var electionConfig =
        config.isPriorityElectionEnabled()
            ? RaftElectionConfig.ofPriorityElection(
                partitionMetadata.getTargetPriority(), partitionMetadata.getPriority(localMemberId))
            : RaftElectionConfig.ofDefaultElection();

    return RaftServer.builder(localMemberId)
        .withName(partition.name())
        .withPartitionId(partitionId)
        .withMembershipService(membershipService)
        .withProtocol(createServerProtocol())
        .withPartitionConfig(config)
        .withStorage(createRaftStorage())
        .withEntryValidator(config.getEntryValidator())
        .withElectionConfig(electionConfig)
        .withMeterRegistry(meterRegistry)
        .build();
  }

  public CompletableFuture<Void> flushLog() {
    return server.flushLog();
  }

  public RaftLogReader openReader() {
    return server.getContext().getLog().openCommittedReader();
  }

  public void addRoleChangeListener(final RaftRoleChangeListener listener) {
    server.addRoleChangeListener(listener);
  }

  @Override
  public String componentName() {
    return getClass().getSimpleName();
  }

  @Override
  public HealthReport getHealthReport() {
    return server.getContext().getHealthReport();
  }

  @Override
  public void addFailureListener(final FailureListener listener) {
    server.addFailureListener(listener);
  }

  @Override
  public void removeFailureListener(final FailureListener listener) {
    server.removeFailureListener(listener);
  }

  public void removeRoleChangeListener(final RaftRoleChangeListener listener) {
    server.removeRoleChangeListener(listener);
  }

  /**
   * @see io.atomix.raft.impl.RaftContext#addCommitListener(RaftCommitListener)
   */
  public void addCommitListener(final RaftCommitListener commitListener) {
    server.getContext().addCommitListener(commitListener);
  }

  /**
   * @see io.atomix.raft.impl.RaftContext#removeCommitListener(RaftCommitListener)
   */
  public void removeCommitListener(final RaftCommitListener commitListener) {
    server.getContext().removeCommitListener(commitListener);
  }

  /**
   * @see
   *     io.atomix.raft.impl.RaftContext#addCommittedEntryListener(RaftApplicationEntryCommittedPositionListener)
   */
  public void addCommittedEntryListener(
      final RaftApplicationEntryCommittedPositionListener commitListener) {
    server.getContext().addCommittedEntryListener(commitListener);
  }

  /**
   * @see
   *     io.atomix.raft.impl.RaftContext#removeCommittedEntryListener(RaftApplicationEntryCommittedPositionListener)
   */
  public void removeCommittedEntryListener(
      final RaftApplicationEntryCommittedPositionListener commitListener) {
    server.getContext().removeCommittedEntryListener(commitListener);
  }

  /**
   * @see
   *     io.atomix.raft.impl.RaftContext#addSnapshotReplicationListener(SnapshotReplicationListener)
   */
  public void addSnapshotReplicationListener(final SnapshotReplicationListener listener) {
    server.getContext().addSnapshotReplicationListener(listener);
  }

  /**
   * @see
   *     io.atomix.raft.impl.RaftContext#removeSnapshotReplicationListener(SnapshotReplicationListener)
   */
  public void removeSnapshotReplicationListener(final SnapshotReplicationListener listener) {
    server.getContext().removeSnapshotReplicationListener(listener);
  }

  public PersistedSnapshotStore getPersistedSnapshotStore() {
    return persistedSnapshotStore;
  }

  /** Deletes the server. */
  public void delete() {
    try {
      FileUtil.deleteFolderIfExists(partition.dataDirectory().toPath());
    } catch (final IOException e) {
      log.error("Failed to delete partition: {}", partition, e);
    }
  }

  public Optional<ZeebeLogAppender> getAppender() {
    final RaftRole role = server.getContext().getRaftRole();
    if (role instanceof ZeebeLogAppender) {
      return Optional.of((ZeebeLogAppender) role);
    }

    return Optional.empty();
  }

  public CompactionBoundInformer getCompactionBoundInformer() {
    final RaftRole role = server.getContext().getRaftRole();
    if (role instanceof final CompactionBoundInformer informer) {
      return informer;
    }
    throw new IllegalStateException("No compaction informer found");
  }

  public Role getRole() {
    return server.getRole();
  }

  public long getTerm() {
    return server.getTerm();
  }

  public MemberId getMemberId() {
    return localMemberId;
  }

  private RaftStorage createRaftStorage() {
    final RaftStorageConfig storageConfig = config.getStorageConfig();
    return RaftStorage.builder(meterRegistry)
        .withPrefix(partition.name())
        .withPartitionId(partition.id().id())
        .withDirectory(partition.dataDirectory())
        .withMaxSegmentSize((int) storageConfig.getSegmentSize())
        .withFlusherFactory(storageConfig.flusherFactory())
        .withFreeDiskSpace(storageConfig.getFreeDiskSpace())
        .withSnapshotStore(persistedSnapshotStore)
        .withJournalIndexDensity(storageConfig.getJournalIndexDensity())
        .withPreallocateSegmentFiles(storageConfig.isPreallocateSegmentFiles())
        .build();
  }

  private RaftServerCommunicator createServerProtocol() {
    return new RaftServerCommunicator(
        partition.name(),
        Serializer.using(RaftNamespaces.RAFT_PROTOCOL),
        clusterCommunicator,
        requestTimeout,
        snapshotRequestTimeout,
        configurationChangeTimeout,
        meterRegistry);
  }

  public CompletableFuture<Void> stepDown() {
    return server.stepDown();
  }

  public CompletableFuture<RaftServer> promote() {
    return server.promote();
  }

  public Collection<RaftMember> getMembers() {
    return server.cluster().getMembers();
  }

  public CompletableFuture<Collection<Path>> getTailSegments(final long index) {
    return server.getContext().getTailSegments(index);
  }
}
