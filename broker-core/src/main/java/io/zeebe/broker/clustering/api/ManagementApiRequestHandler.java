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
import io.zeebe.clustering.management.FetchSnapshotChunkRequestDecoder;
import io.zeebe.clustering.management.InvitationRequestEncoder;
import io.zeebe.clustering.management.InvitationResponseDecoder;
import io.zeebe.clustering.management.ListSnapshotsRequestDecoder;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.ServerTransportBuilder;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class ManagementApiRequestHandler implements ServerRequestHandler, ServerMessageHandler {
  private static final BufferWriter EMPTY_RESPONSE =
      new DirectBufferWriter().wrap(new UnsafeBuffer(new byte[0]));
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final InvitationRequest invitationRequest = new InvitationRequest();

  private final RaftPersistentConfigurationManager raftPersistentConfigurationManager;
  private final ActorControl actor;
  private final ServiceStartContext serviceStartContext;
  private final BrokerCfg brokerCfg;

  private final SnapshotReplicationRequestHandler snapshotReplicationRequestHandler;

  public ManagementApiRequestHandler(
      final RaftPersistentConfigurationManager raftPersistentConfigurationManager,
      final ActorControl actor,
      final ServiceStartContext serviceStartContext,
      final BrokerCfg brokerCfg,
      final Map<Integer, Partition> trackedSnapshotPartitions) {
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
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final long requestId) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int schemaId = messageHeaderDecoder.schemaId();

    if (InvitationResponseDecoder.SCHEMA_ID == schemaId) {
      final int templateId = messageHeaderDecoder.templateId();
      switch (templateId) {
        case InvitationRequestEncoder.TEMPLATE_ID:
          {
            return onInvitationRequest(buffer, offset, length, output, remoteAddress, requestId);
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

  private boolean onInvitationRequest(
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final long requestId) {
    invitationRequest.wrap(buffer, offset, length);

    final int partitionId = invitationRequest.partitionId();
    final int replicationFactor = invitationRequest.replicationFactor();
    final IntArrayList members = new IntArrayList();
    members.addAll(invitationRequest.members());

    LOG.info(
        "Received invitation request for partitionId={}, replicationFactor={} with members={}",
        partitionId,
        replicationFactor,
        members);

    installPartition(partitionId, replicationFactor, members, output, remoteAddress, requestId);

    return true;
  }

  private void installPartition(
      final int partitionId,
      final int replicationFactor,
      final List<Integer> members,
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final long requestId) {
    final ActorFuture<RaftPersistentConfiguration> configurationFuture =
        raftPersistentConfigurationManager.createConfiguration(
            partitionId, replicationFactor, members);

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
            final String partitionName = Partition.getPartitionName(configuration.getPartitionId());
            final ServiceName<Void> partitionInstallServiceName =
                partitionInstallServiceName(partitionName);
            final boolean isSystemPartition =
                Protocol.SYSTEM_PARTITION == configuration.getPartitionId();
            final PartitionInstallService partitionInstallService =
                new PartitionInstallService(brokerCfg, configuration, isSystemPartition);

            final ActorFuture<Void> partitionInstallFuture =
                serviceStartContext
                    .createService(partitionInstallServiceName, partitionInstallService)
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
          final BufferWriter responseWriter;

          try {
            responseWriter = responseSupplier.get();
          } catch (final Exception ex) {
            LOG.error("Error generating server response", ex);
            actor.done();
            return;
          }

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
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    // no messages currently supported
    return true;
  }
}
