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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.RaftMembership;
import io.zeebe.broker.clustering.management.config.ClusterManagementConfig;
import io.zeebe.broker.clustering.management.handler.ClusterManagerFragmentHandler;
import io.zeebe.broker.clustering.management.message.InvitationRequest;
import io.zeebe.broker.clustering.management.message.InvitationResponse;
import io.zeebe.broker.clustering.raft.RaftPersistentFileStorage;
import io.zeebe.broker.clustering.raft.RaftService;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.clustering.gossip.RaftMembershipState;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.Protocol;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftPersistentStorage;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RequestResponseController;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;

public class ClusterManager implements Actor, PartitionManager
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

    private ClusterManagementConfig config;

    //    private final MessageWriter messageWriter;
    protected final ServerResponse response = new ServerResponse();
    protected final ServerInputSubscription inputSubscription;

    protected final LogStreamsManager logStreamsManager;

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

        final ClusterManagerFragmentHandler fragmentHandler = new ClusterManagerFragmentHandler(this);
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

                LogStream logStream = logStreamManager.getLogStream(topicName, partitionId);

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
                createPartition(Protocol.SYSTEM_TOPIC_BUF, Protocol.SYSTEM_PARTITION);
                createPartition(LogStream.DEFAULT_TOPIC_NAME_BUFFER, LogStream.DEFAULT_PARTITION_ID);
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
        commandQueue.runAsync(() ->
        {

            for (int i = 0; i < rafts.size(); i++)
            {
                final Raft raft = rafts.get(i);
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

                final RequestResponseController requestController = new RequestResponseController(context.getClientTransport());
                requestController.open(copy.managementEndpoint(), invitationRequest, null);
                activeRequestControllers.add(requestController);
            }

        });
    }

    public void addRaft(final Raft raft)
    {
        commandQueue.runAsync(() ->
        {
            context.getLocalPeer().addRaft(raft);
            rafts.add(raft);
            startLogStreamServiceControllers.add(new StartLogStreamServiceController(raft, serviceContainer));
        });
    }

    public void removeRaft(final Raft raft)
    {
        final LogStream logStream = raft.getLogStream();
        final DirectBuffer topicName = logStream.getTopicName();
        final int partitionId = logStream.getPartitionId();

        commandQueue.runAsync(() ->
        {
            for (int i = 0; i < rafts.size(); i++)
            {
                final Raft r = rafts.get(i);
                final LogStream stream = r.getLogStream();
                if (topicName.equals(stream.getTopicName()) && partitionId == stream.getPartitionId())
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
                if (topicName.equals(stream.getTopicName()) && partitionId == stream.getPartitionId())
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

    /**
     * Creates log stream and sets up raft service to participate in raft group
     */
    protected void createPartition(DirectBuffer topicName, int partitionId)
    {
        final LogStream logStream = logStreamsManager.createLogStream(topicName, partitionId);

        final SocketAddress socketAddress = context.getLocalPeer().replicationEndpoint();
        createRaft(socketAddress, logStream, new ArrayList<>(invitationRequest.members()));
    }

    public void createPartitionAsync(DirectBuffer topicName, int partitionId)
    {
        final UnsafeBuffer nameBuffer = new UnsafeBuffer(new byte[topicName.capacity()]);
        nameBuffer.putBytes(0, topicName, 0, topicName.capacity());

        commandQueue.runAsync(() ->
        {
            createPartition(nameBuffer, partitionId);
        });
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

        final DirectBuffer topicName = invitationRequest.topicName();
        final int partitionId = invitationRequest.partitionId();

        createPartition(topicName, partitionId);

        invitationResponse.reset();
        response.reset()
                .remoteAddress(requestAddress)
                .requestId(requestId)
                .writer(invitationResponse);

        return output.sendResponse(response);
    }

    /*
     * There are some issues with how this connects the gossip state with the system partition processing.
     *
     * * not garbage-free
     * * not thread-safe (peer list is shared state between multiple actors and therefore threads)
     * * not efficient (the stream processor iterates all partitions when it looks for a specific
     *   partition's leader)
     *
     * This code can be refactored in any way when we rewrite gossip.
     * As a baseline, the system stream processor needs to know for a set of partitions
     * if a partition leader becomes known. In that case, it must generate a command on the system log.
     */
    @Override
    public Iterator<Partition> getKnownPartitions()
    {
        final PeerList currentPeers = context.getPeers();
        final PeerList copy = currentPeers.copy();

        final PartitionIterator iterator = new PartitionIterator();
        iterator.wrap(copy);

        return iterator;
    }

    protected class PartitionIterator implements Iterator<Partition>
    {
        protected PeerList peerList;
        protected PartitionImpl currentPartition = null;

        protected Iterator<Peer> peerIterator;
        protected Iterator<RaftMembership> raftMemberIterator;

        public void wrap(PeerList peerList)
        {
            this.peerList = peerList;
            this.peerIterator = peerList.iterator();
            this.raftMemberIterator = null;
            seekNextPartitionLeader();
        }

        @Override
        public boolean hasNext()
        {
            return currentPartition != null;
        }

        protected void seekNextPartitionLeader()
        {
            currentPartition = null;

            while (currentPartition == null)
            {
                if (raftMemberIterator == null || !raftMemberIterator.hasNext())
                {
                    if (!peerIterator.hasNext())
                    {
                        return;
                    }
                    else
                    {
                        raftMemberIterator = peerIterator.next().raftMemberships().iterator();
                    }
                }

                if (raftMemberIterator.hasNext())
                {
                    final RaftMembership membership = raftMemberIterator.next();

                    if (membership.state() == RaftMembershipState.LEADER)
                    {
                        currentPartition = new PartitionImpl(
                                membership.topicNameBuffer(),
                                0,
                                membership.topicNameLength(),
                                membership.partitionId());
                    }
                }
            }
        }

        @Override
        public Partition next()
        {
            final Partition partitionToReturn = currentPartition;
            seekNextPartitionLeader();
            return partitionToReturn;
        }
    }

    protected class PartitionImpl implements Partition
    {

        protected UnsafeBuffer topicName = new UnsafeBuffer(0, 0);
        protected int partitionId;

        public PartitionImpl(DirectBuffer topicName, int offset, int length, int partitionId)
        {
            this.topicName.wrap(topicName, offset, length);
            this.partitionId = partitionId;
        }

        @Override
        public DirectBuffer getTopicName()
        {
            return topicName;
        }

        @Override
        public int getPartitionId()
        {
            return partitionId;
        }

    }
}
