package org.camunda.tngp.broker.transport;

import static org.camunda.tngp.broker.event.TopicSubscriptionServiceNames.*;
import static org.camunda.tngp.broker.services.DispatcherSubscriptionNames.*;
import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.*;
import static org.camunda.tngp.broker.transport.TransportServiceNames.*;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.event.TopicSubscriptionServiceNames;
import org.camunda.tngp.broker.logstreams.LogStreamServiceNames;
import org.camunda.tngp.broker.services.DispatcherService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.transport.binding.ServerSocketBindingService;
import org.camunda.tngp.broker.transport.cfg.SocketBindingCfg;
import org.camunda.tngp.broker.transport.cfg.TransportComponentCfg;
import org.camunda.tngp.broker.transport.clientapi.ClientApiMessageHandlerService;
import org.camunda.tngp.broker.transport.clientapi.ClientApiSocketBindingService;
import org.camunda.tngp.broker.transport.controlmessage.ControlMessageHandlerManagerService;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.servicecontainer.ServiceContainer;

public class TransportComponent implements Component
{
    @Override
    public void init(SystemContext context)
    {
        final TransportComponentCfg transportComponentCfg = context.getConfigurationManager().readEntry("network", TransportComponentCfg.class);
        final ServiceContainer serviceContainer = context.getServiceContainer();

        final int sendBufferSize = transportComponentCfg.sendBufferSize * 1024 * 1024;
        final DispatcherService sendBufferService = new DispatcherService(sendBufferSize);
        serviceContainer.createService(TRANSPORT_SEND_BUFFER, sendBufferService)
            .dependency(AGENT_RUNNER_SERVICE, sendBufferService.getAgentRunnerInjector())
            .dependency(COUNTERS_MANAGER_SERVICE, sendBufferService.getCountersManagerInjector())
            .install();

        final TransportService transportService = new TransportService();
        serviceContainer.createService(TRANSPORT, transportService)
            .dependency(TRANSPORT_SEND_BUFFER, transportService.getSendBufferInjector())
            .dependency(AGENT_RUNNER_SERVICE, transportService.getAgentRunnerInjector())
            .install();

        context.addRequiredStartAction(bindClientApi(serviceContainer, transportComponentCfg));
        context.addRequiredStartAction(bindManagementApi(serviceContainer, transportComponentCfg));
        context.addRequiredStartAction(bindRaftApi(serviceContainer, transportComponentCfg));
    }

    protected CompletableFuture<Void> bindClientApi(ServiceContainer serviceContainer, TransportComponentCfg transportComponentCfg)
    {
        final SocketBindingCfg socketBindingCfg = transportComponentCfg.clientApi;

        final int port = socketBindingCfg.port;

        String hostname = socketBindingCfg.host;
        if (hostname == null || hostname.isEmpty())
        {
            hostname = transportComponentCfg.host;
        }

        final InetSocketAddress bindAddr = new InetSocketAddress(hostname, port);

        int receiveBufferSize = socketBindingCfg.receiveBufferSize * 1024 * 1024;
        if (receiveBufferSize == -1)
        {
            receiveBufferSize = transportComponentCfg.defaultReceiveBufferSize;
        }

        long controlMessageRequestTimeoutInMillis = socketBindingCfg.controlMessageRequestTimeoutInMillis;
        if (controlMessageRequestTimeoutInMillis <= 0)
        {
            controlMessageRequestTimeoutInMillis = Long.MAX_VALUE;
        }

        final DispatcherService controlMessageBufferService = new DispatcherService(Dispatchers.create(null)
                .bufferSize(receiveBufferSize)
                .subscriptions(TRANSPORT_CONTROL_MESSAGE_HANDLER_SUBSCRIPTION));

        serviceContainer.createService(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), controlMessageBufferService)
            .dependency(AGENT_RUNNER_SERVICE, controlMessageBufferService.getAgentRunnerInjector())
            .dependency(COUNTERS_MANAGER_SERVICE, controlMessageBufferService.getCountersManagerInjector())
            .install();

