package org.camunda.tngp.broker.log;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.log.FsLogBuilder;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.impl.agent.LogAgentContext;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class LogService implements Service<Log>
{
    protected final Injector<LogAgentContext> logAgentContext = new Injector<>();

    protected final FsLogBuilder logBuilder;

    protected Log log;

    public LogService(FsLogBuilder logBuilder)
    {
        this.logBuilder = logBuilder;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final CompletableFuture<Void> startFuture = ((CompletableFuture<Log>) logBuilder
            .logAgentContext(logAgentContext.getValue())
            .build())
            .thenAccept((log) -> this.log = log);

        serviceContext.async(startFuture);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async((CompletableFuture<?>) log.closeAsync());
    }

    @Override
    public Log get()
    {
        return log;
    }

    public Injector<LogAgentContext> getLogAgentContext()
    {
        return logAgentContext;
    }

}

