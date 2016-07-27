package org.camunda.tngp.broker.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.hashindex.HashIndexDescriptor.BLOCK_DATA_OFFSET;
import static org.camunda.tngp.hashindex.HashIndexDescriptor.framedRecordLength;
import static uk.co.real_logic.agrona.BitUtil.SIZE_OF_LONG;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.idx.IndexWriter;
import org.camunda.tngp.broker.idx.LogEntryTracker;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.DispatcherBuilder;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogAgentContext;
import org.camunda.tngp.log.LogBuilder;
import org.camunda.tngp.log.LogContext;
import org.camunda.tngp.log.LogReaderImpl;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.Logs;
import org.camunda.tngp.log.appender.LogAppendHandler;
import org.camunda.tngp.log.appender.LogAppender;
import org.camunda.tngp.log.conductor.LogConductor;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.After;
import org.junit.Test;

public class LogIndexConsistencyTest
{

    protected Log log;
    protected LogAppender logAppender;
    protected LogConductor logConductor;
    protected DispatcherConductor dispatcherConductor;

    protected Long2LongHashIndex index;
    protected FileChannelIndexStore indexStore;

    protected IndexWriter<TaskInstanceReader> indexWriter;
    protected TaskInstanceWriter taskWriter = new TaskInstanceWriter();

    protected Dispatcher dispatcher;

    protected void createLogAndIndex(LogBuilder logBuilder) throws Exception
    {
        final int indexSize = 16;
        final int blockLength = BLOCK_DATA_OFFSET  + 3 * framedRecordLength(SIZE_OF_LONG, SIZE_OF_LONG); // 3 entries fit into a block

        indexStore = FileChannelIndexStore.tempFileIndexStore();
        index = new Long2LongHashIndex(indexStore, indexSize, blockLength);

        final DispatcherBuilder dispatcherBuilder = Dispatchers.create("bar");
        dispatcher = dispatcherBuilder.conductorExternallyManaged().build();

        final LogAgentContext logAgentContext = new LogAgentContext();
        logAgentContext.setWriteBufferExternallyManaged(true);
        logAgentContext.setWriteBuffer(dispatcher);
        logConductor = new LogConductor(logAgentContext);
        logAppender = new LogAppender(logAgentContext);

        log = logBuilder
                .deleteOnClose(true)
                .writeBuffer(dispatcher)
                .logAgentContext(logAgentContext)
                .build();

        final CompletableFuture<Log> future = log.startAsync();
        logConductor.doWork(); // opens the log as requested above
        future.join();
        logAppender.doWork();  // requests conductor to create first segment
        logConductor.doWork(); // creates first segment
        logAppender.doWork();  // append handler has to process first segment before it can write to it

        dispatcherConductor = dispatcherBuilder.getConductorAgent();

        indexWriter = new IndexWriter<>(
                new LogReaderImpl(log),
                log.getWriteBuffer().openSubscription(),
                log.getId(),
                new TaskInstanceReader(),
                new DummyIndexTracker(index),
                new HashIndexManager<?>[0]);

    }

    @After
    public void tearDown() throws Exception
    {
        indexStore.close();

        final CompletableFuture<Log> future = log.closeAsync(); // tells log conductor to close log
        logConductor.doWork(); // closes log and tells appender to remove append handler
        logAppender.doWork();
        future.join();

        final CompletableFuture<Void> conductorCloseFuture = logConductor.close();
        logConductor.doWork();
        dispatcherConductor.doWork();
        conductorCloseFuture.join();

        final CompletableFuture<Void> dispatcherFuture = dispatcher.closeAsync();
        dispatcherConductor.doWork(); // closes subscriptions
        dispatcherFuture.join();

    }

    @Test
    public void shouldNotResolveIndexKeyWhenDirty() throws Exception
    {
        // given
        createLogAndIndex(Logs.createLog("foo", 0));

        final LogWriter writer = new LogWriter(log, indexWriter);
        taskWriter
            .id(42L)
            .state(TaskInstanceState.NEW);

        dispatcherConductor.doWork(); // lets the dispatcher know that there is space to write to

        // when
        writer.write(taskWriter);

        // then the entry should be marked as dirty in the index
        long indexEntry = index.get(42L, -1L, -2L);
        assertThat(indexEntry).isEqualTo(-2L);

        // when the log entry gets committed
        logAppender.doWork();
        // and indexed
        indexWriter.indexLogEntries();

        // then the entry appears index
        indexEntry = index.get(42L, -1L, -2L);
        assertThat(indexEntry).isGreaterThanOrEqualTo(0L);
    }

    @Test
    public void shouldNotResolveIndexKeyWhenIndexWriterHasNotCaughtUp() throws Exception
    {
        // given
        createLogAndIndex(Logs.createLog("foo", 0));

        final LogWriter writer = new LogWriter(log, indexWriter);
        taskWriter
            .id(42L)
            .state(TaskInstanceState.NEW);

        dispatcherConductor.doWork(); // lets the dispatcher know that there is space to write to
        writer.write(taskWriter);

        logAppender.doWork(); // the log entry gets committed

        // when
        long indexEntry = index.get(42L, -1L, -2L);

        // then the entry is still dirty since the index writer has not processed the log entry yet
        assertThat(indexEntry).isEqualTo(-2L);

        // when the log entry gets indexed
        indexWriter.indexLogEntries();

        // then the entry appears in the index
        indexEntry = index.get(42L, -1L, -2L);
        assertThat(indexEntry).isGreaterThanOrEqualTo(0L);
    }

    @Test
    public void shouldResolveIndexKeyWhenLogAppendFails() throws Exception
    {
        // given
        createLogAndIndex(new TestLogBuilder("foo", 0));

        final LogWriter writer = new LogWriter(log, indexWriter);
        taskWriter
            .id(42L)
            .state(TaskInstanceState.NEW);

        dispatcherConductor.doWork(); // lets the dispatcher know that there is space to write to
        writer.write(taskWriter);

        logAppender.doWork();

        // when
        indexWriter.indexLogEntries();

        // then
        assertThat(index.get(42L, -1L, -2L)).isEqualTo(-1L);

    }

    public static class DummyIndexTracker implements LogEntryTracker<TaskInstanceReader>
    {

        protected Long2LongHashIndex index;

        public DummyIndexTracker(Long2LongHashIndex index)
        {
            this.index = index;
        }

        @Override
        public void onLogEntryStaged(TaskInstanceReader logEntry)
        {
            index.markDirty(logEntry.id());
        }

        @Override
        public void onLogEntryFailed(TaskInstanceReader logEntry)
        {
            index.resolveDirty(logEntry.id());
        }

        @Override
        public void onLogEntryCommit(TaskInstanceReader logEntry, long position)
        {
            index.put(logEntry.id(), position);
            index.resolveDirty(logEntry.id());
        }
    }

    public static class TestLogBuilder extends LogBuilder
    {
        public TestLogBuilder(String name, int id)
        {
            super(name, id);
        }

        @Override
        protected LogContext newLogContext()
        {
            final LogContext logContext = new LogContext(name, id);
            logContext.setLogAppendHandler(new AlwaysFailLogAppendHandler());
            return logContext;
        }
    }

    public static class AlwaysFailLogAppendHandler extends LogAppendHandler
    {
        @Override
        protected int append(BlockPeek blockPeek, LogAppender logAppender)
        {
            blockPeek.markFailed();
            return 0;
        }
    }
}
