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
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.handler.Topology;
import io.zeebe.broker.clustering.management.handler.ClusterManagerFragmentHandler;
import io.zeebe.broker.clustering.management.memberList.ClusterMemberListManager;
import io.zeebe.broker.clustering.management.memberList.MemberRaftComposite;
import io.zeebe.broker.clustering.management.message.CreatePartitionMessage;
import io.zeebe.broker.clustering.management.message.InvitationRequest;
import io.zeebe.broker.clustering.management.message.InvitationResponse;
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
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class ClusterManager implements Actor
{
    public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final ClusterManagerContext context;
    private final ServiceContainer serviceContainer;

    private final List<Raft> rafts;
    private final List<StartLogStreamServiceController> startLogStreamServiceControllers;

    private final DeferredCommandContext commandQueue;

    private final List<RequestResponseController> activeRequestControllers;

    private final InvitationRequest invitationRequest;
    private final InvitationResponse invitationResponse;
    private final CreatePartitionMessage createPartitionMessage = new CreatePartitionMessage();

    private TransportComponentCfg transportComponentCfg;

    private final ServerResponse response = new ServerResponse();
    private final ServerInputSubscription inputSubscription;

    private final LogStreamsManager logStreamsManager;
    private final ClusterMemberListManager clusterMemberListManager;

    public ClusterManager(final ClusterManagerContext context, final ServiceContainer serviceContainer, final TransportComponentCfg transportComponentCfg)
    {
        this.context = context;
        this.serviceContainer = serviceContainer;
        this.transportComponentCfg = transportComponentCfg;
        this.rafts = new CopyOnWriteArrayList<>();
        this.startLogStreamServiceControllers = new CopyOnWriteArrayList<>();
        this.commandQueue = new DeferredCommandContext();
        this.activeRequestControllers = new CopyOnWriteArrayList<>();
        this.invitationRequest = new InvitationRequest();
        this.logStreamsManager = context.getLogStreamsManager();

        this.invitationResponse = new InvitationResponse();

        final ClusterManagerFragmentHandler fragmentHandler = new ClusterManagerFragmentHandler(this, context.getWorkflowRequestMessageHandler());
        inputSubscription = context.getServerTransport()
                                   .openSubscription("cluster-management", fragmentHandler, fragmentHandler)
                                   .join();

        clusterMemberListManager = new ClusterMemberListManager(context, transportComponentCfg, this::inviteUpdatedMember);
    }

    public void open()
    {
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
    public String name()
    {
        return "management";
    }

    @Override
    public int doWork()
    {
        int workCount = 0;

        workCount += commandQueue.doWork();
        workCount += clusterMemberListManager.doWork();
        workCount += inputSubscription.poll();

        int i = 0;
        while (i < activeRequestControllers.size())
        {
            final RequestResponseController requestController = activeRequestControllers.get(i);
            workCount += requestController.doWork();

            if (requestController.isFailed() || requestController.isResponseAvailable())
            {
                requestController.close();
            }

            if (requestController.isClosed())
            {
                activeRequestControllers.remove(i);
            }
            else
            {
                i++;
            }
        }

        for (int j = 0; j < startLogStreamServiceControllers.size(); j++)
        {
            workCount += startLogStreamServiceControllers.get(j)
                                                         .doWork();
        }

        return workCount;
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

        final RequestResponseController requestController = new RequestResponseController(context.getClientTransport());

        requestController.open(member, invitationRequest, (buffer, offset, length) ->
            LOG.debug("Got invitation response from {} for partition id {}.",
                      member,
                      logStream.getPartitionId()));
        activeRequestControllers.add(requestController);
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
        final RaftService raftService = new RaftService(socketAddress, logStream, members, persistentStorage, clusterMemberListManager);

        final ServiceName<Raft> raftServiceName = raftServiceName(logStream.getLogName());

        serviceContainer.createService(raftServiceName, raftService)
                        .group(RAFT_SERVICE_GROUP)
                        .dependency(ACTOR_SCHEDULER_SERVICE, raftService.getActorSchedulerInjector())
                        .dependency(TransportServiceNames.bufferingServerTransport(TransportServiceNames.REPLICATION_API_SERVER_NAME),
                                    raftService.getServerTransportInjector())
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

    public void onCreatePartitionMessage(final DirectBuffer buffer, final int offset, final int length)
    {
        createPartitionMessage.wrap(buffer, offset, length);

        LOG.debug("Received create partition message for partition {}", createPartitionMessage.getPartitionId());

        final int partitionId = createPartitionMessage.getPartitionId();

        if (!partitionExists(partitionId))
        {
            LOG.debug("Creating partition {}", createPartitionMessage.getPartitionId());
            createPartition(createPartitionMessage.getTopicName(), partitionId);
        }
        else
        {
            LOG.debug("Partition {} exists already. Ignoring creation request.", createPartitionMessage.getPartitionId());
        }
    }

    public CompletableFuture<Topology> requestTopology()
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

        commandQueue.runAsync(() ->
        {
            LOG.trace("ADD raft {} for partition {} state {}.", raft.getSocketAddress(), raft.getLogStream()
                                                                                             .getPartitionId(), raft.getState());
            rafts.add(raft);

            startLogStreamServiceControllers.add(new StartLogStreamServiceController(raftServiceName, raft, serviceContainer));

            if (isRaftCreator)
            {
                final Iterator<MemberRaftComposite> iterator = context.getMemberListService()
                                                                      .iterator();
                while (iterator.hasNext())
                {
                    final MemberRaftComposite next = iterator.next();
                    if (!next.getMember()
                             .getAddress()
                             .equals(transportComponentCfg.managementApi.toSocketAddress(transportComponentCfg.host)))
                    {
                        // TODO don't invite all members to raft
                        inviteMemberToRaft(next.getMember().getAddress(), raft);
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

        commandQueue.runAsync(() ->
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

            for (int i = 0; i < startLogStreamServiceControllers.size(); i++)
            {
                final Raft r = startLogStreamServiceControllers.get(i)
                                                               .getRaft();
                final LogStream stream = r.getLogStream();
                if (partitionId == stream.getPartitionId())
                {
                    startLogStreamServiceControllers.remove(i);
                    break;
                }
            }
        });
    }
}
