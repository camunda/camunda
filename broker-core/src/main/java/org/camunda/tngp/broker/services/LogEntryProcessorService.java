package org.camunda.tngp.broker.services;

import org.camunda.tngp.broker.services.LogEntryProcessorService.LogEntryProcessor;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.util.buffer.BufferReader;

public abstract class LogEntryProcessorService<T extends BufferReader> implements Service<LogEntryProcessor<T>>
{
    protected static final int READ_BUFFER_SIZE = 1024 * 1024;

    protected final T bufferReader;

    protected final Injector<Log> logInjector = new Injector<>();

    protected LogEntryProcessor<T> logEntryProcessor;

    protected abstract int recoverLastReadPosition();

    public LogEntryProcessorService(T bufferReader)
    {
        this.bufferReader = bufferReader;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final LogReader logReader = new LogReader(logInjector.getValue(), READ_BUFFER_SIZE);

        logReader.setPosition(recoverLastReadPosition());

        final LogEntryHandler<T> entryHandler = createEntryHandler();

        logEntryProcessor = new LogEntryProcessor<>(logReader, bufferReader, entryHandler);
    }

    protected abstract LogEntryHandler<T> createEntryHandler();

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

    public static class LogEntryProcessor<T extends BufferReader>
    {
        protected LogReader logReader;
        protected T bufferReader;
        protected LogEntryHandler<T> entryHandler;

        public LogEntryProcessor(LogReader logReader, T bufferReader, LogEntryHandler<T> entryHandler)
        {
            this.bufferReader = bufferReader;
            this.logReader = logReader;
            this.entryHandler = entryHandler;
        }

        public int doWork()
        {
            int workCount = 0;

            final boolean hasNext = logReader.read(bufferReader);
            if (hasNext)
            {
                entryHandler.handle(bufferReader);
                workCount++;
            }

            return workCount;
        }
    }

    public interface LogEntryHandler<T extends BufferReader>
    {
        void handle(T reader);
    }

}
