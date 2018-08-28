/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.snapshots;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.api.ErrorResponse;
import io.zeebe.broker.clustering.api.FetchSnapshotChunkRequest;
import io.zeebe.broker.clustering.api.FetchSnapshotChunkResponse;
import io.zeebe.broker.clustering.api.ListSnapshotsRequest;
import io.zeebe.broker.clustering.api.ListSnapshotsResponse;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.ReadableTopology;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.clustering.management.ErrorResponseDecoder;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.logstreams.spi.SnapshotWriter;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerTransportBuilder;
import io.zeebe.util.StreamUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

/**
 * Replicates all snapshots from the leader of a given partition. Depends on the followerService for
 * a given partition to ensure correct lifecycle.
 */
public class SnapshotReplicationService extends Actor
    implements Service<SnapshotReplicationService> {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;
  public static final int DEFAULT_CHUNK_LENGTH = ServerTransportBuilder.DEFAULT_MAX_MESSAGE_LENGTH;
  public static final Duration ERROR_RETRY_INTERVAL = Duration.ofSeconds(1);

  private final Injector<ClientTransport> managementClientApiInjector = new Injector<>();
  private ClientTransport clientTransport;

  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
  private TopologyManager topologyManager;

  private final Injector<Partition> partitionInjector = new Injector<>();
  private Partition partition;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ErrorResponse errorResponse = new ErrorResponse();

  // Reuse response/request objects to avoid allocating often
  private final ListSnapshotsRequest listSnapshotsRequest = new ListSnapshotsRequest();
  private final ListSnapshotsResponse listSnapshotsResponse = new ListSnapshotsResponse();

  private final FetchSnapshotChunkRequest fetchSnapshotChunkRequest =
      new FetchSnapshotChunkRequest();
  private final FetchSnapshotChunkResponse fetchSnapshotChunkResponse =
      new FetchSnapshotChunkResponse();

  private final Duration pollInterval;
  private int leaderNodeId;
  private String actorName;

  // Used to properly calculate polling intervals (since replication operation can take a while)
  private long lastPollEpoch;

  // On-going replication state
  private final Queue<ListSnapshotsResponse.SnapshotMetadata> snapshotsToReplicate =
      new ArrayDeque<>();
  private SnapshotWriter currentSnapshotWriter;
  private ListSnapshotsResponse.SnapshotMetadata currentReplicatingSnapshot;
  private long chunkOffset;

  public SnapshotReplicationService(final Duration pollInterval) {
    this.pollInterval = pollInterval;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    clientTransport = managementClientApiInjector.getValue();
    partition = partitionInjector.getValue();
    topologyManager = topologyManagerInjector.getValue();
    listSnapshotsRequest.setPartitionId(partition.getInfo().getPartitionId());
    actorName =
        String.format(
            "%s-%d-snap-repl",
            partition.getInfo().getTopicName(), partition.getInfo().getPartitionId());

    LOG.debug("Starting replication for partition {}", partition.getInfo());
    startContext.async(startContext.getScheduler().submitActor(this), true);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    LOG.debug("Stopping replication for partition {}", partition.getInfo());
    stopContext.async(actor.close());
  }

  @Override
  public SnapshotReplicationService get() {
    return this;
  }

  @Override
  protected void onActorStarted() {
    this.pollLeaderForSnapshots();
  }

  @Override
  protected void onActorClosing() {
    snapshotsToReplicate.clear();
    abortCurrentReplication();
  }

  @Override
  public String getName() {
    return actorName == null ? super.getName() : actorName;
  }

  private void pollLeaderForSnapshots() {
    final ActorFuture<NodeInfo> topologyQuery = topologyManager.query(this::getLeaderInfo);
    actor.runOnCompletion(
        topologyQuery,
        (leaderInfo, error) -> {
          if (error != null) {
            LOG.error("Failed to query topology for leader info, retrying", error);
            actor.runDelayed(ERROR_RETRY_INTERVAL, this::pollLeaderForSnapshots);
          } else if (leaderInfo == null) {
            LOG.trace("Waiting for leader node info, retrying");
            actor.runDelayed(ERROR_RETRY_INTERVAL, this::pollLeaderForSnapshots);
          } else {
            leaderNodeId = leaderInfo.getNodeId();
            LOG.trace("Updated leader node as {}", leaderNodeId);
            pollSnapshots();
          }
        });
  }

  private void pollSnapshots() {
    lastPollEpoch = ActorClock.currentTimeMillis();

    final ActorFuture<ClientResponse> responseFuture =
        clientTransport.getOutput().sendRequest(leaderNodeId, listSnapshotsRequest);
    LOG.trace("Polling snapshots from {}", leaderNodeId);
    snapshotsToReplicate.clear();

    actor.runOnCompletion(
        responseFuture,
        (clientResponse, error) -> {
          if (error != null) {
            LOG.error("Error listing snapshots from leader", error);
            actor.runDelayed(ERROR_RETRY_INTERVAL, this::pollLeaderForSnapshots);
          } else {
            handleListSnapshotsResponse(clientResponse.getResponseBuffer());
          }
        });
  }

  private void schedulePollSnapshots() {
    final long millisSinceLastPoll = ActorClock.currentTimeMillis() - lastPollEpoch;
    final Duration interval = pollInterval.minusMillis(millisSinceLastPoll);

    if (interval.isNegative() || interval.isZero()) {
      actor.run(this::pollSnapshots);
    } else {
      actor.runDelayed(interval, this::pollSnapshots);
    }
  }

  private void handleListSnapshotsResponse(final DirectBuffer buffer) {
    if (isErrorResponse(buffer)) {
      logErrorResponse("Error listing snapshots", buffer);
      actor.runDelayed(ERROR_RETRY_INTERVAL, this::pollLeaderForSnapshots);
      return;
    }

    listSnapshotsResponse.wrap(buffer);

    for (ListSnapshotsResponse.SnapshotMetadata metadata : listSnapshotsResponse.getSnapshots()) {
      if (!partition
          .getSnapshotStorage()
          .snapshotExists(metadata.getName(), metadata.getLogPosition())) {
        snapshotsToReplicate.add(metadata);
      }
    }

    LOG.trace("Replicating {} snapshots", snapshotsToReplicate.size());
    replicateNextSnapshot();
  }

  private void replicateNextSnapshot() {
    chunkOffset = 0;
    currentReplicatingSnapshot = snapshotsToReplicate.poll();

    if (currentReplicatingSnapshot == null) {
      schedulePollSnapshots();
      return;
    }

    try {
      currentSnapshotWriter =
          partition
              .getSnapshotStorage()
              .createTemporarySnapshot(
                  currentReplicatingSnapshot.getName(),
                  currentReplicatingSnapshot.getLogPosition());
    } catch (final Exception ex) {
      LOG.error("Could not create writer for {}", currentReplicatingSnapshot, ex);
      replicateNextSnapshot();
      return;
    }

    replicateSnapshot();
  }

  private void replicateSnapshot() {
    final ActorFuture<ClientResponse> awaitFetchChunk =
        clientTransport.getOutput().sendRequest(leaderNodeId, requestForNextChunk());

    actor.runOnCompletion(
        awaitFetchChunk,
        (clientResponse, error) -> {
          if (error != null) {
            LOG.error("Error fetching chunk", error);
            abortAndReplicateNext();
          } else {
            handleFetchSnapshotChunkResponse(clientResponse.getResponseBuffer());
          }
        });
  }

  private void handleFetchSnapshotChunkResponse(final DirectBuffer buffer) {
    if (isErrorResponse(buffer)) {
      logErrorResponse("Error fetching chunk", buffer);
      abortAndReplicateNext();
      return;
    }

    fetchSnapshotChunkResponse.wrap(buffer);
    final DirectBuffer chunk = fetchSnapshotChunkResponse.getData();

    try {
      StreamUtil.write(chunk, currentSnapshotWriter.getOutputStream());
    } catch (final Exception ex) {
      LOG.error("Error writing chunk", ex);
      abortAndReplicateNext();
      return;
    }

    chunkOffset += chunk.capacity();
    if (chunkOffset >= currentReplicatingSnapshot.getLength()) {
      finalizeSnapshot();
    } else {
      replicateSnapshot();
    }
  }

  private void finalizeSnapshot() {
    try {
      currentSnapshotWriter.validateAndCommit(currentReplicatingSnapshot.getChecksum());
      currentSnapshotWriter = null;
    } catch (final Exception ex) {
      LOG.error("Error committing, aborting", ex);
      abortAndReplicateNext();
      return;
    }

    replicateNextSnapshot();
  }

  private void abortCurrentReplication() {
    chunkOffset = 0;
    currentReplicatingSnapshot = null;

    if (currentSnapshotWriter != null) {
      currentSnapshotWriter.abort();
    }
  }

  private void abortAndReplicateNext() {
    abortCurrentReplication();
    this.replicateNextSnapshot();
  }

  private FetchSnapshotChunkRequest requestForNextChunk() {
    return fetchSnapshotChunkRequest
        .setPartitionId(partition.getInfo().getPartitionId())
        .setName(currentReplicatingSnapshot.getName())
        .setLogPosition(currentReplicatingSnapshot.getLogPosition())
        .setChunkLength(DEFAULT_CHUNK_LENGTH)
        .setChunkOffset(chunkOffset);
  }

  private void logErrorResponse(final String message, final DirectBuffer buffer) {
    errorResponse.wrap(buffer);
    LOG.error("{} - {} - {}", message, errorResponse.getCode(), errorResponse.getMessage());
  }

  private boolean isErrorResponse(final DirectBuffer buffer) {
    messageHeaderDecoder.wrap(buffer, 0);
    return messageHeaderDecoder.templateId() == ErrorResponseDecoder.TEMPLATE_ID;
  }

  private NodeInfo getLeaderInfo(ReadableTopology topology) {
    return topology.getLeader(partition.getInfo().getPartitionId());
  }

  public Injector<ClientTransport> getManagementClientApiInjector() {
    return managementClientApiInjector;
  }

  public Injector<Partition> getPartitionInjector() {
    return partitionInjector;
  }

  public Injector<TopologyManager> getTopologyManagerInjector() {
    return topologyManagerInjector;
  }
}
