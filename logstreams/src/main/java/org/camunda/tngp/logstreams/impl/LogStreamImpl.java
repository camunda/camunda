package org.camunda.tngp.logstreams.impl;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicLongPosition;
import org.agrona.concurrent.status.CountersManager;
import org.agrona.concurrent.status.Position;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.PositionUtil;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamFailureListener;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.snapshot.TimeBasedSnapshotPolicy;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.util.agent.AgentRunnerService;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import static org.camunda.tngp.logstreams.log.LogStreamUtil.INVALID_ADDRESS;
import static org.camunda.tngp.logstreams.log.LogStreamUtil.getAddressForPosition;
import static org.camunda.tngp.util.EnsureUtil.ensureFalse;
import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;
import static org.camunda.tngp.util.buffer.BufferUtil.bufferAsString;
import static org.camunda.tngp.util.buffer.BufferUtil.cloneBuffer;

/**
 * Represents the implementation of the LogStream interface.
 */
public final class LogStreamImpl implements LogStream
{
    public static final String EXCEPTION_MSG_TRUNCATE_FAILED = "Truncation failed! Position %d was not found.";
    public static final String EXCEPTION_MSG_TRUNCATE_AND_LOG_STREAM_CTRL_IN_PARALLEL = "Can't truncate the log storage and have a log stream controller active at the same time.";
    public static final String EXCEPTION_MSG_TRUNCATE_COMMITTED_POSITION = "Can't truncate position which is already committed!";

    private static final int DEFAULT_INDEX_BLOCK_SIZE = 1024 * 1024 * 4;
    private static final int DEFAULT_READ_BLOCK_SIZE = 1024;

    protected volatile int term = 0;

    protected final DirectBuffer topicName;
    protected final int partitionId;
    protected final String name;

    protected final LogStorage logStorage;
    protected final LogBlockIndex blockIndex;
    protected final AgentRunnerService agentRunnerService;


    protected final LogBlockIndexController logBlockIndexController;

    protected LogStreamController logStreamController;
    protected AgentRunnerService writeAgentRunnerService;
    protected Dispatcher writeBuffer;
    protected final Position commitPosition = new AtomicLongPosition();

    private LogStreamImpl(final LogStreamBuilder logStreamBuilder)
    {
        final DirectBuffer topicName = logStreamBuilder.getTopicName();
        if (topicName.capacity() > MAX_TOPIC_NAME_LENGTH)
        {
            throw new RuntimeException(String.format("Topic name exceeds max length (%d > %d bytes)", topicName.capacity(), MAX_TOPIC_NAME_LENGTH));
        }

        this.topicName = cloneBuffer(topicName);
        this.partitionId = logStreamBuilder.getPartitionId();
        this.name = logStreamBuilder.getLogName();
        this.logStorage = logStreamBuilder.getLogStorage();
        this.blockIndex = logStreamBuilder.getBlockIndex();
        this.agentRunnerService = logStreamBuilder.getAgentRunnerService();

        commitPosition.setOrdered(INVALID_ADDRESS);
        this.logBlockIndexController = new LogBlockIndexController(logStreamBuilder, commitPosition);

        if (!logStreamBuilder.isLogStreamControllerDisabled())
        {
            this.logStreamController = new LogStreamController(logStreamBuilder);
            writeAgentRunnerService = logStreamBuilder.getWriteBufferAgentRunnerService();
            this.writeBuffer = logStreamBuilder.getWriteBuffer();
        }
    }


    public LogBlockIndexController getLogBlockIndexController()
    {
        return logBlockIndexController;
    }

    public LogStreamController getLogStreamController()
    {
        return logStreamController;
    }

    @Override
    public DirectBuffer getTopicName()
    {
        return topicName;
    }

    @Override
    public int getPartitionId()
    {
        return partitionId;
    }

    @Override
    public String getLogName()
    {
        return name;
    }

