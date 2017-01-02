package org.camunda.tngp.broker.services;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.DispatcherBuilder;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class DispatcherService implements Service<Dispatcher>
{
    protected final Injector<AgentRunnerServices> agentRunnerInjector = new Injector<>();
    protected final Injector<Counters> countersInjector = new Injector<>();

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
    public void start(ServiceStartContext ctx)
    {
        final Counters counters = countersInjector.getValue();

        dispatcher = dispatcherBuilder
                .name(ctx.getName())
                .conductorExternallyManaged()
                .countersManager(counters.getCountersManager())
                .countersBuffer(counters.getCountersBuffer())
                .build();

        dispatcherConductor = dispatcherBuilder.getConductorAgent();

        agentRunnerInjector.getValue().conductorAgentRunnerSerive().run(dispatcherConductor);
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        final CompletableFuture<Void> closeFuture = dispatcher.closeAsync().thenAccept((v) ->
        {
            agentRunnerInjector.getValue().conductorAgentRunnerSerive().remove(dispatcherConductor);
        });

        ctx.async(closeFuture);
    }

    @Override
    public Dispatcher get()
    {
        return dispatcher;
    }

    public Injector<AgentRunnerServices> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }

    public Injector<Counters> getCountersManagerInjector()
    {
        return countersInjector;
    }
}
