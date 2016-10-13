package org.camunda.tngp.broker.log;

import java.util.concurrent.ExecutionException;

import org.camunda.tngp.log.FsLogBuilder;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.impl.agent.LogAgentContext;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

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
    public void start(ServiceContext serviceContext)
    {
        try
        {
            log = logBuilder
                    .logAgentContext(logAgentContext.getValue())
                    .build()
                    .get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop()
    {
        log.close();
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

