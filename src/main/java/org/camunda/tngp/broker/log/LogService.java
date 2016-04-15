package org.camunda.tngp.broker.log;

import org.camunda.tngp.broker.servicecontainer.Injector;
import org.camunda.tngp.broker.servicecontainer.Service;
import org.camunda.tngp.broker.servicecontainer.ServiceContext;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogAgentContext;
import org.camunda.tngp.log.LogBuilder;

public class LogService implements Service<Log>
{
    protected final Injector<LogAgentContext> logAgentContext = new Injector<>();

    protected final LogBuilder logBuilder;

    protected Log log;

    public LogService(LogBuilder logBuilder)
    {
        this.logBuilder = logBuilder;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        log = logBuilder
                .logAgentContext(logAgentContext.getValue())
                .build();

        log.start();
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

