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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.Recovery;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionGroupConfig;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.ProxyProtocol;
import io.atomix.raft.MultiRaftProtocol;
import io.atomix.raft.RaftClient;
import io.atomix.raft.RaftStateMachineFactory;
import io.atomix.raft.impl.DefaultRaftClient;
import io.atomix.raft.storage.snapshot.SnapshotStoreFactory;
import io.atomix.storage.StorageLevel;
import io.atomix.utils.concurrent.BlockingAwareThreadPoolContextFactory;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.ThreadContextFactory;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import io.atomix.utils.memory.MemorySize;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Raft partition group. */
public class RaftPartitionGroup implements ManagedPartitionGroup {

  public static final Type TYPE = new Type();
  private static final Logger LOGGER = LoggerFactory.getLogger(RaftPartitionGroup.class);
  private static final Duration SNAPSHOT_TIMEOUT = Duration.ofSeconds(15);
  private final String name;
  private final RaftPartitionGroupConfig config;
  private final int partitionSize;
  private final ThreadContextFactory threadContextFactory;
  private final Map<PartitionId, RaftPartition> partitions = Maps.newConcurrentMap();
  private final List<PartitionId> sortedPartitionIds = Lists.newCopyOnWriteArrayList();
  private final String snapshotSubject;
  private Collection<PartitionMetadata> metadata;
  private ClusterCommunicationService communicationService;

  public RaftPartitionGroup(final RaftPartitionGroupConfig config) {
    final Logger log =
        ContextualLoggerFactory.getLogger(
            DefaultRaftClient.class,
            LoggerContext.builder(RaftClient.class).addValue(config.getName()).build());
    this.name = config.getName();
    this.config = config;
    this.partitionSize = config.getPartitionSize();

    final int threadPoolSize =
        Math.max(Math.min(Runtime.getRuntime().availableProcessors() * 2, 16), 4);
    this.threadContextFactory =
        new BlockingAwareThreadPoolContextFactory(
            "raft-partition-group-" + name + "-%d", threadPoolSize, log);
    this.snapshotSubject = "raft-partition-group-" + name + "-snapshot";

    buildPartitions(config, threadContextFactory)
        .forEach(
            p -> {
              this.partitions.put(p.id(), p);
              this.sortedPartitionIds.add(p.id());
            });
    Collections.sort(sortedPartitionIds);
  }

  private static Collection<RaftPartition> buildPartitions(
      final RaftPartitionGroupConfig config, final ThreadContextFactory threadContextFactory) {
    final File partitionsDir =
        new File(config.getStorageConfig().getDirectory(config.getName()), "partitions");
    final List<RaftPartition> partitions = new ArrayList<>(config.getPartitions());
    for (int i = 0; i < config.getPartitions(); i++) {
      partitions.add(
          new RaftPartition(
              PartitionId.from(config.getName(), i + 1),
              config,
              new File(partitionsDir, String.valueOf(i + 1)),
              threadContextFactory));
    }
    return partitions;
  }

