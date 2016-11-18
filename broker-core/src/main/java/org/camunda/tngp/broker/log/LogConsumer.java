package org.camunda.tngp.broker.log;

import static org.camunda.tngp.broker.log.ResponseControl.NOOP_RESPONSE_CONTROL;

import java.util.ArrayList;
import java.util.List;

import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.ResponseControl.ApiResponseControl;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.broker.log.idx.IndexWriterTracker;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.util.buffer.BufferReader;


public class LogConsumer
{
    public static final String DEBUG_LOGGING_ENABLED_PROP_NAME = "camunda.debug.logging.enabled";
    public static final boolean DEBUG_LOGGING_ENABLED = Boolean.getBoolean(DEBUG_LOGGING_ENABLED_PROP_NAME);

    protected int logId;
    protected LogEntryProcessor<LogEntryHeaderReader> logEntryProcessor;
    protected LogEntryProcessor<LogEntryHeaderReader> recoveryProcessor;
    protected LogConsumerTaskHandler logEntryHandler;
    protected LogReader logReader;
    protected DeferredResponsePool apiResponsePool;
    protected boolean handlesApiRequests;

    protected ApiResponseControl apiResponseControl;

    protected Int2ObjectHashMap<LogEntryTypeHandler<?>> entryHandlers = new Int2ObjectHashMap<>();
    protected Templates templates;
    protected List<IndexWriterTracker> indexWriters = new ArrayList<>();
    protected DecoratingLogWriters logWriters;

    protected LastProcessedEventFinder finder;

    public LogConsumer(
            int logId,
            LogReader logReader,
            Templates templates,
            LogWriters logWriters)
    {
        this(logId, logReader, null, templates, logWriters);
    }

    public LogConsumer(
            int logId,
            LogReader logReader,
            DeferredResponsePool apiResponsePool,
            Templates templates,
            LogWriters logWriters)
    {
        this.logId = logId;
        this.logEntryHandler = new LogConsumerTaskHandler();
        this.logEntryProcessor = new LogEntryProcessor<>(logReader, new LogEntryHeaderReader(), logEntryHandler);
        this.logReader = logReader;
        this.apiResponsePool = apiResponsePool;
        this.handlesApiRequests = apiResponsePool != null;
        this.templates = templates;

        this.apiResponseControl = new ApiResponseControl();

        this.logWriters = new DecoratingLogWriters(logWriters);

        this.finder = new LastProcessedEventFinder(logId);
        this.recoveryProcessor = new LogEntryProcessor<>(null, new LogEntryHeaderReader(), finder);
    }

