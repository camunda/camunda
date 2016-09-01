package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.log.Log;

public class WfRepositoryContext implements ResourceContext
{
    protected final int id;
    protected final String name;

    protected LogWriter logWriter;

    protected LogConsumer logConsumer;

    protected Log log;

    public WfRepositoryContext(int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    @Override
    public int getResourceId()
    {
        return id;
    }

    @Override
    public String getResourceName()
    {
        return name;
    }

    public LogWriter getLogWriter()
    {
        return logWriter;
    }

    public void setLogWriter(LogWriter logWriter)
    {
        this.logWriter = logWriter;
    }

    public void setLogConsumer(LogConsumer logConsumer)
    {
        this.logConsumer = logConsumer;
    }
    public LogConsumer getLogConsumer()
    {
        return logConsumer;
    }

    public Log getLog()
    {
        return log;
    }

    public void setLog(Log log)
    {
        this.log = log;
    }

}
