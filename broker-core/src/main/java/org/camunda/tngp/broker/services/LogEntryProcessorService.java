package org.camunda.tngp.broker.services;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.util.buffer.BufferReader;

public abstract class LogEntryProcessorService<T extends BufferReader> implements Service<LogEntryProcessor<T>>
{
    public static final int READ_BUFFER_SIZE = 1024 * 1024;

    protected final T bufferReader;

    protected final Injector<Log> logInjector = new Injector<>();

    protected LogEntryProcessor<T> logEntryProcessor;


    public LogEntryProcessorService(T bufferReader)
    {
        this.bufferReader = bufferReader;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final LogReader logReader = new LogReaderImpl(logInjector.getValue(), READ_BUFFER_SIZE);

        logReader.setPosition(recoverLastReadPosition());

        final LogEntryHandler<T> entryHandler = createEntryHandler();

        logEntryProcessor = new LogEntryProcessor<>(logReader, bufferReader, entryHandler);
    }

    protected abstract LogEntryHandler<T> createEntryHandler();

    protected abstract int recoverLastReadPosition();

    @Override
    public LogEntryProcessor<T> get()
    {
        return logEntryProcessor;
    }

    @Override
    public void stop()
    {
    }

    public Injector<Log> getLogInjector()
    {
        return logInjector;
    }

}
