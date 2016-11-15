package org.camunda.tngp.broker.log;

import org.camunda.tngp.logstreams.FsLogStreamBuilder;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class LogService implements Service<LogStream>
{
    protected final FsLogStreamBuilder logBuilder;

    protected LogStream log;

    public LogService(FsLogStreamBuilder logBuilder)
    {
        this.logBuilder = logBuilder;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        serviceContext.run(() ->
        {
            log = logBuilder.build();
            log.open();
        });

    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(log.closeAsync());
    }

    @Override
    public LogStream get()
    {
        return log;
    }

}

