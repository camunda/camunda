package io.zeebe.broker.logstreams;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

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
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public LogStream get()
    {
        return logStream;
    }

}

