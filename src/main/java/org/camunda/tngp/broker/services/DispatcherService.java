package org.camunda.tngp.broker.services;

import org.camunda.tngp.broker.system.threads.AgentRunnerService;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.DispatcherBuilder;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class DispatcherService implements Service<Dispatcher>
{
    protected final Injector<AgentRunnerService> agentRunnerInjector = new Injector<>();

    protected DispatcherBuilder dispatcherBuilder;
    protected Dispatcher dispatcher;
    protected DispatcherConductor dispatcherConductor;

    public DispatcherService(int bufferSize)
    {
        this(Dispatchers.create(null).bufferSize(bufferSize));
    }

    public DispatcherService(DispatcherBuilder builder)
    {
        this.dispatcherBuilder = builder;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        dispatcher = dispatcherBuilder
                .name(serviceContext.getName())
                .conductorExternallyManaged()
                .build();

        dispatcherConductor = dispatcherBuilder.getConductorAgent();

        agentRunnerInjector.getValue().runConductorAgent(dispatcherConductor);
    }

    @Override
    public void stop()
    {
        try
        {
            dispatcher.close();
        }
        finally
        {
            agentRunnerInjector.getValue().removeConductorAgent(dispatcherConductor);
        }
    }

    @Override
    public Dispatcher get()
    {
        return dispatcher;
    }

    public Injector<AgentRunnerService> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }
}
