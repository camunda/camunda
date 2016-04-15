package org.camunda.tngp.broker.transport;

import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.transport.TransportServiceNames.*;

import java.net.InetSocketAddress;

import org.camunda.tngp.broker.servicecontainer.ServiceContainer;
import org.camunda.tngp.broker.servicecontainer.ServiceName;
import org.camunda.tngp.broker.services.DispatcherService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.transport.binding.ServerSocketBindingService;
import org.camunda.tngp.broker.transport.cfg.SocketBindingCfg;
import org.camunda.tngp.broker.transport.cfg.TransportComponentCfg;
import org.camunda.tngp.dispatcher.Dispatcher;

public class TransportComponent implements Component
{
    @Override
    public void init(SystemContext context)
    {
        final TransportComponentCfg transportComponentCfg = context.getConfigurationManager().readEntry("network", TransportComponentCfg.class);
        final ServiceContainer serviceContainer = context.getServiceContainer();

        final int sendBufferSize = transportComponentCfg.sendBufferSize * 1024 * 1024;
        final DispatcherService sendBufferService = new DispatcherService(sendBufferSize);
        serviceContainer.installService(TRANSPORT_SEND_BUFFER, sendBufferService)
            .dependency(AGENT_RUNNER_SERVICE, sendBufferService.getAgentRunnerInjector())
            .done();

        final TransportService transportService = new TransportService();
        serviceContainer.installService(TRANSPORT, transportService)
            .dependency(TRANSPORT_SEND_BUFFER, transportService.getSendBufferInjector())
            .dependency(AGENT_RUNNER_SERVICE, transportService.getAgentRunnerInjector())
            .done();

        bindClientApi(serviceContainer, transportComponentCfg);
    }

    protected void bindClientApi(ServiceContainer serviceContainer, TransportComponentCfg transportComponentCfg)
    {
        final SocketBindingCfg socketBindingCfg = transportComponentCfg.clientApi;

        bindSocket(serviceContainer, transportComponentCfg, socketBindingCfg, CLIENT_API_SOCKET_BINDING_NAME);

    }

    protected void bindSocket(
            final ServiceContainer serviceContainer,
            final TransportComponentCfg transportComponentCfg,
            final SocketBindingCfg socketBindingCfg,
            final String bindingName)
    {
        final int port = socketBindingCfg.port;

        String hostname = socketBindingCfg.hostname;
        if(hostname == null || hostname.isEmpty())
        {
            hostname = transportComponentCfg.hostname;
        }

        final InetSocketAddress bindAddr = new InetSocketAddress(hostname, port);

        int receiveBufferSize = socketBindingCfg.receiveBufferSize * 1024 * 1024;
        if(receiveBufferSize == -1)
        {
            receiveBufferSize = transportComponentCfg.defaultReceiveBufferSize;
        }

        final DispatcherService receiveBufferService = new DispatcherService(receiveBufferSize);
        final ServiceName<Dispatcher> receiveBufferName = serviceContainer.installService(serverSocketBindingReceiveBufferName(bindingName), receiveBufferService)
            .dependency(AGENT_RUNNER_SERVICE, receiveBufferService.getAgentRunnerInjector())
            .done();

        final ServerSocketBindingService socketBindingService = new ServerSocketBindingService(bindingName, bindAddr);
        serviceContainer.installService(serverSocketBindingServiceName(bindingName), socketBindingService)
            .dependency(TRANSPORT, socketBindingService.getTransportInjector())
            .dependency(receiveBufferName, socketBindingService.getReceiveBufferInjector())
            .done();
    }
}
