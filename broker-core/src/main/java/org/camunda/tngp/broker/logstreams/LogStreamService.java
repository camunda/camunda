package org.camunda.tngp.broker.logstreams;

import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class LogStreamService implements Service<LogStream>
{
    protected final LogStream logStream;

    public LogStreamService(LogStream logStream)
    {
        this.logStream = logStream;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        serviceContext.async(logStream.openAsync());
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(logStream.closeAsync());
    }

    @Override
    public LogStream get()
    {
        return logStream;
    }

}

