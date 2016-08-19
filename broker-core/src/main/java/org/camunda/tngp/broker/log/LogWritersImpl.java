package org.camunda.tngp.broker.log;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;

public class LogWritersImpl implements LogWriters
{
    protected ResourceContext thisLogContext;
    protected ResourceContextProvider<?> resourceContextProvider;

    public LogWritersImpl(ResourceContext thisLogContext, ResourceContextProvider<?> resourceContextProvider)
    {
        this.thisLogContext = thisLogContext;
        this.resourceContextProvider = resourceContextProvider;
    }

    @Override
    public void writeToCurrentLog(LogEntryWriter<?, ?> entryWriter)
    {
        writeToResourceContext(thisLogContext, entryWriter);
    }

    @Override
    public void writeToLog(int logId, LogEntryWriter<?, ?> entryWriter)
    {
        final ResourceContext resourceContext;
        if (resourceContextProvider != null)
        {
            resourceContext = resourceContextProvider.getContextForResource(logId);
        }
        else
        {
            resourceContext = null;
        }


        writeToResourceContext(resourceContext, entryWriter);
    }

    @Override
    public void writeToAllLogs(LogEntryWriter<?, ?> entryWriter)
    {
        if (resourceContextProvider != null)
        {
            final ResourceContext[] contexts = resourceContextProvider.getContexts();
            for (int i = 0; i < contexts.length; i++)
            {
                writeToResourceContext(contexts[i], entryWriter);
            }
        }

        if (thisLogContext != null)
        {
            writeToResourceContext(thisLogContext, entryWriter);
        }

    }

    protected void writeToResourceContext(ResourceContext resourceContext, LogEntryWriter<?, ?> entryWriter)
    {

        if (resourceContext == null)
        {
            throw new RuntimeException("Cannot write log entry: No resource context provided");
        }

        resourceContext.getLogWriter().write(entryWriter);
    }

}
