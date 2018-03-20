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
package io.zeebe.broker.clustering.management;

import static io.zeebe.broker.clustering.ClusterServiceNames.RAFT_SERVICE_GROUP;
import static io.zeebe.broker.clustering.ClusterServiceNames.raftServiceName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.handler.Topology;
import io.zeebe.broker.clustering.management.handler.ClusterManagerFragmentHandler;
import io.zeebe.broker.clustering.management.memberList.ClusterMemberListManager;
import io.zeebe.broker.clustering.management.memberList.MemberRaftComposite;
import io.zeebe.broker.clustering.management.message.*;
import io.zeebe.broker.clustering.raft.RaftPersistentFileStorage;
import io.zeebe.broker.clustering.raft.RaftService;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.broker.transport.cfg.SocketBindingCfg;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.Protocol;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftPersistentStorage;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.*;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class ClusterManager extends Actor
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;
    private static final DirectBuffer EMPTY_BUF = new UnsafeBuffer(0, 0);

    private final ClusterManagerContext context;
    private final ServiceContainer serviceContainer;

    private final List<Raft> rafts;

    private final InvitationRequest invitationRequest;
    private final InvitationResponse invitationResponse;
    private final CreatePartitionRequest createPartitionRequest = new CreatePartitionRequest();

    private TransportComponentCfg transportComponentCfg;

    private final ServerResponse response = new ServerResponse();

    private final LogStreamsManager logStreamsManager;
    private final ClusterMemberListManager clusterMemberListManager;

    public ClusterManager(final ClusterManagerContext context,
                          final ServiceContainer serviceContainer,
                          final TransportComponentCfg transportComponentCfg)
    {
        this.context = context;
        this.serviceContainer = serviceContainer;
        this.transportComponentCfg = transportComponentCfg;
        this.rafts = new CopyOnWriteArrayList<>();
        this.invitationRequest = new InvitationRequest();
        this.logStreamsManager = context.getLogStreamsManager();

        this.invitationResponse = new InvitationResponse();
        this.clusterMemberListManager = new ClusterMemberListManager(context, actor, transportComponentCfg, this::inviteUpdatedMember);
    }

    public void close()
    {
        actor.close();
    }

    private void open()
    {
        final List<SocketAddress> collect = Arrays.stream(transportComponentCfg.gossip.initialContactPoints)
            .map(SocketAddress::from)
            .collect(Collectors.toList());
        if (!collect.isEmpty())
        {
            context.getGossip().join(collect);
        }

        clusterMemberListManager.publishNodeAPIAddresses();

        final LogStreamsManager logStreamManager = context.getLogStreamsManager();

        final File storageDirectory = new File(transportComponentCfg.management.directory);

        if (!storageDirectory.exists())
        {
            try
            {
                storageDirectory.getParentFile()
                                .mkdirs();
                Files.createDirectory(storageDirectory.toPath());
            }
            catch (final IOException e)
            {
                LOG.error("Unable to create directory {}", storageDirectory, e);
            }
        }

        final SocketBindingCfg replicationApi = transportComponentCfg.replicationApi;
        final SocketAddress socketAddress = new SocketAddress(replicationApi.getHost(transportComponentCfg.host), replicationApi.port);
        final File[] storageFiles = storageDirectory.listFiles();

        if (storageFiles != null && storageFiles.length > 0)
        {
            for (int i = 0; i < storageFiles.length; i++)
            {
                final File storageFile = storageFiles[i];
                final RaftPersistentFileStorage storage = new RaftPersistentFileStorage(storageFile.getAbsolutePath());

                final DirectBuffer topicName = storage.getTopicName();
                final int partitionId = storage.getPartitionId();

                LogStream logStream = logStreamManager.getLogStream(partitionId);

                if (logStream == null)
                {
                    final String directory = storage.getLogDirectory();
                    logStream = logStreamManager.createLogStream(topicName, partitionId, directory);
                }

                storage.setLogStream(logStream);

                createRaft(socketAddress, logStream, storage.getMembers(), storage);
            }
        }
        else
        {
            if (transportComponentCfg.gossip.initialContactPoints.length == 0)
            {
                LOG.debug("Broker bootstraps the system topic");
                createPartition(Protocol.SYSTEM_TOPIC_BUF, Protocol.SYSTEM_PARTITION);
            }
        }
    }

    @Override
    public String getName()
    {
        return "cluster-manager";
    }

    @Override
    protected void onActorStarted()
    {
        final ClusterManagerFragmentHandler fragmentHandler = new ClusterManagerFragmentHandler(this, context.getWorkflowRequestMessageHandler());

        final ActorFuture<ServerInputSubscription> serverInputSubscriptionActorFuture = context.getServerTransport()
            .openSubscription("cluster-management", fragmentHandler, fragmentHandler);

        actor.runOnCompletion(serverInputSubscriptionActorFuture, (subscription, throwable) ->
        {
            if (throwable == null)
            {
                actor.consume(subscription, () ->
                {
                    if (subscription.poll() == 0)
                    {
                        actor.yield();
                    }
                });

                open();
            }
            else
            {
                Loggers.CLUSTERING_LOGGER.error("Failed to open a subscription.");
            }
        });
    }

    private void inviteUpdatedMember(SocketAddress updatedMember)
    {
        LOG.debug("Send raft invitations to member {}.", updatedMember);
        for (Raft raft : rafts)
        {
            if (raft.getState() == RaftState.LEADER)
            {
                // TODO don't invite all members
                inviteMemberToRaft(updatedMember, raft);
            }
        }
    }

    /**
     * Invites the member to the RAFT group.
     */
    protected void inviteMemberToRaft(SocketAddress member, Raft raft)
    {
        // TODO(menski): implement replication factor
        // TODO: if this should be garbage free, we have to limit
        // the number of concurrent invitations.
        final List<SocketAddress> members = new ArrayList<>();
        members.add(raft.getSocketAddress());
        raft.getMembers()
            .forEach(raftMember -> members.add(raftMember.getRemoteAddress()
                                                         .getAddress()));

        final LogStream logStream = raft.getLogStream();
        final InvitationRequest invitationRequest = new InvitationRequest().topicName(logStream.getTopicName())
                                                                           .partitionId(logStream.getPartitionId())
                                                                           .term(raft.getTerm())
                                                                           .members(members);

        LOG.debug("Send invitation request to {} for partition {} in term {}", member, logStream.getPartitionId(), raft.getTerm());

        final RemoteAddress remoteAddress = context.getManagementClient().registerRemoteAddress(member);
        final ActorFuture<ClientResponse> clientResponse = context.getManagementClient().getOutput().sendRequest(remoteAddress, invitationRequest);

        actor.runOnCompletion(clientResponse, (request, throwable) ->
        {
            if (throwable == null)
            {
                request.close();
                LOG.debug("Got invitation response from {} for partition id {}.", member, logStream.getPartitionId());
            }
            else
            {
                LOG.debug("Invitation request failed");
            }
        });
    }

    public void createRaft(final SocketAddress socketAddress, final LogStream logStream, final List<SocketAddress> members)
    {
        final FsLogStorage logStorage = (FsLogStorage) logStream.getLogStorage();
        final String path = logStorage.getConfig()
                                      .getPath();

        final String directory = transportComponentCfg.management.directory;
        final RaftPersistentFileStorage storage = new RaftPersistentFileStorage(String.format("%s%s.meta", directory, logStream.getLogName()));
        storage.setLogStream(logStream)
               .setLogDirectory(path)
               .save();

        createRaft(socketAddress, logStream, members, storage);
    }

    public void createRaft(final SocketAddress socketAddress, final LogStream logStream, final List<SocketAddress> members,
                           final RaftPersistentStorage persistentStorage)
    {
        final ServiceName<Raft> raftServiceName = raftServiceName(logStream.getLogName());
        final RaftService raftService = new RaftService(transportComponentCfg.raft, socketAddress, logStream, members, persistentStorage, clusterMemberListManager, clusterMemberListManager, raftServiceName);

        serviceContainer.createService(raftServiceName, raftService)
                        .group(RAFT_SERVICE_GROUP)
                        .dependency(TransportServiceNames.clientTransport(TransportServiceNames.REPLICATION_API_CLIENT_NAME),
                                    raftService.getClientTransportInjector())
                        .install();
    }

    protected boolean partitionExists(int partitionId)
    {
        return logStreamsManager.hasLogStream(partitionId);
    }

    /**
     * Creates log stream and sets up raft service to participate in raft group
     */
    protected void createPartition(DirectBuffer topicName, int partitionId)
    {
        createPartition(topicName, partitionId, Collections.emptyList());
    }

    /**
     * Creates log stream and sets up raft service to participate in raft group
     */
    protected void createPartition(DirectBuffer topicName, int partitionId, List<SocketAddress> members)
    {
        final LogStream logStream = logStreamsManager.createLogStream(topicName, partitionId);

        final SocketBindingCfg replicationApi = transportComponentCfg.replicationApi;
        final SocketAddress socketAddress = new SocketAddress(replicationApi.getHost(transportComponentCfg.host), replicationApi.port);
        createRaft(socketAddress, logStream, members);
    }

    public boolean onInvitationRequest(final DirectBuffer buffer, final int offset, final int length, final ServerOutput output,
                                       final RemoteAddress requestAddress, final long requestId)
    {
        invitationRequest.reset();
        invitationRequest.wrap(buffer, offset, length);

        LOG.debug("Received invitation request from {} for partition {}", requestAddress.getAddress(), invitationRequest.partitionId());

        final DirectBuffer topicName = invitationRequest.topicName();
        final int partitionId = invitationRequest.partitionId();

        createPartition(topicName, partitionId, new ArrayList<>(invitationRequest.members()));

        invitationResponse.reset();
        response.reset()
                .remoteAddress(requestAddress)
                .requestId(requestId)
                .writer(invitationResponse);

        return output.sendResponse(response);
    }

    public boolean onCreatePartitionRequest(
            final DirectBuffer buffer, final int offset, final int length,
            final ServerOutput output, final RemoteAddress requestAddress, final long requestId)
    {
        createPartitionRequest.wrap(buffer, offset, length);

        LOG.debug("Received create partition request for partition {}", createPartitionRequest.getPartitionId());

        final int partitionId = createPartitionRequest.getPartitionId();

        if (!partitionExists(partitionId))
        {
            LOG.debug("Creating partition {}", createPartitionRequest.getPartitionId());
            createPartition(createPartitionRequest.getTopicName(), partitionId);
        }
        else
        {
            LOG.debug("Partition {} exists already. Ignoring creation request.", createPartitionRequest.getPartitionId());
        }

        response.reset()
            .remoteAddress(requestAddress)
            .requestId(requestId)
            .buffer(EMPTY_BUF);

        return output.sendResponse(response);
    }

    public ActorFuture<Topology> requestTopology()
    {
        return clusterMemberListManager.createTopology();
    }

    /**
     * This method is called, if a new RAFT is added to the service group.
     */
    public void addRaftCallback(final ServiceName<Raft> raftServiceName, final Raft raft)
    {
        // this must be determined before we cross the async boundary to avoid race conditions
        final boolean isRaftCreator = raft.getMemberSize() == 0;
        actor.call(() ->
        {
            LOG.trace("ADD raft {} for partition {} state {}.", raft.getSocketAddress(), raft.getLogStream()
                                                                                             .getPartitionId(), raft.getState());
            rafts.add(raft);

            if (isRaftCreator)
            {
                final Iterator<MemberRaftComposite> iterator = context.getMemberListService()
                                                                      .iterator();
                while (iterator.hasNext())
                {
                    final MemberRaftComposite next = iterator.next();
                    if (!next.getMember()
                             .equals(transportComponentCfg.managementApi.toSocketAddress(transportComponentCfg.host)))
                    {
                        // TODO don't invite all members to raft
                        inviteMemberToRaft(next.getMember(), raft);
                    }
                }
            }
        });
    }

    /**
     * This method is called, if a RAFT is removed from the service group.
     */
    public void removeRaftCallback(final Raft raft)
    {
        final LogStream logStream = raft.getLogStream();
        final int partitionId = logStream.getPartitionId();

        actor.call(() ->
        {
            for (int i = 0; i < rafts.size(); i++)
            {
                final Raft r = rafts.get(i);
                final LogStream stream = r.getLogStream();
                if (partitionId == stream.getPartitionId())
                {
                    rafts.remove(i);
                    break;
                }
            }
        });
    }
}
