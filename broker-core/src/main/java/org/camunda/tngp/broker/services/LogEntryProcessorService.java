package org.camunda.tngp.broker.services;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.logstreams.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamReader;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.buffer.BufferReader;

public abstract class LogEntryProcessorService<T extends BufferReader> implements Service<LogEntryProcessor<T>>
{
    protected final T bufferReader;

    protected final Injector<LogStream> logInjector = new Injector<>();

    protected LogEntryProcessor<T> logEntryProcessor;


    public LogEntryProcessorService(T bufferReader)
    {
        this.bufferReader = bufferReader;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final LogStreamReader logReader = new BufferedLogStreamReader(logInjector.getValue());

        serviceContext.run(() ->
        {
            logReader.seek(recoverLastReadPosition());

            final LogEntryHandler<T> entryHandler = createEntryHandler();

            logEntryProcessor = new LogEntryProcessor<>(logReader, bufferReader, entryHandler);
        });
    }

    protected abstract LogEntryHandler<T> createEntryHandler();

    protected abstract int recoverLastReadPosition();

    @Override
    public LogEntryProcessor<T> get()
    {
        return logEntryProcessor;
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
    }

    public Injector<LogStream> getLogInjector()
    {
        return logInjector;
    }

}
