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
package io.zeebe.broker.clustering.api;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LOCAL_NODE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.partitionInstallServiceName;
import static io.zeebe.broker.transport.TransportServiceNames.REPLICATION_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.clientTransport;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.partitions.PartitionAlreadyExistsException;
import io.zeebe.broker.clustering.base.partitions.PartitionInstallService;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfiguration;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManager;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.clustering.gossip.MessageHeaderDecoder;
import io.zeebe.clustering.management.*;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class ManagementApiRequestHandler implements ServerRequestHandler, ServerMessageHandler {
  private static final BufferWriter EMPTY_RESPONSE =
      new DirectBufferWriter().wrap(new UnsafeBuffer(new byte[0]));
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final CreatePartitionRequest createPartitionRequest = new CreatePartitionRequest();
  private final InvitationRequest invitationRequest = new InvitationRequest();

  private final RaftPersistentConfigurationManager raftPersistentConfigurationManager;
  private final ActorControl actor;
  private final ServiceStartContext serviceStartContext;
  private final BrokerCfg brokerCfg;

  private final SnapshotReplicationRequestHandler snapshotReplicationRequestHandler;

  public ManagementApiRequestHandler(
      RaftPersistentConfigurationManager raftPersistentConfigurationManager,
      ActorControl actor,
      ServiceStartContext serviceStartContext,
      BrokerCfg brokerCfg,
      Map<Integer, Partition> trackedSnapshotPartitions) {
    this.raftPersistentConfigurationManager = raftPersistentConfigurationManager;
    this.actor = actor;
    this.serviceStartContext = serviceStartContext;
    this.brokerCfg = brokerCfg;
    this.snapshotReplicationRequestHandler =
        new SnapshotReplicationRequestHandler(
            LOG, trackedSnapshotPartitions, ServerTransportBuilder.DEFAULT_MAX_MESSAGE_LENGTH);
  }

  @Override
  public boolean onRequest(
      ServerOutput output,
      RemoteAddress remoteAddress,
      DirectBuffer buffer,
      int offset,
      int length,
      long requestId) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int schemaId = messageHeaderDecoder.schemaId();

    if (InvitationResponseDecoder.SCHEMA_ID == schemaId) {
      final int templateId = messageHeaderDecoder.templateId();
      switch (templateId) {
        case InvitationRequestEncoder.TEMPLATE_ID:
          {
            return onInvitationRequest(buffer, offset, length, output, remoteAddress, requestId);
          }
        case CreatePartitionRequestDecoder.TEMPLATE_ID:
          {
            return onCreatePartitionRequest(
                buffer, offset, length, output, remoteAddress, requestId);
          }
        case ListSnapshotsRequestDecoder.TEMPLATE_ID:
          {
            sendResponseAsync(
                output,
                remoteAddress,
                requestId,
                snapshotReplicationRequestHandler.handleListSnapshotsAsync(buffer, offset, length));
            break;
          }
        case FetchSnapshotChunkRequestDecoder.TEMPLATE_ID:
          {
            // Instead provide a supplier which knows how to get the right response
            sendResponseAsync(
                output,
                remoteAddress,
                requestId,
                snapshotReplicationRequestHandler.handleFetchSnapshotChunkAsync(
                    buffer, offset, length));
            break;
          }
      }
    }

    return true;
  }

  private boolean onCreatePartitionRequest(
      DirectBuffer buffer,
      int offset,
      int length,
      ServerOutput output,
      RemoteAddress remoteAddress,
      long requestId) {
    createPartitionRequest.wrap(buffer, offset, length);

    final DirectBuffer topicName = BufferUtil.cloneBuffer(createPartitionRequest.getTopicName());
    final int partitionId = createPartitionRequest.getPartitionId();
    final int replicationFactor = createPartitionRequest.getReplicationFactor();
    final List<SocketAddress> members = Collections.emptyList();

    LOG.info(
        "Received create partition request for topic={}, partitionId={} and replicationFactor={}",
        BufferUtil.bufferAsString(topicName),
        partitionId,
        replicationFactor);

    installPartition(
        topicName, partitionId, replicationFactor, members, output, remoteAddress, requestId);

    return true;
  }

  private boolean onInvitationRequest(
      DirectBuffer buffer,
      int offset,
      int length,
      ServerOutput output,
      RemoteAddress remoteAddress,
      long requestId) {
    invitationRequest.wrap(buffer, offset, length);

    final DirectBuffer topicName = BufferUtil.cloneBuffer(invitationRequest.topicName());
    final int partitionId = invitationRequest.partitionId();
    final int replicationFactor = invitationRequest.replicationFactor();
    final List<SocketAddress> members = new ArrayList<>(invitationRequest.members());

    LOG.info(
        "Received invitation request for topicName={}, partitionId={}, replicationFactor={} with members={}",
        BufferUtil.bufferAsString(topicName),
        partitionId,
        replicationFactor,
        members);

    installPartition(
        topicName, partitionId, replicationFactor, members, output, remoteAddress, requestId);

    return true;
  }

  private void installPartition(
      final DirectBuffer topicName,
      final int partitionId,
      final int replicationFactor,
      final List<SocketAddress> members,
      ServerOutput output,
      RemoteAddress remoteAddress,
      long requestId) {
    final ActorFuture<RaftPersistentConfiguration> configurationFuture =
        raftPersistentConfigurationManager.createConfiguration(
            topicName, partitionId, replicationFactor, members);

    actor.runOnCompletion(
        configurationFuture,
        (configuration, throwable) -> {
          if (throwable != null) {
            if (throwable instanceof PartitionAlreadyExistsException) {
              LOG.info(throwable.getMessage());
            } else {
              LOG.error("Exception while creating partition", throwable);
            }

            sendEmptyResponse(output, remoteAddress, requestId);
          } else {
            final String partitionName =
                String.format(
                    "%s-%d",
                    BufferUtil.bufferAsString(configuration.getTopicName()),
                    configuration.getPartitionId());
            final ServiceName<Void> partitionInstallServiceName =
                partitionInstallServiceName(partitionName);
            final boolean isSystemPartition =
                Protocol.SYSTEM_PARTITION == configuration.getPartitionId();
            final PartitionInstallService partitionInstallService =
                new PartitionInstallService(brokerCfg, configuration, isSystemPartition);

            final ActorFuture<Void> partitionInstallFuture =
                serviceStartContext
                    .createService(partitionInstallServiceName, partitionInstallService)
                    .dependency(LOCAL_NODE, partitionInstallService.getLocalNodeInjector())
                    .dependency(
                        clientTransport(REPLICATION_API_CLIENT_NAME),
                        partitionInstallService.getClientTransportInjector())
                    .install();

            actor.runOnCompletion(
                partitionInstallFuture,
                (aVoid, installThrowable) -> {
                  if (installThrowable == null) {
                    sendEmptyResponse(output, remoteAddress, requestId);
                  } else {
                    LOG.error("Exception while creating partition", installThrowable);
                  }
                });
          }
        });
  }

  private void sendResponseAsync(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final long requestId,
      final Supplier<BufferWriter> responseSupplier) {
    actor.runUntilDone(
        () -> {
          final BufferWriter responseWriter = responseSupplier.get();
          final ServerResponse response =
              new ServerResponse()
                  .reset()
                  .remoteAddress(remoteAddress)
                  .requestId(requestId)
                  .writer(responseWriter);

          if (output.sendResponse(response)) {
            actor.done();
          } else {
            actor.yield();
          }
        });
  }

  private void sendEmptyResponse(
      final ServerOutput output, final RemoteAddress remoteAddress, final long requestId) {
    sendResponseAsync(output, remoteAddress, requestId, () -> EMPTY_RESPONSE);
  }

  @Override
  public boolean onMessage(
      ServerOutput output,
      RemoteAddress remoteAddress,
      DirectBuffer buffer,
      int offset,
      int length) {
    // no messages currently supported
    return true;
  }
}
