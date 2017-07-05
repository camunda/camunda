package io.zeebe.broker.clustering.management;

import static io.zeebe.broker.clustering.ClusterServiceNames.*;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.transport.TransportServiceNames.REPLICATION_SOCKET_BINDING_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.TRANSPORT;
import static io.zeebe.broker.transport.TransportServiceNames.TRANSPORT_SEND_BUFFER;
import static io.zeebe.broker.transport.TransportServiceNames.serverSocketBindingReceiveBufferName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.zeebe.broker.Loggers;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import io.zeebe.broker.clustering.channel.ClientChannelManagerService;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.management.config.ClusterManagementConfig;
import io.zeebe.broker.clustering.management.handler.ClusterManagerFragmentHandler;
import io.zeebe.broker.clustering.management.message.InvitationRequest;
import io.zeebe.broker.clustering.management.message.InvitationResponse;
import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.MetaStore;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.service.RaftContextService;
import io.zeebe.broker.clustering.raft.service.RaftService;
import io.zeebe.broker.clustering.service.SubscriptionService;
import io.zeebe.broker.clustering.service.TransportConnectionPoolService;
import io.zeebe.broker.clustering.util.MessageWriter;
import io.zeebe.broker.clustering.util.RequestResponseController;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.ChannelManager;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.util.actor.Actor;
import org.apache.logging.log4j.Logger;

public class ClusterManager implements Actor
{
    public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final ClusterManagerContext context;
    private final ServiceContainer serviceContainer;

    private final List<Raft> rafts;

    private final ManyToOneConcurrentArrayQueue<Runnable> managementCmdQueue;
    private final Consumer<Runnable> commandConsumer;

    private final List<RequestResponseController> activeRequestController;

    private final ClusterManagerFragmentHandler fragmentHandler;

    private final InvitationRequest invitationRequest;
    private final InvitationResponse invitationResponse;

    private ClusterManagementConfig config;

    private final MessageWriter messageWriter;

    public ClusterManager(final ClusterManagerContext context, final ServiceContainer serviceContainer, ClusterManagementConfig config)
    {
        this.context = context;
        this.serviceContainer = serviceContainer;
        this.config = config;
        this.rafts = new CopyOnWriteArrayList<>();
        this.managementCmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
        this.commandConsumer = (r) -> r.run();
        this.activeRequestController = new CopyOnWriteArrayList<>();
        this.invitationRequest = new InvitationRequest();

        this.fragmentHandler = new ClusterManagerFragmentHandler(this, context.getSubscription());
        this.invitationResponse = new InvitationResponse();
        this.messageWriter = new MessageWriter(context.getSendBuffer());

        context.getPeers().registerListener((p) -> addPeer(p));
    }