        final ClientApiMessageHandlerService messageHandlerService = new ClientApiMessageHandlerService();
        serviceContainer.createService(CLIENT_API_MESSAGE_HANDLER, messageHandlerService)
            .dependency(TRANSPORT_SEND_BUFFER, messageHandlerService.getSendBufferInjector())
            .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), messageHandlerService.getControlMessageBufferInjector())
            .dependency(TopicSubscriptionServiceNames.TOPIC_SUBSCRIPTION_SERVICE, messageHandlerService.getTopicSubcriptionServiceInjector())
            .dependency(TASK_QUEUE_SUBSCRIPTION_MANAGER, messageHandlerService.getTaskSubcriptionManagerInjector())
            .groupReference(LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP, messageHandlerService.getLogStreamsGroupReference())
            .install();

        final ClientApiSocketBindingService socketBindingService = new ClientApiSocketBindingService(CLIENT_API_SOCKET_BINDING_NAME, bindAddr);
        final CompletableFuture<Void> socketBindingServiceFuture = serviceContainer.createService(serverSocketBindingServiceName(CLIENT_API_SOCKET_BINDING_NAME), socketBindingService)
            .dependency(TRANSPORT, socketBindingService.getTransportInjector())
            .dependency(CLIENT_API_MESSAGE_HANDLER, socketBindingService.getMessageHandlerInjector())
            .install();

        final ControlMessageHandlerManagerService controlMessageHandlerManagerService = new ControlMessageHandlerManagerService(controlMessageRequestTimeoutInMillis);
        final CompletableFuture<Void> controlMessageServiceFuture = serviceContainer.createService(CONTROL_MESSAGE_HANDLER_MANAGER, controlMessageHandlerManagerService)
            .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), controlMessageHandlerManagerService.getControlMessageBufferInjector())
            .dependency(TRANSPORT_SEND_BUFFER, controlMessageHandlerManagerService.getSendBufferInjector())
            .dependency(AGENT_RUNNER_SERVICE, controlMessageHandlerManagerService.getAgentRunnerServicesInjector())
            .dependency(TASK_QUEUE_SUBSCRIPTION_MANAGER, controlMessageHandlerManagerService.getTaskSubscriptionManagerInjector())
            .dependency(TOPIC_SUBSCRIPTION_SERVICE, controlMessageHandlerManagerService.getTopicSubscriptionServiceInjector())
            .install();

        // make sure that all services are installed
        return CompletableFuture.allOf(socketBindingServiceFuture, controlMessageServiceFuture);
    }

    protected CompletableFuture<Void> bindManagementApi(ServiceContainer serviceContainer, TransportComponentCfg transportComponentCfg)
    {
        final SocketBindingCfg socketBindingCfg = transportComponentCfg.managementApi;

        final int port = socketBindingCfg.port;

        String host = socketBindingCfg.host;
        if (host == null || host.isEmpty())
        {
            host = transportComponentCfg.host;
        }

        final InetSocketAddress bindAddr = new InetSocketAddress(host, port);

        int receiveBufferSize = socketBindingCfg.receiveBufferSize * 1024 * 1024;
        if (receiveBufferSize == -1)
        {
            receiveBufferSize = transportComponentCfg.defaultReceiveBufferSize;
        }

        final DispatcherService receiveBufferService = new DispatcherService(receiveBufferSize);
        serviceContainer.createService(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), receiveBufferService)
            .dependency(AGENT_RUNNER_SERVICE, receiveBufferService.getAgentRunnerInjector())
            .dependency(COUNTERS_MANAGER_SERVICE, receiveBufferService.getCountersManagerInjector())
            .install();

        final ServerSocketBindingService socketBindingService = new ServerSocketBindingService(MANAGEMENT_SOCKET_BINDING_NAME, bindAddr);
        return serviceContainer.createService(serverSocketBindingServiceName(MANAGEMENT_SOCKET_BINDING_NAME), socketBindingService)
            .dependency(TRANSPORT, socketBindingService.getTransportInjector())
            .dependency(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), socketBindingService.getReceiveBufferInjector())
            .install();
    }

    protected CompletableFuture<Void> bindRaftApi(ServiceContainer serviceContainer, TransportComponentCfg transportComponentCfg)
    {
        final SocketBindingCfg socketBindingCfg = transportComponentCfg.replicationApi;

        final int port = socketBindingCfg.port;

        String host = socketBindingCfg.host;
        if (host == null || host.isEmpty())
        {
            host = transportComponentCfg.host;
        }

        final InetSocketAddress bindAddr = new InetSocketAddress(host, port);

        int receiveBufferSize = socketBindingCfg.receiveBufferSize * 1024 * 1024;
        if (receiveBufferSize == -1)
        {
            receiveBufferSize = transportComponentCfg.defaultReceiveBufferSize;
        }

        final DispatcherService receiveBufferService = new DispatcherService(receiveBufferSize);
        serviceContainer.createService(serverSocketBindingReceiveBufferName(REPLICATION_SOCKET_BINDING_NAME), receiveBufferService)
            .dependency(AGENT_RUNNER_SERVICE, receiveBufferService.getAgentRunnerInjector())
            .dependency(COUNTERS_MANAGER_SERVICE, receiveBufferService.getCountersManagerInjector())
            .install();

        final ServerSocketBindingService socketBindingService = new ServerSocketBindingService(REPLICATION_SOCKET_BINDING_NAME, bindAddr);
        return serviceContainer.createService(serverSocketBindingServiceName(REPLICATION_SOCKET_BINDING_NAME), socketBindingService)
            .dependency(TRANSPORT, socketBindingService.getTransportInjector())
            .dependency(serverSocketBindingReceiveBufferName(REPLICATION_SOCKET_BINDING_NAME), socketBindingService.getReceiveBufferInjector())
            .install();
    }
}
