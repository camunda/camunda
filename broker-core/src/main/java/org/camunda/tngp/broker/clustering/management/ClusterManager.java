package org.camunda.tngp.broker.clustering.management;

import static org.camunda.tngp.broker.clustering.ClusterServiceNames.PEER_LOCAL_SERVICE;
import static org.camunda.tngp.broker.clustering.ClusterServiceNames.RAFT_SERVICE_GROUP;
import static org.camunda.tngp.broker.clustering.ClusterServiceNames.clientChannelManagerName;
import static org.camunda.tngp.broker.clustering.ClusterServiceNames.raftContextServiceName;
import static org.camunda.tngp.broker.clustering.ClusterServiceNames.raftServiceName;
import static org.camunda.tngp.broker.clustering.ClusterServiceNames.subscriptionServiceName;
import static org.camunda.tngp.broker.clustering.ClusterServiceNames.transportConnectionPoolName;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.transport.TransportServiceNames.REPLICATION_SOCKET_BINDING_NAME;
import static org.camunda.tngp.broker.transport.TransportServiceNames.TRANSPORT;
import static org.camunda.tngp.broker.transport.TransportServiceNames.TRANSPORT_SEND_BUFFER;
import static org.camunda.tngp.broker.transport.TransportServiceNames.serverSocketBindingReceiveBufferName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.clustering.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.channel.ClientChannelManagerService;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.management.handler.ClusterManagerFragmentHandler;
import org.camunda.tngp.broker.clustering.management.message.InvitationRequest;
import org.camunda.tngp.broker.clustering.management.message.InvitationResponse;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.service.RaftContextService;
import org.camunda.tngp.broker.clustering.raft.service.RaftService;
import org.camunda.tngp.broker.clustering.service.SubscriptionService;
import org.camunda.tngp.broker.clustering.service.TransportConnectionPoolService;
import org.camunda.tngp.broker.clustering.util.MessageWriter;
import org.camunda.tngp.broker.clustering.util.RequestResponseController;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class ClusterManager implements Agent
{
    private final ClusterManagerContext context;
    private final ServiceStartContext serviceContainer;

    private final List<Raft> rafts;

    private final ManyToOneConcurrentArrayQueue<Runnable> managementCmdQueue;
    private final Consumer<Runnable> commandConsumer;

    private final List<RequestResponseController> activeRequestController;

    private final ClusterManagerFragmentHandler fragmentHandler;

    private final InvitationRequest invitationRequest;
    private final InvitationResponse invitationResponse;

    private final MessageWriter messageWriter;

    public ClusterManager(final ClusterManagerContext context, final ServiceStartContext serviceContainer)
    {
        this.context = context;
        this.serviceContainer = serviceContainer;
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

    @Override
    public String roleName()
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
                    final InvitationRequest invitationRequest = new InvitationRequest();
                    invitationRequest.id(raft.id())
                        .term(raft.term())
                        .members(raft.configuration().members());

                    final ClientChannelManager clientChannelManager = context.getClientChannelManager();
                    final TransportConnectionPool connections = context.getConnections();
                    final RequestResponseController requestController = new RequestResponseController(clientChannelManager, connections);
                    requestController.open(copy.managementEndpoint(), invitationRequest);
                    activeRequestController.add(requestController);
                }
            }

        });
    }

    public void addStream(final LogStream logStream)
    {
        managementCmdQueue.add(() ->
        {
            final PeerList peers = context.getPeers();
            if (peers.sizeVolatile() == 1)
            {
                createRaft(logStream, Collections.emptyList());
            }
        });
    }

    public void addRaft(final Raft raft)
    {
        managementCmdQueue.add(() -> rafts.add(raft));
    }

    public void removeRaft(final Raft raft)
    {
        managementCmdQueue.add(() ->
        {
            for (int i = 0; i < rafts.size(); i++)
            {
                final Raft r = rafts.get(i);
                if (r.id() == raft.id())
                {
                    rafts.remove(i);
                    break;
                }
            }
        });
    }

    public void createRaft(LogStream logStream, List<Member> members)
    {
        final int id = logStream.getId();
        final String component = String.format("raft.%d", id);

        final TransportConnectionPoolService transportConnectionPoolService = new TransportConnectionPoolService();

        final ServiceName<TransportConnectionPool> transportConnectionPoolServiceName = transportConnectionPoolName(component);
        serviceContainer.createService(transportConnectionPoolServiceName, transportConnectionPoolService)
            .dependency(TRANSPORT, transportConnectionPoolService.getTransportInjector())
            .install();

        // TODO: make it configurable
        final ClientChannelManagerService clientChannelManagerService = new ClientChannelManagerService(128);
        final ServiceName<ClientChannelManager> clientChannelManagerServiceName = clientChannelManagerName(component);
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
        final RaftContextService raftContextService = new RaftContextService();
        final ServiceName<RaftContext> raftContextServiceName = raftContextServiceName(String.valueOf(id));
        serviceContainer.createService(raftContextServiceName, raftContextService)
            .dependency(PEER_LOCAL_SERVICE, raftContextService.getLocalPeerInjector())
            .dependency(TRANSPORT_SEND_BUFFER, raftContextService.getSendBufferInjector())
            .dependency(clientChannelManagerServiceName, raftContextService.getClientChannelManagerInjector())
            .dependency(transportConnectionPoolServiceName, raftContextService.getTransportConnectionPoolInjector())
            .dependency(subscriptionServiceName, raftContextService.getSubscriptionInjector())
            .install();

        final RaftService raftService = new RaftService(logStream, new CopyOnWriteArrayList<>(members));
        final ServiceName<Raft> raftServiceName = raftServiceName(String.valueOf(id));
        serviceContainer.createService(raftServiceName, raftService)
            .group(RAFT_SERVICE_GROUP)
            .dependency(AGENT_RUNNER_SERVICE, raftService.getAgentRunnerInjector())
            .dependency(raftContextServiceName, raftService.getRaftContextInjector())
            .install();
    }

    public int onInvitationRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId)
    {
        invitationRequest.reset();
        invitationRequest.wrap(buffer, offset, length);

        final int id = invitationRequest.id();

        // TODO: start log stream as service, if it is not already started as a service!
        final LogStream stream = LogStreams.createFsLogStream("raft-log-" + context.getLocalPeer().managementEndpoint().port(), id)
                .deleteOnClose(false)
                .logDirectory("/tmp/raft-log-" + context.getLocalPeer().managementEndpoint().port() + "-" + id)
                .agentRunnerService(context.getAgentRunner().logAppenderAgentRunnerService())
                .writeBufferAgentRunnerService(context.getAgentRunner().conductorAgentRunnerService())
                .logSegmentSize(512 * 1024 * 1024)
                .build();
        stream.open();

        createRaft(stream, new ArrayList<>(invitationRequest.members()));

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