    public void open()
    {
        final String metaDirectory = config.directory;

        final LogStreamsManager logStreamManager = context.getLogStreamsManager();

        final File dir = new File(metaDirectory);

        if (!dir.exists())
        {
            try
            {
                dir.getParentFile().mkdirs();
                Files.createDirectory(dir.toPath());
            }
            catch (IOException e)
            {
                LOG.error("Unable to create directory {}: {}", dir, e);
            }
        }

        final File[] metaFiles = dir.listFiles();

        if (metaFiles != null && metaFiles.length > 0)
        {
            for (int i = 0; i < metaFiles.length; i++)
            {
                final File file = metaFiles[i];
                final MetaStore meta = new MetaStore(file.getAbsolutePath());

                final int partitionId = meta.loadPartitionId();
                final DirectBuffer topicName = meta.loadTopicName();

                LogStream logStream = logStreamManager.getLogStream(topicName, partitionId);

                if (logStream == null)
                {
                    final String directory = meta.loadLogDirectory();
                    logStream = logStreamManager.createLogStream(topicName, partitionId, directory);
                }

                createRaft(logStream, meta, Collections.emptyList(), false);
            }
        }
        else if (context.getPeers().sizeVolatile() == 1)
        {
            logStreamManager.forEachLogStream(logStream -> createRaft(logStream, Collections.emptyList(), true));
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

        workcount += managementCmdQueue.drain(commandConsumer);
        workcount += fragmentHandler.doWork();

        int i = 0;
        while (i < activeRequestController.size())
        {
            final RequestResponseController requestController = activeRequestController.get(i);
            workcount += requestController.doWork();

            if (requestController.isFailed() || requestController.isResponseAvailable())
            {
                requestController.close();
            }

            if (requestController.isClosed())
            {
                activeRequestController.remove(i);
            }
            else
            {
                i++;
            }
        }

        return workcount;
    }

    public void addPeer(final Peer peer)
    {
        final Peer copy = new Peer();
        copy.wrap(peer);
        managementCmdQueue.add(() ->
        {

            for (int i = 0; i < rafts.size(); i++)
            {
                final Raft raft = rafts.get(i);
                if (raft.needMembers())
                {
                    // TODO: if this should be garbage free, we have to limit
                    // the number of concurrent invitations.
                    final LogStream logStream = raft.stream();
                    final InvitationRequest invitationRequest = new InvitationRequest()
                        .topicName(logStream.getTopicName())
                        .partitionId(logStream.getPartitionId())
                        .term(raft.term())
                        .members(raft.configuration().members());

                    final ChannelManager clientChannelManager = context.getClientChannelPool();
                    final TransportConnectionPool connections = context.getConnections();
                    final RequestResponseController requestController = new RequestResponseController(clientChannelManager, connections);
                    requestController.open(copy.managementEndpoint(), invitationRequest);
                    activeRequestController.add(requestController);
                }
            }

        });
    }

    public void addRaft(final Raft raft)
    {
        managementCmdQueue.add(() ->
        {
            context.getLocalPeer().addRaft(raft);
            rafts.add(raft);
        });
    }

    public void removeRaft(final Raft raft)
    {
        final LogStream logStream = raft.stream();
        final DirectBuffer topicName = logStream.getTopicName();
        final int partitionId = logStream.getPartitionId();

        managementCmdQueue.add(() ->
        {
            for (int i = 0; i < rafts.size(); i++)
            {
                final Raft r = rafts.get(i);
                final LogStream stream = r.stream();
                if (topicName.equals(stream.getTopicName()) && partitionId == stream.getPartitionId())
                {
                    context.getLocalPeer().removeRaft(raft);
                    rafts.remove(i);
                    break;
                }
            }
        });
    }

    public void createRaft(LogStream logStream, List<Member> members, boolean bootstrap)
    {
        final FsLogStorage logStorage = (FsLogStorage) logStream.getLogStorage();
        final String path = logStorage.getConfig().getPath();

        final MetaStore meta = new MetaStore(String.format("%s%s.meta", config.directory, logStream.getLogName()));
        meta.storeTopicNameAndPartitionIdAndDirectory(logStream.getTopicName(), logStream.getPartitionId(), path);

        createRaft(logStream, meta, members, bootstrap);
    }

    public void createRaft(LogStream logStream, MetaStore meta, List<Member> members, boolean bootstrap)
    {
        final String logName = logStream.getLogName();
        final String component = String.format("raft.%s", logName);

        final TransportConnectionPoolService transportConnectionPoolService = new TransportConnectionPoolService();

        final ServiceName<TransportConnectionPool> transportConnectionPoolServiceName = transportConnectionPoolName(component);
        serviceContainer.createService(transportConnectionPoolServiceName, transportConnectionPoolService)
            .dependency(TRANSPORT, transportConnectionPoolService.getTransportInjector())
            .install();

        // TODO: make it configurable
        final ClientChannelManagerService clientChannelManagerService = new ClientChannelManagerService(128);
        final ServiceName<ChannelManager> clientChannelManagerServiceName = clientChannelManagerName(component);
        serviceContainer.createService(clientChannelManagerServiceName, clientChannelManagerService)
            .dependency(TRANSPORT, clientChannelManagerService.getTransportInjector())
            .dependency(transportConnectionPoolServiceName, clientChannelManagerService.getTransportConnectionPoolInjector())
            .dependency(serverSocketBindingReceiveBufferName(REPLICATION_SOCKET_BINDING_NAME), clientChannelManagerService.getReceiveBufferInjector())
            .install();

        final SubscriptionService subscriptionService = new SubscriptionService();
        final ServiceName<Subscription> subscriptionServiceName = subscriptionServiceName(component);
        serviceContainer.createService(subscriptionServiceName, subscriptionService)
            .dependency(serverSocketBindingReceiveBufferName(REPLICATION_SOCKET_BINDING_NAME), subscriptionService.getReceiveBufferInjector())
            .install();

        // TODO: provide raft configuration
        final RaftContextService raftContextService = new RaftContextService(serviceContainer);
        final ServiceName<RaftContext> raftContextServiceName = raftContextServiceName(logName);
        serviceContainer.createService(raftContextServiceName, raftContextService)
            .dependency(PEER_LOCAL_SERVICE, raftContextService.getLocalPeerInjector())
            .dependency(TRANSPORT_SEND_BUFFER, raftContextService.getSendBufferInjector())
            .dependency(clientChannelManagerServiceName, raftContextService.getClientChannelManagerInjector())
            .dependency(transportConnectionPoolServiceName, raftContextService.getTransportConnectionPoolInjector())
            .dependency(subscriptionServiceName, raftContextService.getSubscriptionInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, raftContextService.getActorSchedulerInjector())
            .install();

        final RaftService raftService = new RaftService(logStream, meta, new CopyOnWriteArrayList<>(members), bootstrap);
        final ServiceName<Raft> raftServiceName = raftServiceName(logName);
        serviceContainer.createService(raftServiceName, raftService)
            .group(RAFT_SERVICE_GROUP)
            .dependency(ACTOR_SCHEDULER_SERVICE, raftService.getActorSchedulerInjector())
            .dependency(raftContextServiceName, raftService.getRaftContextInjector())
            .install();
    }

    public int onInvitationRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId)
    {
        invitationRequest.reset();
        invitationRequest.wrap(buffer, offset, length);

        final DirectBuffer topicName = invitationRequest.topicName();
        final int partitionId = invitationRequest.partitionId();

        final LogStreamsManager logStreamManager = context.getLogStreamsManager();
        final LogStream logStream = logStreamManager.createLogStream(topicName, partitionId);

        createRaft(logStream, new ArrayList<>(invitationRequest.members()), false);

        invitationResponse.reset();
        messageWriter.protocol(Protocols.REQUEST_RESPONSE)
            .channelId(channelId)
            .connectionId(connectionId)
            .requestId(requestId)
            .message(invitationResponse)
            .tryWriteMessage();

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

}
