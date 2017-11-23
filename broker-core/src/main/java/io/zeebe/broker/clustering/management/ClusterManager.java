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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerListIterator;
import io.zeebe.broker.clustering.management.config.ClusterManagementConfig;
import io.zeebe.broker.clustering.management.handler.ClusterManagerFragmentHandler;
import io.zeebe.broker.clustering.management.message.CreatePartitionMessage;
import io.zeebe.broker.clustering.management.message.InvitationRequest;
import io.zeebe.broker.clustering.management.message.InvitationResponse;
import io.zeebe.broker.clustering.raft.RaftPersistentFileStorage;
import io.zeebe.broker.clustering.raft.RaftService;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.broker.transport.TransportServiceNames;
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

    private ClusterManagementConfig config;

    //    private final MessageWriter messageWriter;
    private final ServerResponse response = new ServerResponse();
    private final ServerInputSubscription inputSubscription;

    private final LogStreamsManager logStreamsManager;

    public ClusterManager(final ClusterManagerContext context, final ServiceContainer serviceContainer, final ClusterManagementConfig config)
    {
        this.context = context;
        this.serviceContainer = serviceContainer;
        this.config = config;
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

        context.getPeers().registerListener(this::addPeer);
    }

    public void open()
    {
        final LogStreamsManager logStreamManager = context.getLogStreamsManager();

        final File storageDirectory = new File(config.directory);

        if (!storageDirectory.exists())
        {
            try
            {
                storageDirectory.getParentFile().mkdirs();
                Files.createDirectory(storageDirectory.toPath());
            }
            catch (final IOException e)
            {
                LOG.error("Unable to create directory {}", storageDirectory, e);
            }
        }

        final SocketAddress socketAddress = context.getLocalPeer().replicationEndpoint();
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
            final boolean isBootstrappingBroker = context.getPeers().sizeVolatile() == 1;
            if (isBootstrappingBroker)
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
    public int doWork() throws Exception
    {
        int workcount = 0;

        workcount += commandQueue.doWork();
        workcount += inputSubscription.poll();

        int i = 0;
        while (i < activeRequestControllers.size())
        {
            final RequestResponseController requestController = activeRequestControllers.get(i);
            workcount += requestController.doWork();

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
            workcount += startLogStreamServiceControllers.get(j).doWork();
        }

        return workcount;
    }

    public void addPeer(final Peer peer)
    {
        final Peer copy = new Peer();
        copy.wrap(peer);

        LOG.debug("Peer {} joined the cluster", copy.managementEndpoint());

        commandQueue.runAsync(() ->
        {

            for (int i = 0; i < rafts.size(); i++)
            {
                final Raft raft = rafts.get(i);

                // only send an invitation request if we are currently leader of the raft group
                if (raft.getState() == RaftState.LEADER)
                {
                    invitePeerToRaft(raft, copy);
                }
            }

        });
    }

    protected void invitePeerToRaft(Raft raft, Peer peer)
    {
        // TODO(menski): implement replication factor
        // TODO: if this should be garbage free, we have to limit
        // the number of concurrent invitations.
        final List<SocketAddress> members = new ArrayList<>();
        members.add(raft.getSocketAddress());
        raft.getMembers().forEach(raftMember -> members.add(raftMember.getRemoteAddress().getAddress()));

        final LogStream logStream = raft.getLogStream();
        final InvitationRequest invitationRequest = new InvitationRequest()
            .topicName(logStream.getTopicName())
            .partitionId(logStream.getPartitionId())
            .term(raft.getTerm())
            .members(members);

        LOG.debug("Send invitation request to {} for partition {} in term {}", peer.managementEndpoint(), logStream.getPartitionId(), raft.getTerm());

        final RequestResponseController requestController = new RequestResponseController(context.getClientTransport());
        requestController.open(peer.managementEndpoint(), invitationRequest, null);
        activeRequestControllers.add(requestController);
    }

    public void addRaft(final ServiceName<Raft> raftServiceName, final Raft raft)
    {
        // this must be determined before we cross the async boundary to avoid race conditions
        final boolean isRaftCreator = raft.getMemberSize() == 0;

        commandQueue.runAsync(() ->
        {
            context.getLocalPeer().addRaft(raft);
            rafts.add(raft);
            startLogStreamServiceControllers.add(new StartLogStreamServiceController(raftServiceName, raft, serviceContainer));

            if (isRaftCreator)
            {
                // invite every known peer
                // TODO: not garbage-free, but required to avoid shared state and conflicting iterator use
                // this should be resolved when we rewrite gossip
                final PeerList knownPeers = context.getPeers().copy();
                final PeerListIterator it = knownPeers.iterator();

                while (it.hasNext())
                {
                    final Peer nextPeer = it.next();
                    if (!nextPeer.equals(context.getLocalPeer()))
                    {
                        invitePeerToRaft(raft, nextPeer);
                    }
                }
            }
        });
    }

    public void removeRaft(final Raft raft)
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
                    context.getLocalPeer().removeRaft(raft);
                    rafts.remove(i);
                    break;
                }
            }

            for (int i = 0; i < startLogStreamServiceControllers.size(); i++)
            {
                final Raft r = startLogStreamServiceControllers.get(i).getRaft();
                final LogStream stream = r.getLogStream();
                if (partitionId == stream.getPartitionId())
                {
                    startLogStreamServiceControllers.remove(i);
                    break;
                }
            }
        });
    }

    public void createRaft(final SocketAddress socketAddress, final LogStream logStream, final List<SocketAddress> members)
    {
        final FsLogStorage logStorage = (FsLogStorage) logStream.getLogStorage();
        final String path = logStorage.getConfig().getPath();

        final RaftPersistentFileStorage storage = new RaftPersistentFileStorage(String.format("%s%s.meta", config.directory, logStream.getLogName()));
        storage
            .setLogStream(logStream)
            .setLogDirectory(path)
            .save();

        createRaft(socketAddress, logStream, members, storage);
    }

    public void createRaft(
            final SocketAddress socketAddress,
            final LogStream logStream,
            final List<SocketAddress> members,
            final RaftPersistentStorage persistentStorage)
    {
        final RaftService raftService = new RaftService(socketAddress, logStream, members, persistentStorage);

        final ServiceName<Raft> raftServiceName = raftServiceName(logStream.getLogName());

        serviceContainer.createService(raftServiceName, raftService)
                        .group(RAFT_SERVICE_GROUP)
                        .dependency(ACTOR_SCHEDULER_SERVICE, raftService.getActorSchedulerInjector())
                        .dependency(TransportServiceNames.bufferingServerTransport(TransportServiceNames.REPLICATION_API_SERVER_NAME), raftService.getServerTransportInjector())
                        .dependency(TransportServiceNames.clientTransport(TransportServiceNames.REPLICATION_API_CLIENT_NAME), raftService.getClientTransportInjector())
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

        final SocketAddress socketAddress = context.getLocalPeer().replicationEndpoint();
        createRaft(socketAddress, logStream, members);
    }

    public boolean onInvitationRequest(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final ServerOutput output,
        final RemoteAddress requestAddress,
        final long requestId)
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

    public void onCreatePartitionMessage(
            final DirectBuffer buffer,
            final int offset,
            final int length)
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

}