    @SuppressWarnings("unchecked")
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
        indexWriters.add(new IndexWriterTracker(indexWriter));
    }

    public int doConsume()
    {
        return logEntryProcessor.doWork(Integer.MAX_VALUE);
    }

    protected long getLastIndexedPosition()
    {
        long lastIndexedPosition = -1L;
        for (int i = 0; i < indexWriters.size(); i++)
        {
            final long lastPositionForIndex = indexWriters.get(i).getLastIndexedPosition();
            lastIndexedPosition = Math.max(lastIndexedPosition, lastPositionForIndex);
        }

        return lastIndexedPosition;
    }

    /**
     * Determines the last log entry consumed by this LogConsumer based on the
     * log entry backpointers
     */
    protected long getLastConsumedPosition(List<LogReader> targetLogReaders)
    {
        long lastConsumedPosition = -1L;

        finder.sourceLogId = logId;

        for (int i = 0; i < targetLogReaders.size(); i++)
        {
            final LogReader targetLogReader = targetLogReaders.get(i);

            finder.lastProcessedEventPosition = -1L;

            targetLogReader.seekToFirstEntry();
            recoveryProcessor.setLogReader(targetLogReader);
            recoveryProcessor.doWork(Integer.MAX_VALUE);

            lastConsumedPosition = Math.max(lastConsumedPosition, finder.lastProcessedEventPosition);
        }

        return lastConsumedPosition;
    }

    /**
     * Restores this consumer's state based on the log contents.
     */
    public void recover(List<LogReader> targetLogReaders)
    {
        final long lastIndexedPosition = getLastIndexedPosition();

        final long lastProcessedPosition = getLastConsumedPosition(targetLogReaders);
        final long lastConsumedPosition = Math.max(lastProcessedPosition, lastIndexedPosition);

        if (lastConsumedPosition >= 0)
        {
            final long firstUnconsumedPosition = lastConsumedPosition + 1;

            fastForwardIndexesUntil(firstUnconsumedPosition);
            logReader.seek(firstUnconsumedPosition);

        }
        else
        {
            logReader.seekToFirstEntry();
        }
    }

    /**
     * Replays all log entries from the consumer's current position until <code>fastForwardStopPosition</code>.
     */
    public void fastForwardUntil(long fastForwardStopPosition)
    {
        fastForwardUntil(logReader.getPosition(), fastForwardStopPosition, false);
    }

    /**
     * @param position is exclusive
     */
    protected void fastForwardIndexesUntil(long position)
    {
        long minimalIndexPosition = Long.MAX_VALUE;

        for (int i = 0; i < indexWriters.size(); i++)
        {
            final long lastIndexedPosition = indexWriters.get(i).getLastIndexedPosition();

            if (lastIndexedPosition < minimalIndexPosition)
            {
                minimalIndexPosition  = lastIndexedPosition;
            }
        }

        fastForwardUntil(minimalIndexPosition + 1, position, true);
    }

    protected void fastForwardUntil(long fromPosition, long toPosition, boolean indexOnly)
    {
        if (fromPosition >= toPosition)
        {
            return;
        }

        logEntryHandler.indexOnly = indexOnly;

        logReader.seek(fromPosition);
        logEntryProcessor.doWorkUntil(toPosition);

        logEntryHandler.indexOnly = false;
    }

    public void writeSavepoints()
    {
        for (int i = 0; i < indexWriters.size(); i++)
        {
            indexWriters.get(i).writeCheckpoint();
        }
    }

    public void fastForwardToLastEvent()
    {
        logEntryProcessor.doWork(Integer.MAX_VALUE);
    }

    public class DecoratingLogWriters implements LogWriters
    {
        protected LogWriters logWriters;

        protected long entryPosition;

        public DecoratingLogWriters(LogWriters logWriters)
        {
            this.logWriters = logWriters;
        }

        public void position(long entryPosition)
        {
            this.entryPosition = entryPosition;
        }

        @Override
        public long writeToCurrentLog(LogEntryWriter<?, ?> entryWriter)
        {
            decorateEventInContext(entryWriter);
            return logWriters.writeToCurrentLog(entryWriter);
        }

        @Override
        public long writeToLog(int targetLogId, LogEntryWriter<?, ?> entryWriter)
        {
            decorateEventInContext(entryWriter);
            return logWriters.writeToLog(targetLogId, entryWriter);
        }

        @Override
        public void writeToAllLogs(LogEntryWriter<?, ?> entryWriter)
        {
            decorateEventInContext(entryWriter);
            logWriters.writeToAllLogs(entryWriter);
        }

        protected void decorateEventInContext(LogEntryWriter<?, ?> entryWriter)
        {
            entryWriter
                .source(EventSource.LOG)
                .sourceEventLogId(logId)
                .sourceEventPosition(entryPosition);
        }
    }

    public class LogConsumerTaskHandler implements LogEntryHandler<LogEntryHeaderReader>
    {

        protected boolean indexOnly = false;

        @Override
        public int handle(long position, LogEntryHeaderReader reader)
        {
            if (!indexOnly)
            {
                handleEvent(position, reader);
            }

            indexEvent(position, reader);

            return 0;
        }

        protected void handleEvent(long position, LogEntryHeaderReader reader)
        {
            final EventSource source = reader.source();
            final ResponseControl responseControl;

            if (handlesApiRequests && source == EventSource.API)
            {
                // TODO: das removeFirst funktioniert so lange wie die ResponsePoolQueue von niemand
                //   anderem konsumiert wird (z.B. nicht mehr, wenn Requests von außen abgebrochen werden können)
                final DeferredResponse popDeferred = apiResponsePool.popDeferred();
                apiResponseControl.wrap(popDeferred);
                responseControl = apiResponseControl;
            }
            else
            {
                responseControl = NOOP_RESPONSE_CONTROL;
            }


            doHandle(position, reader, responseControl);
        }

        protected void indexEvent(long position, LogEntryHeaderReader reader)
        {
            for (int i = 0; i < indexWriters.size(); i++)
            {
                final IndexWriterTracker indexWriter = indexWriters.get(i);
                if (position > indexWriter.getLastIndexedPosition())
                {
                    indexWriter.writeIndexAndTrack(position, reader);
                }
            }
        }

        /**
         * Helper method for wildcard capture (see https://docs.oracle.com/javase/tutorial/java/generics/capture.html)
         */
        protected <S extends BufferReader> void doHandle(long position, LogEntryHeaderReader headerReader, ResponseControl responseControl)
        {
            final Template<S> template = headerReader.template();
            final LogEntryTypeHandler<S> handler = getHandler(template);

            if (handler == null)
            {
                if (DEBUG_LOGGING_ENABLED)
                {
                    System.out.println("No log entry handler for template " + template.id() + ". Ignoring event.");
                }
                return;
            }

            final S entryReader = templates.getReader(template);
            headerReader.readInto(entryReader);
            logWriters.position(position);
            handler.handle(entryReader, responseControl, logWriters);
        }
    }

    public static class LastProcessedEventFinder implements LogEntryHandler<LogEntryHeaderReader>
    {

        protected long lastProcessedEventPosition;
        protected long sourceLogId;

        public LastProcessedEventFinder(long sourceLogId)
        {
            this.sourceLogId = sourceLogId;
        }

        @Override
        public int handle(long position, LogEntryHeaderReader reader)
        {
            final long sourcePosition = reader.sourceEventPosition();
            if (sourcePosition != MessageHeaderDecoder.sourceEventPositionNullValue() &&
                    sourcePosition > lastProcessedEventPosition &&
                    reader.sourceEventLogId() == sourceLogId)
            {
                lastProcessedEventPosition = sourcePosition;
            }

            return 0;
        }
    }

}

