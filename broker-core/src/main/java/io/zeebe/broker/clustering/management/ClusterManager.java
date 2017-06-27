package io.zeebe.broker.clustering.management;

import static io.zeebe.broker.clustering.ClusterServiceNames.PEER_LOCAL_SERVICE;
import static io.zeebe.broker.clustering.ClusterServiceNames.RAFT_SERVICE_GROUP;
import static io.zeebe.broker.clustering.ClusterServiceNames.raftContextServiceName;
import static io.zeebe.broker.clustering.ClusterServiceNames.raftServiceName;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;

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
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RequestResponseController;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.actor.Actor;
import org.slf4j.Logger;

public class ClusterManager implements Actor
{
    public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final ClusterManagerContext context;
    private final ServiceContainer serviceContainer;

    private final List<Raft> rafts;

    private final ManyToOneConcurrentArrayQueue<Runnable> managementCmdQueue;
    private final Consumer<Runnable> commandConsumer;

    private final List<RequestResponseController> activeRequestControllers;

    private final ClusterManagerFragmentHandler fragmentHandler;

    private final InvitationRequest invitationRequest;
    private final InvitationResponse invitationResponse;

    private ClusterManagementConfig config;

//    private final MessageWriter messageWriter;
    protected final ServerResponse response = new ServerResponse();
    protected final ServerInputSubscription inputSubscription;

    public ClusterManager(final ClusterManagerContext context, final ServiceContainer serviceContainer, ClusterManagementConfig config)
    {
        this.context = context;
        this.serviceContainer = serviceContainer;
        this.config = config;
        this.rafts = new CopyOnWriteArrayList<>();
        this.managementCmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
        this.commandConsumer = (r) -> r.run();
        this.activeRequestControllers = new CopyOnWriteArrayList<>();
        this.invitationRequest = new InvitationRequest();

        this.fragmentHandler = new ClusterManagerFragmentHandler(this);
        this.invitationResponse = new InvitationResponse();

        // TODO: kann man das hier blockierend machen?
        inputSubscription = context.getServerTransport()
                .openSubscription("cluster-management", fragmentHandler, fragmentHandler)
                .join();

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
                if (raft.needsMembers())
                {
                    // TODO: if this should be garbage free, we have to limit
                    // the number of concurrent invitations.
                    final LogStream logStream = raft.stream();
                    final InvitationRequest invitationRequest = new InvitationRequest()
                        .topicName(logStream.getTopicName())
                        .partitionId(logStream.getPartitionId())
                        .term(raft.term())
                        .members(raft.configuration().members());

                    final RequestResponseController requestController = new RequestResponseController(context.getClientTransport());
                    requestController.open(copy.managementEndpoint(), invitationRequest, null);
                    activeRequestControllers.add(requestController);
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

        // TODO: provide raft configuration
        final RaftContextService raftContextService = new RaftContextService(serviceContainer);
        final ServiceName<RaftContext> raftContextServiceName = raftContextServiceName(logName);
        serviceContainer.createService(raftContextServiceName, raftContextService)
            .dependency(PEER_LOCAL_SERVICE, raftContextService.getLocalPeerInjector())
            .dependency(TransportServiceNames.clientTransport(TransportServiceNames.REPLICATION_API_CLIENT_NAME), raftContextService.getClientTransportInjector())
            .dependency(TransportServiceNames.bufferingServerTransport(TransportServiceNames.REPLICATION_API_SERVER_NAME), raftContextService.getReplicationApiTransportInjector())
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

        final LogStreamsManager logStreamManager = context.getLogStreamsManager();
        final LogStream logStream = logStreamManager.createLogStream(topicName, partitionId);

        createRaft(logStream, new ArrayList<>(invitationRequest.members()), false);

        invitationResponse.reset();
        response.reset()
            .remoteAddress(requestAddress)
            .requestId(requestId)
            .writer(invitationResponse);

        return output.sendResponse(response);
    }

}