    @Override
    public void open()
    {
        try
        {
            openAsync().get();
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Void> openAsync()
    {
        final CompletableFuture<Void> logBlockIndexControllerFuture = logBlockIndexController.openAsync();
        final CompletableFuture<Void> completableFuture;
        if (logStreamController != null)
        {
            completableFuture = CompletableFuture.allOf(logBlockIndexControllerFuture,
                logStreamController.openAsync());
        }
        else
        {
            completableFuture = logBlockIndexControllerFuture;
        }

        return completableFuture;
    }

    @Override
    public void close()
    {
        try
        {
            closeAsync().get();
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Void> closeAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        final BiConsumer<Void, Throwable> closeLogStorage = (n, e) ->
        {
            logStorage.close();
            future.complete(null);
        };

        //write buffer close
        final CompletableFuture<Void> logBlockIndexControllerClosingFuture =
            logBlockIndexController.closeAsync();
        if (logStreamController != null)
        {
            // can't close dispatcher since has no method to reopen
            // TODO camunda-tngp/dispatcher#12
            CompletableFuture.allOf(logBlockIndexControllerClosingFuture,
                logStreamController.closeAsync())
                .whenComplete(closeLogStorage);
        }
        else
        {
            logBlockIndexControllerClosingFuture.whenComplete(closeLogStorage);
        }

        return future;
    }

    @Override
    public long getCurrentAppenderPosition()
    {
        return logStreamController == null ? 0 : logStreamController.getCurrentAppenderPosition();
    }

    @Override
    public long getCommitPosition()
    {
        return commitPosition.get();
    }

    @Override
    public void setCommitPosition(long commitPosition)
    {
        this.commitPosition.setOrdered(commitPosition);
    }

    @Override
    public int getTerm()
    {
        return term;
    }

    @Override
    public void setTerm(int term)
    {
        this.term = term;
    }

    @Override
    public void registerFailureListener(LogStreamFailureListener listener)
    {
        if (logStreamController != null)
        {
            logStreamController.registerFailureListener(listener);
        }
    }

    @Override
    public void removeFailureListener(LogStreamFailureListener listener)
    {
        if (logStreamController != null)
        {
            logStreamController.removeFailureListener(listener);
        }
    }

    @Override
    public LogStorage getLogStorage()
    {
        return logStorage;
    }

    @Override
    public LogBlockIndex getLogBlockIndex()
    {
        return blockIndex;
    }

    @Override
    public int getIndexBlockSize()
    {
        return logBlockIndexController.getIndexBlockSize();
    }

    @Override
    public CompletableFuture<Void> closeLogStreamController()
    {
        final CompletableFuture<Void> completableFuture;
        if (logStreamController != null)
        {
            completableFuture = new CompletableFuture<>();
//            Dispatcher should be closed, but this will fail the integration tests
//            see TODO camunda-tngp/logstreams#60
//            writeBuffer.closeAsync()
//                       .thenCompose((v) ->
            logStreamController.closeAsync()
                .handle((v, ex) ->
                {
                    writeBuffer = null;
                    logStreamController = null;
                    return ex != null
                        ? completableFuture.completeExceptionally(ex)
                        : completableFuture.complete(null);
                });
        }
        else
        {
            completableFuture = CompletableFuture.completedFuture(null);
        }
        return completableFuture;
    }

    @Override
    public CompletableFuture<Void> openLogStreamController()
    {

        return openLogStreamController(writeAgentRunnerService, DEFAULT_MAX_APPEND_BLOCK_SIZE);
    }

    @Override
    public CompletableFuture<Void> openLogStreamController(AgentRunnerService writeBufferAgentRunnerService)
    {
        return openLogStreamController(writeBufferAgentRunnerService, DEFAULT_MAX_APPEND_BLOCK_SIZE);
    }

    @Override
    public CompletableFuture<Void> openLogStreamController(AgentRunnerService writeBufferAgentRunnerService,
                                                           int maxAppendBlockSize)
    {
        final LogStreamBuilder logStreamBuilder = new LogStreamBuilder(topicName, partitionId)
            .logStorage(logStorage)
            .logBlockIndex(blockIndex)
            .agentRunnerService(agentRunnerService)
            .writeBufferAgentRunnerService(writeBufferAgentRunnerService)
            .maxAppendBlockSize(maxAppendBlockSize);

        this.logStreamController = new LogStreamController(logStreamBuilder);
        this.writeBuffer = logStreamBuilder.getWriteBuffer();
        return logStreamController.openAsync();
    }

    @Override
    public Dispatcher getWriteBuffer()
    {
        return logStreamController == null ? null : logStreamController.getWriteBuffer();
    }

    @Override
    public void truncate(long position)
    {
        if (logStreamController != null)
        {
            throw new IllegalStateException(EXCEPTION_MSG_TRUNCATE_AND_LOG_STREAM_CTRL_IN_PARALLEL);
        }

        if (position <= getCommitPosition())
        {
            throw new IllegalArgumentException(EXCEPTION_MSG_TRUNCATE_COMMITTED_POSITION);
        }

        final long truncateAddress = getAddressForPosition(this, position);
        if (truncateAddress != INVALID_ADDRESS)
        {
            logStorage.truncate(truncateAddress);
            logBlockIndexController.truncate();
        }
        else
        {
            throw  new IllegalArgumentException(String.format(EXCEPTION_MSG_TRUNCATE_FAILED, position));
        }
    }

    @Override
    public String toString()
    {
        return "LogStreamImpl{" +
            "topicName=" + bufferAsString(topicName) +
            ", partitionId=" + partitionId +
            ", term=" + term +
            ", name='" + name + '\'' +
            '}';
    }

    // BUILDER ////////////////////////
    public static class LogStreamBuilder<T extends LogStreamBuilder>
    {
        // MANDATORY /////
        // LogController Base
        protected final DirectBuffer topicName;
        protected final int partitionId;
        protected final String logName;
        protected AgentRunnerService agentRunnerService;
        protected LogStorage logStorage;
        protected LogBlockIndex logBlockIndex;

        protected String logRootPath;
        protected String logDirectory;

        protected CountersManager countersManager;

        // OPTIONAL ////////////////////////////////////////////
        protected boolean logStreamControllerDisabled;
        protected int initialLogSegmentId = 0;
        protected boolean deleteOnClose;
        protected int maxAppendBlockSize = 1024 * 1024 * 4;
        protected int writeBufferSize = 1024 * 1024 * 16;
        protected int logSegmentSize = 1024 * 1024 * 128;
        protected int indexBlockSize = DEFAULT_INDEX_BLOCK_SIZE;
        protected float deviation = LogBlockIndexController.DEFAULT_DEVIATION;
        protected int readBlockSize = DEFAULT_READ_BLOCK_SIZE;
        protected SnapshotPolicy snapshotPolicy;
        protected SnapshotStorage snapshotStorage;

        protected AgentRunnerService writeBufferAgentRunnerService;
        protected Dispatcher writeBuffer;

        public LogStreamBuilder(final DirectBuffer topicName, final int partitionId)
        {
            this.topicName = topicName;
            this.partitionId = partitionId;
            this.logName = String.format("%s.%d", bufferAsString(topicName), partitionId);
        }

        @SuppressWarnings("unchecked")
        protected T self()
        {
            return (T) this;
        }

        public T logRootPath(String logRootPath)
        {
            this.logRootPath = logRootPath;
            return self();
        }

        public T logDirectory(String logDir)
        {
            this.logDirectory = logDir;
            return self();
        }

        public T writeBufferSize(int writeBufferSize)
        {
            this.writeBufferSize = writeBufferSize;
            return self();
        }

        public T maxAppendBlockSize(int maxAppendBlockSize)
        {
            this.maxAppendBlockSize = maxAppendBlockSize;
            return self();
        }

        public T writeBufferAgentRunnerService(AgentRunnerService writeBufferAgentRunnerService)
        {
            this.writeBufferAgentRunnerService = writeBufferAgentRunnerService;
            return self();
        }

        public T initialLogSegmentId(int logFragmentId)
        {
            this.initialLogSegmentId = logFragmentId;
            return self();
        }

        public T logSegmentSize(int logSegmentSize)
        {
            this.logSegmentSize = logSegmentSize;
            return self();
        }

        public T deleteOnClose(boolean deleteOnClose)
        {
            this.deleteOnClose = deleteOnClose;
            return self();
        }

        public T agentRunnerService(AgentRunnerService agentRunnerService)
        {
            this.agentRunnerService = agentRunnerService;
            return self();
        }

        public T countersManager(CountersManager countersManager)
        {
            this.countersManager = countersManager;
            return self();
        }

        public T indexBlockSize(int indexBlockSize)
        {
            this.indexBlockSize = indexBlockSize;
            return self();
        }

        public T deviation(float deviation)
        {
            this.deviation = deviation;
            return self();
        }

        public T logStorage(LogStorage logStorage)
        {
            this.logStorage = logStorage;
            return self();
        }

        public T logBlockIndex(LogBlockIndex logBlockIndex)
        {
            this.logBlockIndex = logBlockIndex;
            return self();
        }

        public T logStreamControllerDisabled(boolean logStreamControllerDisabled)
        {
            this.logStreamControllerDisabled = logStreamControllerDisabled;
            return self();
        }

        public T writeBuffer(Dispatcher writeBuffer)
        {
            this.writeBuffer = writeBuffer;
            return self();
        }

        public T snapshotStorage(SnapshotStorage snapshotStorage)
        {
            this.snapshotStorage = snapshotStorage;
            return self();
        }

        public T snapshotPolicy(SnapshotPolicy snapshotPolicy)
        {
            this.snapshotPolicy = snapshotPolicy;
            return self();
        }

        public T readBlockSize(int readBlockSize)
        {
            this.readBlockSize = readBlockSize;
            return self();
        }

        // getter /////////////////


        public DirectBuffer getTopicName()
        {
            return topicName;
        }

        public int getPartitionId()
        {
            return partitionId;
        }

        public String getLogName()
        {
            return logName;
        }

        public AgentRunnerService getAgentRunnerService()
        {
            if (agentRunnerService == null)
            {
                Objects.requireNonNull(agentRunnerService, "No agent runner service provided.");
            }

            return agentRunnerService;
        }

        protected void initLogStorage()
        {
        }

        public LogStorage getLogStorage()
        {
            if (logStorage == null)
            {
                initLogStorage();
            }
            return logStorage;
        }

        public LogBlockIndex getBlockIndex()
        {
            if (logBlockIndex == null)
            {
                this.logBlockIndex = new LogBlockIndex(100000, (c) -> new UnsafeBuffer(ByteBuffer.allocate(c)));
            }
            return logBlockIndex;
        }

        public int getMaxAppendBlockSize()
        {
            return maxAppendBlockSize;
        }

        public int getIndexBlockSize()
        {
            return indexBlockSize;
        }

        public int getReadBlockSize()
        {
            return readBlockSize;
        }

        public SnapshotPolicy getSnapshotPolicy()
        {
            if (snapshotPolicy == null)
            {
                snapshotPolicy = new TimeBasedSnapshotPolicy(Duration.ofMinutes(1));
            }
            return snapshotPolicy;
        }

        public AgentRunnerService getWriteBufferAgentRunnerService()
        {
            return writeBufferAgentRunnerService;
        }

        protected Dispatcher initWriteBuffer(Dispatcher writeBuffer, BufferedLogStreamReader logReader,
                                             String logName, int writeBufferSize)
        {
            if (writeBuffer == null)
            {
                // Get position of last entry
                long lastPosition = 0;

                logReader.seekToLastEvent();

                if (logReader.hasNext())
                {
                    final LoggedEvent lastEntry = logReader.next();
                    lastPosition = lastEntry.getPosition();
                }

                // dispatcher needs to generate positions greater than the last position
                int partitionId = 0;

                if (lastPosition > 0)
                {
                    partitionId = PositionUtil.partitionId(lastPosition);
                }

                writeBuffer = Dispatchers.create("log-write-buffer-" + logName)
                    .bufferSize(writeBufferSize)
                    .subscriptions("log-appender")
                    .initialPartitionId(partitionId + 1)
                    .conductorExternallyManaged()
                    .build();
            }
            return writeBuffer;
        }

        public Dispatcher getWriteBuffer()
        {
            if (writeBuffer == null)
            {
                final BufferedLogStreamReader logReader = new BufferedLogStreamReader(getLogStorage(), getBlockIndex());
                writeBuffer = initWriteBuffer(writeBuffer, logReader, logName, writeBufferSize);
            }
            return writeBuffer;
        }

        public boolean isLogStreamControllerDisabled()
        {
            return logStreamControllerDisabled;
        }

        public void initSnapshotStorage()
        {
        }

        public SnapshotStorage getSnapshotStorage()
        {
            if (snapshotStorage == null)
            {
                initSnapshotStorage();
            }
            return snapshotStorage;
        }

        public float getDeviation()
        {
            return deviation;
        }

        public LogStream build()
        {
            Objects.requireNonNull(getTopicName(), "topicName");
            ensureGreaterThanOrEqual("partitionId", partitionId, 0);
            Objects.requireNonNull(getLogStorage(), "logStorage");
            Objects.requireNonNull(getBlockIndex(), "blockIndex");
            Objects.requireNonNull(getAgentRunnerService(), "agentRunnerService");
            ensureFalse("deviation", deviation <= 0f || deviation > 1f);

            return new LogStreamImpl(this);
        }
    }

}
