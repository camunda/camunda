package org.camunda.tngp.broker.transport;

import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.COUNTERS_MANAGER_SERVICE;
import static org.camunda.tngp.broker.transport.TransportServiceNames.CLIENT_API_MESSAGE_HANDLER;
import static org.camunda.tngp.broker.transport.TransportServiceNames.CLIENT_API_SOCKET_BINDING_NAME;
import static org.camunda.tngp.broker.transport.TransportServiceNames.TRANSPORT;
import static org.camunda.tngp.broker.transport.TransportServiceNames.TRANSPORT_SEND_BUFFER;
import static org.camunda.tngp.broker.transport.TransportServiceNames.serverSocketBindingReceiveBufferName;
import static org.camunda.tngp.broker.transport.TransportServiceNames.serverSocketBindingServiceName;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.logstreams.LogStreamServiceNames;
import org.camunda.tngp.broker.services.DispatcherService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.transport.cfg.SocketBindingCfg;
import org.camunda.tngp.broker.transport.cfg.TransportComponentCfg;
import org.camunda.tngp.broker.transport.clientapi.ClientApiMessageHandlerService;
import org.camunda.tngp.broker.transport.clientapi.ClientApiSocketBindingService;
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
    }

    protected CompletableFuture<Void> bindClientApi(ServiceContainer serviceContainer, TransportComponentCfg transportComponentCfg)
    {
        final SocketBindingCfg socketBindingCfg = transportComponentCfg.clientApi;

        final int port = socketBindingCfg.port;

        String hostname = socketBindingCfg.hostname;
        if (hostname == null || hostname.isEmpty())
        {
            hostname = transportComponentCfg.hostname;
        }

        final InetSocketAddress bindAddr = new InetSocketAddress(hostname, port);

        int receiveBufferSize = socketBindingCfg.receiveBufferSize * 1024 * 1024;
        if (receiveBufferSize == -1)
        {
            receiveBufferSize = transportComponentCfg.defaultReceiveBufferSize;
        }

        final DispatcherService controlMessageBufferService = new DispatcherService(receiveBufferSize);
        serviceContainer.createService(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), controlMessageBufferService)
            .dependency(AGENT_RUNNER_SERVICE, controlMessageBufferService.getAgentRunnerInjector())
            .dependency(COUNTERS_MANAGER_SERVICE, controlMessageBufferService.getCountersManagerInjector())
            .install();

        final ClientApiMessageHandlerService messageHandlerService = new ClientApiMessageHandlerService();
        serviceContainer.createService(CLIENT_API_MESSAGE_HANDLER, messageHandlerService)
            .dependency(TRANSPORT_SEND_BUFFER, messageHandlerService.getSendBufferInjector())
            .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), messageHandlerService.getControlMessageBufferInjector())
            .groupReference(LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP, messageHandlerService.getLogStreamsGroupReference())
            .install();

        final ClientApiSocketBindingService socketBindingService = new ClientApiSocketBindingService(CLIENT_API_SOCKET_BINDING_NAME, bindAddr);
        return serviceContainer.createService(serverSocketBindingServiceName(CLIENT_API_SOCKET_BINDING_NAME), socketBindingService)
            .dependency(TRANSPORT, socketBindingService.getTransportInjector())
            .dependency(CLIENT_API_MESSAGE_HANDLER, socketBindingService.getMessageHandlerInjector())
            .install();
    }

}
