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

import java.util.Collections;
import java.util.List;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.PartitionInstallService;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfiguration;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManager;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.clustering.management.*;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class ManagementApiRequestHandler implements ServerRequestHandler, ServerMessageHandler
{
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(new byte[0]);

    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final CreatePartitionRequest createPartitionRequest = new CreatePartitionRequest();
    private final InvitationRequest invitationRequest = new InvitationRequest();

    private final RaftPersistentConfigurationManager raftPersistentConfigurationManager;
    private final ActorControl actor;
    private final ServiceStartContext serviceStartContext;

    private final BrokerCfg brokerCfg;

    public ManagementApiRequestHandler(RaftPersistentConfigurationManager raftPersistentConfigurationManager,
        ActorControl actor,
        ServiceStartContext serviceStartContext,
        BrokerCfg brokerCfg)
    {
        this.raftPersistentConfigurationManager = raftPersistentConfigurationManager;
        this.actor = actor;
        this.serviceStartContext = serviceStartContext;
        this.brokerCfg = brokerCfg;
    }

    @Override
    public boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length, long requestId)
    {
        messageHeaderDecoder.wrap(buffer, offset);

        final int schemaId = messageHeaderDecoder.schemaId();

        if (InvitationResponseDecoder.SCHEMA_ID == schemaId)
        {
            final int templateId = messageHeaderDecoder.templateId();
            switch (templateId)
            {
                case InvitationRequestEncoder.TEMPLATE_ID:
                {
                    return onInvitationRequest(buffer, offset, length, output, remoteAddress, requestId);
                }
                case CreatePartitionRequestDecoder.TEMPLATE_ID:
                {
                    return onCreatePartitionRequest(buffer, offset, length, output, remoteAddress, requestId);
                }
                default:
                {
                    // ignore
                    return true;
                }
            }
        }
        else
        {
            return true;
        }
    }

    private boolean onCreatePartitionRequest(DirectBuffer buffer, int offset, int length, ServerOutput output, RemoteAddress remoteAddress, long requestId)
    {
        createPartitionRequest.wrap(buffer, offset, length);

        final DirectBuffer topicName = createPartitionRequest.getTopicName();
        final int partitionId = createPartitionRequest.getPartitionId();
        final int replicationFactor = createPartitionRequest.getReplicationFactor();
        final List<SocketAddress> members = Collections.emptyList();

        installPartition(topicName, partitionId, replicationFactor, members, output, remoteAddress, requestId);

        return true;
    }


    private boolean onInvitationRequest(DirectBuffer buffer, int offset, int length, ServerOutput output, RemoteAddress remoteAddress, long requestId)
    {
        invitationRequest.wrap(buffer, offset, length);

        final DirectBuffer topicName = invitationRequest.topicName();
        final int partitionId = invitationRequest.partitionId();
        final int replicationFactor = invitationRequest.replicationFactor();
        final List<SocketAddress> members = invitationRequest.members();

        installPartition(topicName, partitionId, replicationFactor, members, output, remoteAddress, requestId);

        return true;
    }


    private void installPartition(final DirectBuffer topicName,
        final int partitionId,
        final int replicationFactor,
        final List<SocketAddress> members,
        ServerOutput output,
        RemoteAddress remoteAddress,
        long requestId)
    {
        final ActorFuture<RaftPersistentConfiguration> configurationFuture = raftPersistentConfigurationManager.createConfiguration(topicName, partitionId, replicationFactor, members);

        actor.runOnCompletion(configurationFuture, (configuration, throwable) ->
        {
            if (throwable != null)
            {
                LOG.error("Exception while creating partition", throwable);
                sendEmptyRespone(output, remoteAddress, requestId);
            }
            else
            {
                final String partitionName = String.format("%s-%d", BufferUtil.bufferAsString(configuration.getTopicName()), configuration.getPartitionId());
                final ServiceName<Void> partitionInstallServiceName = partitionInstallServiceName(partitionName);
                final boolean isSystemPartition = Protocol.SYSTEM_PARTITION == configuration.getPartitionId();
                final PartitionInstallService partitionInstallService = new PartitionInstallService(brokerCfg, configuration, isSystemPartition);

                final ActorFuture<Void> partitionInstallFuture = serviceStartContext.createService(partitionInstallServiceName, partitionInstallService)
                    .dependency(LOCAL_NODE, partitionInstallService.getLocalNodeInjector())
                    .dependency(clientTransport(REPLICATION_API_CLIENT_NAME), partitionInstallService.getClientTransportInjector())
                    .install();

                actor.runOnCompletion(partitionInstallFuture, (aVoid, installThrowable) ->
                {
                    if (installThrowable == null)
                    {
                        sendEmptyRespone(output, remoteAddress, requestId);
                    }
                    else
                    {
                        LOG.error("Exception while creating partition", throwable);
                    }
                });
            }
        });
    }

    protected void sendEmptyRespone(ServerOutput output, RemoteAddress remoteAddress, long requestId)
    {
        final ServerResponse serverResponse = new ServerResponse();

        actor.runUntilDone(() ->
        {
            serverResponse.reset()
                .remoteAddress(remoteAddress)
                .requestId(requestId)
                .buffer(EMPTY_BUFFER);

            if (output.sendResponse(serverResponse))
            {
                actor.done();
            }
            else
            {
                actor.yield();
            }
        });
    }

    @Override
    public boolean onMessage(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
    {
        // no messages currently supported
        return true;
    }
}
