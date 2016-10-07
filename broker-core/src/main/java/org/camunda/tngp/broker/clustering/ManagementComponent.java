package org.camunda.tngp.broker.clustering;

import static org.camunda.tngp.broker.clustering.service.ClientChannelManagerService.*;
import static org.camunda.tngp.broker.clustering.service.ClusterManagerService.*;
import static org.camunda.tngp.broker.clustering.service.GossipProtocolService.*;
import static org.camunda.tngp.broker.clustering.service.RaftProtocolService.*;
import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.transport.TransportServiceNames.*;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.*;

import org.camunda.tngp.broker.clustering.gossip.handler.GossipRequestHandler;
import org.camunda.tngp.broker.clustering.gossip.handler.ProbeRequestHandler;
import org.camunda.tngp.broker.clustering.gossip.task.GossipTask;
import org.camunda.tngp.broker.clustering.management.ClusterManagerTask;
import org.camunda.tngp.broker.clustering.management.handler.InvitationRequestHandler;
import org.camunda.tngp.broker.clustering.raft.handler.AppendRequestHandler;
import org.camunda.tngp.broker.clustering.raft.handler.AppendResponseHandler;
import org.camunda.tngp.broker.clustering.raft.handler.JoinRequestHandler;
import org.camunda.tngp.broker.clustering.raft.handler.VoteRequestHandler;
import org.camunda.tngp.broker.clustering.raft.task.RaftTask;
import org.camunda.tngp.broker.clustering.service.ClientChannelManagerService;
import org.camunda.tngp.broker.clustering.service.ClusterManagerService;
import org.camunda.tngp.broker.clustering.service.GossipProtocolService;
import org.camunda.tngp.broker.clustering.service.RaftProtocolService;
import org.camunda.tngp.broker.clustering.service.TransportConnectionPoolService;
import org.camunda.tngp.broker.clustering.worker.CompositeManagementRequestDispatcher;
import org.camunda.tngp.broker.clustering.worker.ManagementRequestDispatcher;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContextService;
import org.camunda.tngp.broker.clustering.worker.cfg.ManagementComponentCfg;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementDataFrameHandler;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementRequestHandler;
import org.camunda.tngp.broker.logstreams.LogStreamServiceNames;
import org.camunda.tngp.broker.services.DataFramePoolService;
import org.camunda.tngp.broker.services.DeferredResponsePoolService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.transport.worker.AsyncRequestWorkerService;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class ManagementComponent implements Component
{

    public static final String WORKER_NAME = "management-worker.0";

    @Override
    public void init(final SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();

        final ManagementComponentCfg managementComponentCfg = configurationManager.readEntry("network.management", ManagementComponentCfg.class);

        final ManagementWorkerContext workerContext = new ManagementWorkerContext();

        final ManagementRequestDispatcher clusterManagerDispatcher = new ManagementRequestDispatcher(5, new ManagementRequestHandler[] {
            new InvitationRequestHandler(workerContext)
        });

        final ManagementRequestDispatcher gossipDispatcher = new ManagementRequestDispatcher(3, new ManagementRequestHandler[] {
            new GossipRequestHandler(workerContext),
            new ProbeRequestHandler(workerContext)
        });

        final ManagementRequestDispatcher raftDispatcher = new ManagementRequestDispatcher(4, new ManagementRequestHandler[] {
            new VoteRequestHandler(workerContext),
            new JoinRequestHandler(workerContext)
        }, new ManagementDataFrameHandler[] {
            new AppendRequestHandler(workerContext),
            new AppendResponseHandler(workerContext)
        });

        workerContext.setRequestHandler(new CompositeManagementRequestDispatcher(new ManagementRequestDispatcher[] {
            clusterManagerDispatcher,
            gossipDispatcher,
            raftDispatcher
        }));

        workerContext.setWorkerTasks(new WorkerTask[]
        {
            new GossipTask(),
            new ClusterManagerTask(),
            new RaftTask()
        });

        final DataFramePoolService dataFramePoolService = new DataFramePoolService(32);
        final DeferredResponsePoolService responsePoolService = new DeferredResponsePoolService(3);
        final AsyncRequestWorkerService workerService = new AsyncRequestWorkerService();
        final ManagementWorkerContextService workerContextService = new ManagementWorkerContextService(workerContext);
        final ClientChannelManagerService clientChannelManagerService = new ClientChannelManagerService(managementComponentCfg);
        final TransportConnectionPoolService transportConnectionPoolService = new TransportConnectionPoolService();

        final ClusterManagerService clusterManagerService = new ClusterManagerService();
        final GossipProtocolService gossipProtocolService = new GossipProtocolService(managementComponentCfg);
        final RaftProtocolService raftProtocolService = new RaftProtocolService(configurationManager);

        final ServiceName<Dispatcher> receiveBufferService = serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME);
        final ServiceName<DataFramePool> workerDataFramePoolServiceName = workerDataFramePoolServiceName(WORKER_NAME);

        serviceContainer.createService(workerDataFramePoolServiceName, dataFramePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, dataFramePoolService.getSendBufferInector())
            .install();

        serviceContainer.createService(TransportConnectionPoolService.TRANSPORT_CONNECTION_POOL, transportConnectionPoolService)
            .dependency(TRANSPORT, transportConnectionPoolService.getTransportInjector())
            .install();

        serviceContainer.createService(GOSSIP_PROTOCOL, gossipProtocolService)
            .dependency(TRANSPORT, gossipProtocolService.getTransportInjector())
            .dependency(CLIENT_CHANNEL_MANAGER, gossipProtocolService.getClientChannelManagerInjector())
            .dependency(TransportConnectionPoolService.TRANSPORT_CONNECTION_POOL, gossipProtocolService.getTransportConnectionPool())
            .install();

        serviceContainer.createService(RAFT_PROTOCOL, raftProtocolService)
            .dependency(TRANSPORT, raftProtocolService.getTransportInjector())
            .dependency(CLIENT_CHANNEL_MANAGER, raftProtocolService.getClientChannelManagerInjector())
            .dependency(TransportConnectionPoolService.TRANSPORT_CONNECTION_POOL, raftProtocolService.getTransportConnectionPoolInjector())
            .dependency(workerDataFramePoolServiceName, raftProtocolService.getDataFramePoolInjector())
            .install();

        serviceContainer.createService(CLUSTER_MANAGER, clusterManagerService)
            .dependency(GOSSIP_PROTOCOL, clusterManagerService.getGossipProtocolInjector())
            .dependency(RAFT_PROTOCOL, clusterManagerService.getRaftProtocolInjector())
            .dependency(CLIENT_CHANNEL_MANAGER, clusterManagerService.getClientChannelManagerInjector())
            .dependency(TransportConnectionPoolService.TRANSPORT_CONNECTION_POOL, clusterManagerService.getTransportConnectionPoolInjector())
            .dependency(workerDataFramePoolServiceName, clusterManagerService.getDataFramePoolInjector())
            .groupReference(LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP, clusterManagerService.getLogStreamsGroupReference())
            .install();

        serviceContainer.createService(CLIENT_CHANNEL_MANAGER, clientChannelManagerService)
            .dependency(TRANSPORT, clientChannelManagerService.getTransportInjector())
            .dependency(receiveBufferService, clientChannelManagerService.getReceiveBufferInjector())
            .dependency(TransportConnectionPoolService.TRANSPORT_CONNECTION_POOL, clientChannelManagerService.getTransportConnectionPoolInjector())
            .install();

        final ServiceName<DeferredResponsePool> responsePoolServiceName = workerResponsePoolServiceName(WORKER_NAME);
        serviceContainer.createService(responsePoolServiceName, responsePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, responsePoolService.getSendBufferInector())
            .install();

        final ServiceName<AsyncRequestWorkerContext> workerContextServiceName = workerContextServiceName(WORKER_NAME);
        serviceContainer.createService(workerContextServiceName, workerContextService)
            .dependency(responsePoolServiceName, workerContextService.getResponsePoolInjector())
            .dependency(receiveBufferService, workerContextService.getRequestBufferInjector())
            .dependency(TRANSPORT, workerContextService.getTransportInjector())
            .dependency(GOSSIP_PROTOCOL, workerContextService.getGossipProtocolInjector())
            .dependency(RAFT_PROTOCOL, workerContextService.getRaftProtocolInjector())
            .dependency(CLIENT_CHANNEL_MANAGER, workerContextService.getClientChannelManagerInjector())
            .dependency(CLUSTER_MANAGER, workerContextService.getClusterManagerInjector())
            .install();

        serviceContainer.createService(workerServiceName(WORKER_NAME), workerService)
            .dependency(workerContextServiceName, workerService.getWorkerContextInjector())
            .dependency(AGENT_RUNNER_SERVICE, workerService.getAgentRunnerInjector())
            .install();
    }

}
