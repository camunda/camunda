package org.camunda.tngp.broker.log;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

public class LogConsumer
{

    protected LogEntryProcessor<LogEntryHeaderReader> logEntryProcessor;
    protected LogReader logReader;
    protected DeferredResponsePool apiResponsePool;

    protected ResponseControl defaultResponseControl;
    protected ApiResponseControl apiResponseControl;

    protected Int2ObjectHashMap<LogEntryTypeHandler<?>> entryHandlers = new Int2ObjectHashMap<>();
    protected Templates templates;
    protected List<IndexWriter> indexWriters = new ArrayList<>();

    public LogConsumer(LogReader logReader, Templates templates)
    {
        this(logReader, null, templates);
    }

    public LogConsumer(LogReader logReader, DeferredResponsePool apiResponsePool, Templates templates)
    {
        this.logEntryProcessor = new LogEntryProcessor<>(logReader, new LogEntryHeaderReader(), new LogConsumerTaskHandler());
        this.logReader = logReader;
        this.apiResponsePool = apiResponsePool;
        this.templates = templates;

        this.defaultResponseControl = new DefaultResponseControl();
        this.apiResponseControl = new ApiResponseControl();
    }

    public class LogConsumerTaskHandler implements LogEntryHandler<LogEntryHeaderReader>
    {

        @Override
        public int handle(long position, LogEntryHeaderReader reader)
        {
            System.out.println("Handler " + hashCode() + ": Handling event of type " + reader.templateId() + " at position " + position);
            final EventSource source = reader.source();
            final ResponseControl responseControl;

            final boolean handlesApiRequests = apiResponsePool != null;
            if (source == EventSource.API && handlesApiRequests)
            {
                // TODO: das removeFirst funktioniert so lange wie die ResponsePoolQueue von niemand
                //   anderem konsumiert wird (z.B. nicht mehr, wenn Requests von außen abgebrochen werden können)
                apiResponseControl.wrap(apiResponsePool.popDeferred());
                responseControl = apiResponseControl;
            }
            else
            {
                responseControl = defaultResponseControl;
            }


            doHandle(reader, responseControl);

            for (int i = 0; i < indexWriters.size(); i++)
            {
                final IndexWriter indexWriter = indexWriters.get(i);
                indexWriter.indexLogEntry(position, reader);
            }

            return 0;
        }
    }

    /**
     * Helper method for wildcard capture (see https://docs.oracle.com/javase/tutorial/java/generics/capture.html)
     */
    protected <S extends BufferReader> void doHandle(LogEntryHeaderReader headerReader, ResponseControl responseControl)
    {
        final Template<S> template = headerReader.template();
        final LogEntryTypeHandler<S> handler = getHandler(template);

        if (handler == null)
        {
            System.out.println("No log entry handler for template " + template.id() + ". Ignoring event.");
            return;
        }

        final S entryReader = templates.getReader(template);
        headerReader.readInto(entryReader);
        handler.handle(entryReader, responseControl);
    }

    protected <S extends BufferReader> LogEntryTypeHandler<S> getHandler(Template<S> template)
    {
        return (LogEntryTypeHandler<S>) entryHandlers.get(template.id());
    }

    public <S extends BufferReader> void addHandler(Template<S> template, LogEntryTypeHandler<S> typeHandler)
    {
        entryHandlers.put(template.id(), typeHandler);
    }

    public void addIndexWriter(IndexWriter indexWriter)
    {
        indexWriters.add(indexWriter);
    }

    public void doConsume()
    {
        logEntryProcessor.doWork(Integer.MAX_VALUE);
    }

    public void recoverLastSafepointPosition()
    {
//        final HashIndexManager<?>[] indexManagers = logConsumer.getIndexManagers();

        // TODO: maximum of last checkpointed position of all indexes and the position we recover based on the previous log pointers
        final long lastConfirmedPosition = 0L;
        logReader.setPosition(lastConfirmedPosition);

        // TODO: rewind indexes until lastConfirmedPosition
    }

    public void writeSafepoints()
    {
        // TODO: implement
    }

    public static class DefaultResponseControl implements ResponseControl
    {

        @Override
        public void accept(BufferWriter responseWriter)
        {
            // do nothing
        }

        @Override
        public void reject(ErrorWriter errorWriter)
        {
            // do nothing; could be used for detection of inconsistencies
        }
    }

    public static class ApiResponseControl implements ResponseControl
    {
        protected DeferredResponse response;

        public void wrap(DeferredResponse response)
        {
            this.response = response;
        }

        @Override
        public void accept(BufferWriter responseWriter)
        {
            writeResponseAndCommit(responseWriter);
        }

        @Override
        public void reject(ErrorWriter errorWriter)
        {
            writeResponseAndCommit(errorWriter);
        }

        protected void writeResponseAndCommit(BufferWriter writer)
        {
            final boolean success = response.allocateAndWrite(writer);
            // TODO: do something if it could not be allocated

            response.commit();
        }
    }


}
