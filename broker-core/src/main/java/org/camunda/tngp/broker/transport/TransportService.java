package org.camunda.tngp.broker.transport;

import org.camunda.tngp.broker.system.threads.AgentRunnerService;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.impl.agent.Receiver;
import org.camunda.tngp.transport.impl.agent.Sender;
import org.camunda.tngp.transport.impl.agent.TransportConductor;

public class TransportService implements Service<Transport>
{
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final Injector<AgentRunnerService> agentRunnerInjector = new Injector<>();

    protected Transport transport;
    protected TransportConductor transportConductor;
    protected Receiver receiver;
    protected Sender sender;

    @Override
    public void start(ServiceContext serviceContext)
    {
        final TransportBuilder transportBuilder = Transports.createTransport(serviceContext.getName());

        transport = transportBuilder
                .sendBuffer(sendBufferInjector.getValue())
                .agentsExternallyManaged()
                .build();

        transportConductor = transportBuilder.getTransportConductor();
        receiver = transportBuilder.getReceiver();
        sender = transportBuilder.getSender();

        final AgentRunnerService agentRunnerService = agentRunnerInjector.getValue();
        agentRunnerService.runConductorAgent(transportConductor);
        agentRunnerService.runNetworkingAgent(receiver);
        agentRunnerService.runNetworkingAgent(sender);
    }

    @Override
    public void stop()
    {
        final AgentRunnerService agentRunnerService = agentRunnerInjector.getValue();
        agentRunnerService.removeConductorAgent(transportConductor);
        agentRunnerService.removeNetworkingAgent(sender);
        agentRunnerService.removeNetworkingAgent(receiver);
    }

    @Override
    public Transport get()
    {
        return transport;
    }

    public Injector<AgentRunnerService> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }
}