  /**
   * Returns a new Raft partition group builder.
   *
   * @param name the partition group name
   * @return a new partition group builder
   */
  public static Builder builder(final String name) {
    return new Builder(new RaftPartitionGroupConfig().setName(name));
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public PartitionGroup.Type type() {
    return TYPE;
  }

  @Override
  public PrimitiveProtocol.Type protocol() {
    return MultiRaftProtocol.TYPE;
  }

  @Override
  public ProxyProtocol newProtocol() {
    return MultiRaftProtocol.builder(name)
        .withRecoveryStrategy(Recovery.RECOVER)
        .withMaxRetries(5)
        .build();
  }

  @Override
  public RaftPartition getPartition(final PartitionId partitionId) {
    return partitions.get(partitionId);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Collection<Partition> getPartitions() {
    return (Collection) partitions.values();
  }

  @Override
  public List<PartitionId> getPartitionIds() {
    return sortedPartitionIds;
  }

  @Override
  public PartitionGroupConfig config() {
    return config;
  }

  /**
   * Takes snapshots of all Raft partitions.
   *
   * @return a future to be completed once snapshots have been taken
   */
  public CompletableFuture<Void> snapshot() {
    return Futures.allOf(
            config.getMembers().stream()
                .map(MemberId::from)
                .map(id -> communicationService.send(snapshotSubject, null, id, SNAPSHOT_TIMEOUT))
                .collect(Collectors.toList()))
        .thenApply(v -> null);
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("name", name).add("partitions", partitions).toString();
  }

  /**
   * Handles a snapshot request from a peer.
   *
   * @return a future to be completed once the snapshot is complete
   */
  private CompletableFuture<Void> handleSnapshot() {
    return Futures.allOf(
            partitions.values().stream()
                .map(partition -> partition.snapshot())
                .collect(Collectors.toList()))
        .thenApply(v -> null);
  }

  @Override
  public CompletableFuture<ManagedPartitionGroup> join(
      final PartitionManagementService managementService) {

    // We expect to bootstrap partitions where leadership is equally distributed.
    // First member of a PartitionMetadata is the bootstrap leader
    this.metadata = buildPartitions();
    // +------------------+----+----+----+---+
    // | Partition \ Node | 0  | 1  | 2  | 3 |
    // +------------------+----+----+----+---+
    // |                1 | L  | F  | F  |   |
    // |                2 |    | L  | F  | F |
    // |                3 | F  |    | L  | F |
    // |                4 | F  | F  |    | L |
    // |                5 | L  | F  | F  |   |
    // +------------------+----+----+----+---+

    this.communicationService = managementService.getMessagingService();
    communicationService.<Void, Void>subscribe(snapshotSubject, m -> handleSnapshot());
    final List<CompletableFuture<Partition>> futures =
        metadata.stream()
            .map(
                metadata -> {
                  final RaftPartition partition = partitions.get(metadata.id());
                  return partition.open(metadata, managementService);
                })
            .collect(Collectors.toList());
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
        .thenApply(
            v -> {
              LOGGER.info("Started");
              return this;
            });
  }

  @Override
  public CompletableFuture<ManagedPartitionGroup> connect(
      final PartitionManagementService managementService) {
    return join(managementService);
  }

  @Override
  public CompletableFuture<Void> close() {
    final List<CompletableFuture<Void>> futures =
        partitions.values().stream().map(RaftPartition::close).collect(Collectors.toList());
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
        .thenRun(
            () -> {
              threadContextFactory.close();
              if (communicationService != null) {
                communicationService.unsubscribe(snapshotSubject);
              }

              LOGGER.info("Stopped");
            });
  }

  private Collection<PartitionMetadata> buildPartitions() {
    final List<MemberId> sorted =
        new ArrayList<>(
            config.getMembers().stream().map(MemberId::from).collect(Collectors.toSet()));
    Collections.sort(sorted);

    int partitionSize = this.partitionSize;
    if (partitionSize == 0) {
      partitionSize = sorted.size();
    }

    final int length = sorted.size();
    final int count = Math.min(partitionSize, length);

    final Set<PartitionMetadata> metadata = Sets.newHashSet();
    for (int i = 0; i < partitions.size(); i++) {
      final PartitionId partitionId = sortedPartitionIds.get(i);
      final List<MemberId> membersForPartition = new ArrayList<>(count);
      for (int j = 0; j < count; j++) {
        membersForPartition.add(sorted.get((i + j) % length));
      }
      metadata.add(new PartitionMetadata(partitionId, membersForPartition));
    }
    return metadata;
  }

  /** Raft partition group type. */
  public static class Type implements PartitionGroup.Type<RaftPartitionGroupConfig> {

    private static final String NAME = "raft";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public Namespace namespace() {
      return Namespace.builder()
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID + 100)
          .register(RaftPartitionGroupConfig.class)
          .register(RaftStorageConfig.class)
          .register(RaftCompactionConfig.class)
          .register(StorageLevel.class)
          .build();
    }

    @Override
    public ManagedPartitionGroup newPartitionGroup(final RaftPartitionGroupConfig config) {
      return new RaftPartitionGroup(config);
    }

    @Override
    public RaftPartitionGroupConfig newConfig() {
      return new RaftPartitionGroupConfig();
    }
  }

  /** Raft partition group builder. */
  public static class Builder extends PartitionGroup.Builder<RaftPartitionGroupConfig> {

    protected Builder(final RaftPartitionGroupConfig config) {
      super(config);
    }

    /**
     * Sets the Raft partition group members.
     *
     * @param members the Raft partition group members
     * @return the Raft partition group builder
     * @throws NullPointerException if the members are null
     */
    public Builder withMembers(final String... members) {
      return withMembers(Arrays.asList(members));
    }

    /**
     * Sets the Raft partition group members.
     *
     * @param members the Raft partition group members
     * @return the Raft partition group builder
     * @throws NullPointerException if the members are null
     */
    public Builder withMembers(final Collection<String> members) {
      config.setMembers(Sets.newHashSet(checkNotNull(members, "members cannot be null")));
      return this;
    }

    /**
     * Sets the Raft partition group members.
     *
     * @param members the Raft partition group members
     * @return the Raft partition group builder
     * @throws NullPointerException if the members are null
     */
    public Builder withMembers(final MemberId... members) {
      return withMembers(
          Stream.of(members).map(nodeId -> nodeId.id()).collect(Collectors.toList()));
    }

    /**
     * Sets the Raft partition group members.
     *
     * @param members the Raft partition group members
     * @return the Raft partition group builder
     * @throws NullPointerException if the members are null
     */
    public Builder withMembers(final Member... members) {
      return withMembers(
          Stream.of(members).map(node -> node.id().id()).collect(Collectors.toList()));
    }

    /**
     * Sets the number of partitions.
     *
     * @param numPartitions the number of partitions
     * @return the Raft partition group builder
     * @throws IllegalArgumentException if the number of partitions is not positive
     */
    public Builder withNumPartitions(final int numPartitions) {
      config.setPartitions(numPartitions);
      return this;
    }

    /**
     * Sets the partition size.
     *
     * @param partitionSize the partition size
     * @return the Raft partition group builder
     * @throws IllegalArgumentException if the partition size is not positive
     */
    public Builder withPartitionSize(final int partitionSize) {
      config.setPartitionSize(partitionSize);
      return this;
    }

    /**
     * Sets the leader election timeout.
     *
     * @param electionTimeout the leader election timeout
     * @return the Raft partition group configuration
     */
    public Builder withElectionTimeout(final Duration electionTimeout) {
      config.setElectionTimeout(electionTimeout);
      return this;
    }

    /**
     * Sets the heartbeat interval.
     *
     * @param heartbeatInterval the heartbeat interval
     * @return the Raft partition group configuration
     */
    public Builder withHeartbeatInterval(final Duration heartbeatInterval) {
      config.setHeartbeatInterval(heartbeatInterval);
      return this;
    }

    /**
     * Sets the default session timeout.
     *
     * @param defaultSessionTimeout the default session timeout
     * @return the Raft partition group configuration
     */
    public Builder withDefaultSessionTimeout(final Duration defaultSessionTimeout) {
      config.setDefaultSessionTimeout(defaultSessionTimeout);
      return this;
    }

    /**
     * Sets the storage level.
     *
     * @param storageLevel the storage level
     * @return the Raft partition group builder
     */
    public Builder withStorageLevel(final StorageLevel storageLevel) {
      config.getStorageConfig().setLevel(storageLevel);
      return this;
    }

    /**
     * Sets the path to the data directory.
     *
     * @param dataDir the path to the replica's data directory
     * @return the replica builder
     */
    public Builder withDataDirectory(final File dataDir) {
      config
          .getStorageConfig()
          .setDirectory(new File("user.dir").toURI().relativize(dataDir.toURI()).getPath());
      return this;
    }

    /**
     * Sets the segment size.
     *
     * @param segmentSizeBytes the segment size in bytes
     * @return the Raft partition group builder
     */
    public Builder withSegmentSize(final long segmentSizeBytes) {
      return withSegmentSize(new MemorySize(segmentSizeBytes));
    }

    /**
     * Sets the segment size.
     *
     * @param segmentSize the segment size
     * @return the Raft partition group builder
     */
    public Builder withSegmentSize(final MemorySize segmentSize) {
      config.getStorageConfig().setSegmentSize(segmentSize);
      return this;
    }

    /**
     * Sets the maximum Raft log entry size.
     *
     * @param maxEntrySize the maximum Raft log entry size
     * @return the Raft partition group builder
     */
    public Builder withMaxEntrySize(final int maxEntrySize) {
      return withMaxEntrySize(new MemorySize(maxEntrySize));
    }

    /**
     * Sets the maximum Raft log entry size.
     *
     * @param maxEntrySize the maximum Raft log entry size
     * @return the Raft partition group builder
     */
    public Builder withMaxEntrySize(final MemorySize maxEntrySize) {
      config.getStorageConfig().setMaxEntrySize(maxEntrySize);
      return this;
    }

    /**
     * Enables flush on commit.
     *
     * @return the Raft partition group builder
     */
    public Builder withFlushOnCommit() {
      return withFlushOnCommit(true);
    }

    /**
     * Sets whether to flush logs to disk on commit.
     *
     * @param flushOnCommit whether to flush logs to disk on commit
     * @return the Raft partition group builder
     */
    public Builder withFlushOnCommit(final boolean flushOnCommit) {
      config.getStorageConfig().setFlushOnCommit(flushOnCommit);
      return this;
    }

    /**
     * Sets the Raft state machine factory to use.
     *
     * @param stateMachineFactory the new state machine factory to use
     * @return the Raft partition group builder
     */
    public Builder withStateMachineFactory(final RaftStateMachineFactory stateMachineFactory) {
      config.setStateMachineFactory(stateMachineFactory);
      return this;
    }

    /**
     * Sets the Raft snapshot store factory to use.
     *
     * @param snapshotStoreFactory the new snapshot store factory to use
     * @return the Raft partition group builder
     */
    public Builder withSnapshotStoreFactory(final SnapshotStoreFactory snapshotStoreFactory) {
      config.getStorageConfig().setSnapshotStoreFactory(snapshotStoreFactory);
      return this;
    }

    @Override
    public RaftPartitionGroup build() {
      return new RaftPartitionGroup(config);
    }
  }
}
