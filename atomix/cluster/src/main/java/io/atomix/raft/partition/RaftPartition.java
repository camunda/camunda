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
import io.atomix.raft.RaftFailureListener;
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.impl.RaftClientCommunicator;
import io.atomix.raft.partition.impl.RaftNamespaces;
import io.atomix.raft.partition.impl.RaftPartitionClient;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.storage.journal.index.JournalIndex;
import io.atomix.utils.concurrent.ThreadContextFactory;
import io.atomix.utils.serializer.Serializer;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Abstract partition. */
public class RaftPartition implements Partition {

  private final PartitionId partitionId;
  private final RaftPartitionGroupConfig config;
  private final File dataDirectory;
  private final ThreadContextFactory threadContextFactory;
  private final Set<RaftRoleChangeListener> deferredRoleChangeListeners =
      new CopyOnWriteArraySet<>();
  private final Set<RaftFailureListener> raftFailureListeners = new CopyOnWriteArraySet<>();
  private PartitionMetadata partitionMetadata;
  private RaftPartitionClient client;
  private RaftPartitionServer server;
  private Supplier<JournalIndex> journalIndexFactory;

  public RaftPartition(
      final PartitionId partitionId,
      final RaftPartitionGroupConfig config,
      final File dataDirectory,
      final ThreadContextFactory threadContextFactory) {
    this.partitionId = partitionId;
    this.config = config;
    this.dataDirectory = dataDirectory;
    this.threadContextFactory = threadContextFactory;
  }

  public void addRoleChangeListener(final RaftRoleChangeListener listener) {
    if (server == null) {
      deferredRoleChangeListeners.add(listener);
    } else {
      server.addRoleChangeListener(listener);
    }
  }

  @Deprecated
  public void addRoleChangeListener(final Consumer<Role> listener) {
    addRoleChangeListener((newRole, newTerm) -> listener.accept(newRole));
  }

  public void removeRoleChangeListener(final RaftRoleChangeListener listener) {
    deferredRoleChangeListeners.remove(listener);
    server.removeRoleChangeListener(listener);
  }

  public void addFailureListener(final RaftFailureListener failureListener) {
    raftFailureListeners.add(failureListener);
  }

  public void removeFailureListener(final RaftFailureListener failureListener) {
    raftFailureListeners.remove(failureListener);
  }

  public void setJournalIndexFactory(final Supplier<JournalIndex> journalIndexFactory) {
    if (server != null) {
      throw new IllegalStateException(
          "Settings the JournalIndexFactory makes only sense when the RaftPartition is not already opened!");
    }

    this.journalIndexFactory = journalIndexFactory;
  }

  /**
   * Returns the partition data directory.
   *
   * @return the partition data directory
   */
  public File dataDirectory() {
    return dataDirectory;
  }

  /**
   * Takes a snapshot of the partition.
   *
   * @return a future to be completed once the snapshot is complete
   */
  public CompletableFuture<Void> snapshot() {
    final RaftPartitionServer server = this.server;
    if (server != null) {
      return server.snapshot();
    }
    return CompletableFuture.completedFuture(null);
  }

  /** Opens the partition. */
  CompletableFuture<Partition> open(
      final PartitionMetadata metadata, final PartitionManagementService managementService) {
    this.partitionMetadata = metadata;
    this.client = createClient(managementService);
    if (partitionMetadata
        .members()
        .contains(managementService.getMembershipService().getLocalMember().id())) {
      initServer(managementService);
      return server.start().thenCompose(v -> client.start()).thenApply(v -> null);
    }
    return client.start().thenApply(v -> this);
  }

  private void initServer(final PartitionManagementService managementService) {
    server = createServer(managementService);

    if (!deferredRoleChangeListeners.isEmpty()) {
      deferredRoleChangeListeners.forEach(server::addRoleChangeListener);
      deferredRoleChangeListeners.clear();
    }
    server.addFailureListener(this::onFailure);
  }

  /** Creates a Raft server. */
  protected RaftPartitionServer createServer(final PartitionManagementService managementService) {
    return new RaftPartitionServer(
        this,
        config,
        managementService.getMembershipService().getLocalMember().id(),
        managementService.getMembershipService(),
        managementService.getMessagingService(),
        managementService.getPrimitiveTypes(),
        threadContextFactory,
        journalIndexFactory);
  }

  /** Creates a Raft client. */
  private RaftPartitionClient createClient(final PartitionManagementService managementService) {
    return new RaftPartitionClient(
        this,
        managementService.getMembershipService().getLocalMember().id(),
        new RaftClientCommunicator(
            name(),
            Serializer.using(RaftNamespaces.RAFT_PROTOCOL),
            managementService.getMessagingService()),
        threadContextFactory);
  }

  /**
   * Returns the partition name.
   *
   * @return the partition name
   */
  public String name() {

    return String.format("%s-partition-%d", partitionId.group(), partitionId.id());
  }

  /** Updates the partition with the given metadata. */
  CompletableFuture<Void> update(
      final PartitionMetadata metadata, final PartitionManagementService managementService) {
    if (server == null
        && metadata
            .members()
            .contains(managementService.getMembershipService().getLocalMember().id())) {
      initServer(managementService);
      return server.join(metadata.members());
    } else if (server != null
        && !metadata
            .members()
            .contains(managementService.getMembershipService().getLocalMember().id())) {
      return server.leave().thenRun(() -> server = null);
    }
    return CompletableFuture.completedFuture(null);
  }

  /** Closes the partition. */
  CompletableFuture<Void> close() {
    return closeClient()
        .exceptionally(v -> null)
        .thenCompose(v -> closeServer())
        .exceptionally(v -> null);
  }

  private CompletableFuture<Void> closeClient() {
    if (client != null) {
      return client.stop();
    }
    return CompletableFuture.completedFuture(null);
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
        .thenCompose(v -> client.stop())
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
    return partitionMetadata != null ? partitionMetadata.members() : Collections.emptyList();
  }

  @Override
  public MemberId primary() {
    return client != null ? client.leader() : null;
  }

  @Override
  public Collection<MemberId> backups() {
    final MemberId leader = primary();
    if (leader == null) {
      return members();
    }
    return members().stream().filter(m -> !m.equals(leader)).collect(Collectors.toSet());
  }

  @Override
  public RaftPartitionClient getClient() {
    return client;
  }

  public Role getRole() {
    return server != null ? server.getRole() : null;
  }

  public RaftPartitionServer getServer() {
    return server;
  }

  public CompletableFuture<Void> stepDown() {
    return server.stepDown();
  }

  private void onFailure() {
    CompletableFuture.allOf(
            raftFailureListeners.stream()
                .map(RaftFailureListener::onRaftFailed)
                .toArray(CompletableFuture[]::new))
        .join();
  }
}
