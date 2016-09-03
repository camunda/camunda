package org.camunda.tngp.broker.log;

import java.util.ArrayList;
import java.util.List;

import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.ResponseControl.ApiResponseControl;
import org.camunda.tngp.broker.log.ResponseControl.DefaultResponseControl;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.broker.log.idx.IndexWriterTracker;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.util.buffer.BufferReader;


public class LogConsumer
{
    public static final String DEBUG_LOGGING_ENABLED_PROP_NAME = "camunda.debug.logging.enabled";
    public static final boolean DEBUG_LOGGING_ENABLED = Boolean.getBoolean(DEBUG_LOGGING_ENABLED_PROP_NAME);

    // TODO: magic number that becomes obsolete once we have symbolic log positions. Then it can be replaced with 0.
    public static final long LOG_INITIAL_POSITION = 264L;

    protected int logId;
    protected LogEntryProcessor<LogEntryHeaderReader> logEntryProcessor;
    protected LogEntryProcessor<LogEntryHeaderReader> recoveryProcessor;
    protected LogConsumerTaskHandler logEntryHandler;
    protected LogReader logReader;
    protected DeferredResponsePool apiResponsePool;

    protected ResponseControl defaultResponseControl;
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
        this.templates = templates;

        this.defaultResponseControl = new DefaultResponseControl();
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

    public void doConsume()
    {
        logEntryProcessor.doWork(Integer.MAX_VALUE);
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
            targetLogReader.setPosition(LOG_INITIAL_POSITION);
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
            fastForwardIndexesUntil(lastConsumedPosition);

            logReader.setPosition(lastConsumedPosition);

            // this sets the log reader's position to the next event;
            // it is a hack that can be resolved
            // when the log is based on symbolic positions (then it is just lastConsumedPosition + 1)
            if (logReader.hasNext())
            {
                logReader.read(new LogEntryHeaderReader());
            }
        }
        else
        {
            logReader.setPosition(LOG_INITIAL_POSITION);
        }
    }

    /**
     * Replays all log entries from the consumer's current position until <code>fastForwardStopPosition</code>.
     */
    public void fastForwardUntil(long fastForwardStopPosition)
    {
        fastForwardUntil(logReader.position(), fastForwardStopPosition, false);
    }

    /**
     * @param position is inclusive
     */
    protected void fastForwardIndexesUntil(long position)
    {
        long minimalIndexPosition = Long.MAX_VALUE;

        for (int i = 0; i < indexWriters.size(); i++)
        {
            final long lastIndexedPosition = indexWriters.get(i).getLastIndexedPosition();
            if (lastIndexedPosition >= 0 && lastIndexedPosition < minimalIndexPosition)
            {
                minimalIndexPosition  = lastIndexedPosition;
            }
        }

        fastForwardUntil(minimalIndexPosition, position, true);
    }

    protected void fastForwardUntil(long fromPosition, long toPosition, boolean indexOnly)
    {
        logEntryHandler.indexOnly = indexOnly;

        logReader.setPosition(fromPosition);
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

    public class DecoratingLogWriters implements LogWriters
    {
        protected LogWriters logWriters;

        protected long entryPosition;

        public DecoratingLogWriters(LogWriters logWriters)
        {
            this.logWriters = logWriters;
        }

        public void inContextOfEntry(long entryPosition)
        {
            this.entryPosition = entryPosition;
        }

        @Override
        public void writeToCurrentLog(LogEntryWriter<?, ?> entryWriter)
        {
            decorateEventInContext(entryWriter);
            logWriters.writeToCurrentLog(entryWriter);
        }

        @Override
        public void writeToLog(int targetLogId, LogEntryWriter<?, ?> entryWriter)
        {
            decorateEventInContext(entryWriter);
            logWriters.writeToLog(targetLogId, entryWriter);
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
            logWriters.inContextOfEntry(position);
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

