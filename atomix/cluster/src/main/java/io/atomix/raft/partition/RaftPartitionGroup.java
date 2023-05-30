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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionGroupConfig;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.zeebe.EntryValidator;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.memory.MemorySize;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStoreFactory;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
  private final int replicationFactor;
  private final Map<PartitionId, RaftPartition> partitions = Maps.newConcurrentMap();
  private final List<PartitionId> sortedPartitionIds = Lists.newCopyOnWriteArrayList();
  private final String snapshotSubject;
  private Collection<PartitionMetadata> metadata;
  private ClusterCommunicationService communicationService;

  public RaftPartitionGroup(final RaftPartitionGroupConfig config) {
    this.config = config;

    name = config.getName();
    replicationFactor = config.getReplicationFactor();
    snapshotSubject = "raft-partition-group-" + name + "-snapshot";

    buildPartitions(config)
        .forEach(
            p -> {
              partitions.put(p.id(), p);
              sortedPartitionIds.add(p.id());
            });
    Collections.sort(sortedPartitionIds);
  }

  private static Collection<RaftPartition> buildPartitions(final RaftPartitionGroupConfig config) {
    final File partitionsDir =
        new File(config.getStorageConfig().getDirectory(config.getName()), "partitions");
    final List<RaftPartition> partitions = new ArrayList<>(config.getPartitionCount());
    for (int i = 0; i < config.getPartitionCount(); i++) {
      partitions.add(
          new RaftPartition(
              PartitionId.from(config.getName(), i + 1),
              config,
              new File(partitionsDir, String.valueOf(i + 1))));
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
  public RaftPartition getPartition(final int partitionId) {
    return getPartition(PartitionId.from(name, partitionId));
  }

  @Override
  public RaftPartition getPartition(final PartitionId partitionId) {
    return partitions.get(partitionId);
  }

  @Override
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
    final var members =
        config.getMembers().stream().map(MemberId::from).collect(Collectors.toSet());
    metadata =
        config
            .getPartitionConfig()
            .getPartitionDistributor()
            .distributePartitions(members, sortedPartitionIds, replicationFactor);

    communicationService = managementService.getMessagingService();
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
              if (communicationService != null) {
                communicationService.unsubscribe(snapshotSubject);
              }

              LOGGER.info("Stopped");
            });
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
      return new Namespace.Builder()
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID + 100)
          .register(RaftPartitionGroupConfig.class)
          .register(RaftStorageConfig.class)
          .build();
    }

    @Override
    public ManagedPartitionGroup newPartitionGroup(final RaftPartitionGroupConfig config) {
      return new RaftPartitionGroup(config);
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
      config.setPartitionCount(numPartitions);
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
      config.setReplicationFactor(partitionSize);
      return this;
    }

    /**
     * Sets the maximum append requests which are sent per follower at once. Default is 2.
     *
     * @param maxAppendsPerFollower the maximum appends send per follower
     * @return the Raft partition group builder
     */
    public Builder withMaxAppendsPerFollower(final int maxAppendsPerFollower) {
      checkArgument(maxAppendsPerFollower > 0, "maxAppendsPerFollower must be positive");
      config.getPartitionConfig().setMaxAppendsPerFollower(maxAppendsPerFollower);
      return this;
    }

    /**
     * Sets the maximum batch size, which is sent per append request. Default size is 32 KB.
     *
     * @param maxAppendBatchSize the maximum batch size per append
     * @return the Raft partition group builder
     */
    public Builder withMaxAppendBatchSize(final int maxAppendBatchSize) {
      checkArgument(maxAppendBatchSize > 0, "maxAppendBatchSize must be positive");
      config.getPartitionConfig().setMaxAppendBatchSize(maxAppendBatchSize);
      return this;
    }

    /**
     * Sets the heartbeatInterval. The leader will send heartbeats to a follower at this interval.
     *
     * @param heartbeatInterval the delay between two heartbeats
     * @return the Raft partition group builder
     */
    public Builder withHeartbeatInterval(final Duration heartbeatInterval) {
      checkArgument(heartbeatInterval.toMillis() > 0, "heartbeatInterval must be atleast 1ms");
      config.getPartitionConfig().setHeartbeatInterval(heartbeatInterval);
      return this;
    }

    /**
     * Sets the election timeout. If a follower does not receive a heartbeat from the leader within
     * election timeout, it can start a new leader election.
     *
     * @param electionTimeout the election timeout
     * @return the Raft partition group builder
     */
    public Builder withElectionTimeout(final Duration electionTimeout) {
      checkArgument(electionTimeout.toMillis() > 0, "heartbeatInterval must be atleast 1ms");
      config.getPartitionConfig().setElectionTimeout(electionTimeout);
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
     * Set the minimum free disk space (in bytes) to leave when allocating a new segment
     *
     * @param freeDiskSpace free disk space in bytes
     * @return the Raft partition group builder
     */
    public Builder withFreeDiskSpace(final long freeDiskSpace) {
      config.getStorageConfig().setFreeDiskSpace(freeDiskSpace);
      return this;
    }

    /**
     * Sets whether to flush logs to disk on commit.
     *
     * @param flushExplicitly whether to flush logs to disk on commit
     * @return the Raft partition group builder
     */
    public Builder withFlushExplicitly(final boolean flushExplicitly) {
      config.getStorageConfig().setFlushExplicitly(flushExplicitly);
      return this;
    }

    /**
     * Sets the Raft snapshot store factory to use.
     *
     * @param persistedSnapshotStoreFactory the new snapshot store factory to use
     * @return the Raft partition group builder
     */
    public Builder withSnapshotStoreFactory(
        final ReceivableSnapshotStoreFactory persistedSnapshotStoreFactory) {
      config.getStorageConfig().setPersistedSnapshotStoreFactory(persistedSnapshotStoreFactory);
      return this;
    }

    /**
     * Sets the entry validator to be called when an entry is appended.
     *
     * @param entryValidator the entry validator
     * @return the Raft Partition group builder
     */
    public Builder withEntryValidator(final EntryValidator entryValidator) {
      config.setEntryValidator(entryValidator);
      return this;
    }

    public Builder withJournalIndexDensity(final int journalIndexDensity) {
      config.getStorageConfig().setJournalIndexDensity(journalIndexDensity);
      return this;
    }

    public Builder withPriorityElection(final boolean enable) {
      config.getPartitionConfig().setPriorityElectionEnabled(enable);
      return this;
    }

    /**
     * Sets the timeout for all messages sent between raft replicas.
     *
     * @param requestTimeout the timeout
     * @return the Raft Partition group builder
     */
    public Builder withRequestTimeout(final Duration requestTimeout) {
      config.getPartitionConfig().setRequestTimeout(requestTimeout);
      return this;
    }

    /**
     * Sets the snapshot request timeout for all messages sent between raft replicas.
     *
     * @param snapshotRequestTimeout the timeout
     * @return the Raft Partition group builder
     */
    public Builder withSnapshotRequestTimeout(final Duration snapshotRequestTimeout) {
      config.getPartitionConfig().setSnapshotRequestTimeout(snapshotRequestTimeout);
      return this;
    }

    /**
     * If the leader is not able to reach the quorum, the leader may step down. This is triggered
     * after minStepDownFailureCount number of requests fails to get a response from the quorum of
     * followers as well as if the last response was received before maxQuorumResponseTime.
     *
     * @param minStepDownFailureCount The number of failures after which a leader considers stepping
     *     down.
     * @return the Raft Partition group builder
     */
    public Builder withMinStepDownFailureCount(final int minStepDownFailureCount) {
      config.getPartitionConfig().setMinStepDownFailureCount(minStepDownFailureCount);
      return this;
    }

    /**
     * If the leader is not able to reach the quorum, the leader may step down. This is triggered *
     * after minStepDownFailureCount number of requests fails to get a response from the quorum of *
     * followers as well as if the last response was received before maxQuorumResponseTime.
     *
     * <p>When this value is 0, it will use a default value of electionTimeout * 2.
     *
     * @param maxQuorumResponseTimeout the quorum response timeout
     * @return the Raft Partition group builder
     */
    public Builder withMaxQuorumResponseTimeout(final Duration maxQuorumResponseTimeout) {
      config.getPartitionConfig().setMaxQuorumResponseTimeout(maxQuorumResponseTimeout);
      return this;
    }

    /**
     * Sets the partition distributor to use. The partition distributor determines which members
     * will own which partitions, and ensures they are correctly replicated.
     *
     * @param partitionDistributor the partition distributor to use
     * @return this builder for chaining
     */
    public Builder withPartitionDistributor(final PartitionDistributor partitionDistributor) {
      config.getPartitionConfig().setPartitionDistributor(partitionDistributor);
      return this;
    }

    /**
     * Sets the threshold for preferring snapshot replication. The unit is <i>number of records</i>
     * by which a follower may lag behind before the leader starts to prefer replicating snapshots
     * instead of records.
     *
     * @param preferSnapshotReplicationThreshold the threshold to use
     * @return this builder for chaining
     */
    public Builder withPreferSnapshotReplicationThreshold(
        final int preferSnapshotReplicationThreshold) {
      config
          .getPartitionConfig()
          .setPreferSnapshotReplicationThreshold(preferSnapshotReplicationThreshold);
      return this;
    }

    /**
     * Sets whether segment files are pre-allocated at creation. If true, segment files are
     * pre-allocated to the maximum segment size (see {@link #withSegmentSize(long)}) at creation
     * before any writes happen.
     *
     * @param preallocateSegmentFiles true to preallocate files, false otherwise
     * @return this builder for chaining
     */
    public Builder withPreallocateSegmentFiles(final boolean preallocateSegmentFiles) {
      config.getStorageConfig().setPreallocateSegmentFiles(preallocateSegmentFiles);
      return this;
    }

    @Override
    public RaftPartitionGroup build() {
      return new RaftPartitionGroup(config);
    }
  }
}
