package org.camunda.tngp.logstreams.impl;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.PositionUtil;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamFailureListener;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.util.agent.AgentRunnerService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import static org.camunda.tngp.logstreams.impl.LogStreamImpl.LogStreamBuilder.initWriteBuffer;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public final class LogStreamImpl implements LogStream
{
    private final BiConsumer<Void, Throwable> removeWriteBufferReference = (((aVoid, throwable) -> this.writeBuffer = null));

    private final BiConsumer<Void, Throwable> removeLogStreamControllerReference = ((aVoid, throwable) ->
    {
        this.logStreamController = null;
        this.writeBuffer.closeAsync().whenComplete(removeWriteBufferReference);
    });

    protected final LogBlockIndex logBlockIndex;
    protected final LogStorage logStorage;
    protected final int logId;
    protected final String logName;
    protected final AgentRunnerService agentRunnerService;

    protected final LogBlockIndexController logBlockIndexController;

    protected LogStreamController logStreamController;
    protected Dispatcher writeBuffer;

    private LogStreamImpl(LogStreamBuilder logStreamBuilder)
    {
        this.logName = logStreamBuilder.getLogName();
        this.logId = logStreamBuilder.getLogId();
        this.logBlockIndex = logStreamBuilder.getBlockIndex();
        this.logStorage = logStreamBuilder.getLogStorage();
        this.agentRunnerService = logStreamBuilder.getAgentRunnerService();
        this.logBlockIndexController = new LogBlockIndexController(logStreamBuilder);

        if (!logStreamBuilder.isWithoutLogStreamController())
        {
            this.logStreamController = new LogStreamController(logStreamBuilder);
            this.writeBuffer = logStreamBuilder.getWriteBuffer();
        }
    }


    public abstract static class LogStreamBuilder implements LogBlockIndexController.LogBlockIndexControllerBuilder,
        LogStreamController.LogStreamControllerBuilder
    {

        protected static Dispatcher initWriteBuffer(Dispatcher writeBuffer, BufferedLogStreamReader logReader,
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

        public abstract int getLogId();

        public abstract boolean isWithoutLogStreamController();

        public LogStream build()
        {
            return new LogStreamImpl(this);
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
    public int getId()
    {
        return logId;
    }

    @Override
    public String getLogName()
    {
        return logName;
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
            final CompletableFuture<Void> logStreamControllerFuture = logStreamController.openAsync();
            completableFuture = CompletableFuture.allOf(logBlockIndexControllerFuture, logStreamControllerFuture);
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
        final BiConsumer<Void, Throwable> voidThrowableBiConsumer = (n, e) ->
        {
            logStorage.close();
            future.complete(null);
        };

        final CompletableFuture<Void> bFuture = logBlockIndexController.closeAsync();
        if (logStreamController != null)
        {
            final CompletableFuture<Void> lFuture = logStreamController.closeAsync();
            CompletableFuture.allOf(bFuture, lFuture).whenComplete(voidThrowableBiConsumer);
        }
        else
        {
            bFuture.whenComplete(voidThrowableBiConsumer);
        }

        return future;
    }

    @Override
    public long getCurrentAppenderPosition()
    {
        return logStreamController == null ? 0 : logStreamController.getCurrentAppenderPosition();
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
        return logBlockIndex;
    }

    @Override
    public int getIndexBlockSize()
    {
        return logBlockIndexController.getIndexBlockSize();
    }

    @Override
    public void stopLogStreaming()
    {
        if (logStreamController != null)
        {
            logStreamController.closeAsync().whenComplete(removeLogStreamControllerReference);
        }
    }

    @Override
    public CompletableFuture<Void> startLogStreaming(AgentRunnerService writeBufferAgentRunnerService)
    {
        return startLogStreaming(writeBufferAgentRunnerService, DEFAULT_MAX_APPEND_BLOCK_SIZE);
    }

    @Override
    public CompletableFuture<Void> startLogStreaming(AgentRunnerService writeBufferAgentRunnerService,
                                  int maxAppendBlockSize)
    {
        return new LogStreamControlBuilder()
            .maxAppendBlockSize(maxAppendBlockSize)
            .writeBufferAgentRunnerService(writeBufferAgentRunnerService)
            .build()
            .openAsync();
    }

    @Override
    public Dispatcher getWriteBuffer()
    {
        return writeBuffer;
    }

    private final class LogStreamControlBuilder implements LogStreamController.LogStreamControllerBuilder
    {
        protected int maxAppendBlockSize;
        protected int writeBufferSize = DEFAULT_WRITE_BUFFER_SIZE;
        protected Dispatcher writeBuffer;
        protected AgentRunnerService writeBufferAgentRunnerService;

        @Override
        public String getLogName()
        {
            return LogStreamImpl.this.logName;
        }

        @Override
        public LogStorage getLogStorage()
        {
            return LogStreamImpl.this.logStorage;
        }

        @Override
        public LogBlockIndex getBlockIndex()
        {
            return LogStreamImpl.this.logBlockIndex;
        }

        @Override
        public AgentRunnerService getAgentRunnerService()
        {
            return LogStreamImpl.this.agentRunnerService;
        }

        public LogStreamControlBuilder maxAppendBlockSize(int maxAppendBlockSize)
        {
            this.maxAppendBlockSize = maxAppendBlockSize;
            return this;
        }

        public LogStreamControlBuilder writeBufferSize(int writeBufferSize)
        {
            this.writeBufferSize = writeBufferSize;
            return this;
        }

        public LogStreamControlBuilder writeBuffer(Dispatcher writeBuffer)
        {
            this.writeBuffer = writeBuffer;
            return this;
        }

        public LogStreamControlBuilder writeBufferAgentRunnerService(AgentRunnerService writeBufferAgentRunnerService)
        {
            this.writeBufferAgentRunnerService = writeBufferAgentRunnerService;
            return this;
        }

        @Override
        public int getMaxAppendBlockSize()
        {
            return maxAppendBlockSize;
        }

        @Override
        public Dispatcher getWriteBuffer()
        {
            if (writeBuffer == null)
            {
                final BufferedLogStreamReader streamReader = new BufferedLogStreamReader(getLogStorage(), getLogBlockIndex());
                writeBuffer = initWriteBuffer(writeBuffer, streamReader, logName, writeBufferSize);
            }
            return writeBuffer;
        }

        @Override
        public AgentRunnerService getWriteBufferAgentRunnerService()
        {
            return writeBufferAgentRunnerService;
        }

        public LogStreamController build()
        {
            // init write buffer
            LogStreamImpl.this.logStreamController = new LogStreamController(this);
            LogStreamImpl.this.writeBuffer = LogStreamControlBuilder.this.getWriteBuffer();
            return logStreamController;
        }
    }
}
