package org.camunda.tngp.logstreams.impl;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.logstreams.LogStreamFailureListener;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class LogStreamController implements Agent
{
    protected final ClosedState closedState = new ClosedState();
    protected final OpeningState openingState = new OpeningState();
    protected final OpenState openState = new OpenState();
    protected final SnapshottingState snapshottingState = new SnapshottingState();
    protected final FailingState failingState = new FailingState();
    protected final FailedState failedState = new FailedState();
    protected final ClosingState closingState = new ClosingState();

    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(16);

    protected final Consumer<Runnable> cmdConsumer = (r) ->
    {
        r.run();
    };

    protected final AtomicBoolean isRunning = new AtomicBoolean(false);

    protected final String name;
    protected final StreamContext streamContext;
    protected final LogStorage logStorage;
    protected final SnapshotStorage snapshotStorage;
    protected final SnapshotPolicy snapshotPolicy;
    protected final LogBlockIndex blockIndex;
    protected final AgentRunnerService agentRunnerService;
    protected final BlockPeek blockPeek = new BlockPeek();

    protected List<LogStreamFailureListener> failureListeners = new ArrayList<>();

    protected volatile LogStreamControllerState state = closedState;

    protected Subscription writeBufferSubscription;

    protected final int maxAppendBlockSize;
    protected final int indexBlockSize;

    protected CompletableFuture<Void> closeFuture;
    protected CompletableFuture<Void> openFuture;

    public LogStreamController(String name, StreamContext streamContext)
    {
        this.name = name;
        this.streamContext = streamContext;
        this.blockIndex = streamContext.getBlockIndex();
        this.logStorage = streamContext.getLogStorage();
        this.snapshotStorage = streamContext.getSnapshotStorage();
        this.snapshotPolicy = streamContext.getSnapshotPolicy();
        this.agentRunnerService = streamContext.getAgentRunnerService();

        this.maxAppendBlockSize = streamContext.getMaxAppendBlockSize();
        this.indexBlockSize = streamContext.getIndexBlockSize();
    }

    @Override
    public String roleName()
    {
        return name;
    }

    public int doWork()
    {
        int workCount = 0;

        if (state.acceptsCommands())
        {
            workCount += cmdQueue.drain(cmdConsumer);
        }

        workCount += state.doWork();

        return workCount;
    }

    interface LogStreamControllerState
    {
        int doWork();

        default boolean acceptsCommands()
        {
            return false;
        }
    }

    class OpeningState implements LogStreamControllerState
    {
        @Override
        public int doWork()
        {
            try
            {
                final Dispatcher writeBuffer = streamContext.getWriteBuffer();
                writeBufferSubscription = writeBuffer.getSubscriptionByName("log-appender");

                recoverBlockIndex();

                state = openState;
                openFuture.complete(null);
            }
            catch (Exception e)
            {
                openFuture.completeExceptionally(e);
                state = closedState;
            }
            finally
            {
                openFuture = null;
            }

            return 1;
        }

        protected void recoverBlockIndex() throws Exception
        {
            final ReadableSnapshot lastSnapshot = snapshotStorage.getLastSnapshot(name);
            if (lastSnapshot != null)
            {
                lastSnapshot.recoverFromSnapshot(blockIndex);
                lastSnapshot.validateAndClose();

                blockIndex.recover(logStorage, lastSnapshot.getPosition(), indexBlockSize);
            }
            else
            {
                blockIndex.recover(logStorage, indexBlockSize);
            }
        }

    }

    class OpenState implements LogStreamControllerState
    {
        private int currentBlockSize;

        @Override
        public boolean acceptsCommands()
        {
            return true;
        }

        @Override
        public int doWork()
        {
            final int bytesAvailable = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

            if (bytesAvailable > 0)
            {
                final ByteBuffer nioBuffer = blockPeek.getRawBuffer();
                final MutableDirectBuffer buffer = blockPeek.getBuffer();

                final long postion = buffer.getLong(positionOffset(messageOffset(0)));

                final long address = logStorage.append(nioBuffer);

                if (address >= 0)
                {
                    onBlockWritten(postion, address, blockPeek.getBlockLength());
                    blockPeek.markCompleted();

                    if (snapshotPolicy.apply(postion))
                    {
                        state = snapshottingState;
                        snapshottingState.lastEventLogPosition = postion;
                    }
                }
                else
                {
                    blockPeek.markFailed();

                    gotoFailingState(postion);
                }

                return 1;
            }
            else
            {
                return 0;
            }
        }

        protected void onBlockWritten(long postion, long addr, int blockLength)
        {
            if (currentBlockSize == 0)
            {
                blockIndex.addBlock(postion, addr);
            }

            final int newBlockSize = currentBlockSize + blockLength;
            if (newBlockSize > indexBlockSize)
            {
                currentBlockSize = 0;
            }
            else
            {
                currentBlockSize = newBlockSize;
            }
        }
    }

    class FailingState implements LogStreamControllerState
    {
        long failedPosition;

        @Override
        public int doWork()
        {
            state = failedState;

            for (int i = 0; i < failureListeners.size(); i++)
            {
                final LogStreamFailureListener logStreamWriteErrorListener = failureListeners.get(i);
                try
                {
                    logStreamWriteErrorListener.onFailed(failedPosition);
                }
                catch (Exception e)
                {
                    System.err.println("Exception while invoking " + logStreamWriteErrorListener + ".");
                }
            }

            return 1;
        }
    }

    class FailedState implements LogStreamControllerState
    {
        @Override
        public boolean acceptsCommands()
        {
            return true;
        }

        @Override
        public int doWork()
        {
            final int available = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

            if (available > 0)
            {
                blockPeek.markFailed();
            }

            return available;
        }
    }

    class ClosingState implements LogStreamControllerState
    {

        @Override
        public int doWork()
        {
            try
            {
                logStorage.close();
            }
            finally
            {
                state = closedState;
                closeFuture.complete(null);
            }

            return 1;
        }

    }

    class ClosedState implements LogStreamControllerState
    {
        @Override
        public boolean acceptsCommands()
        {
            return true;
        }

        @Override
        public int doWork()
        {
            if (isRunning.compareAndSet(true, false))
            {
                agentRunnerService.remove(LogStreamController.this);
            }
            return 0;
        }

    }

    class SnapshottingState implements LogStreamControllerState
    {
        long lastEventLogPosition;

        @Override
        public int doWork()
        {
            SnapshotWriter snapshotWriter = null;

            try
            {
                snapshotWriter = snapshotStorage.createSnapshot(name, lastEventLogPosition);

                snapshotWriter.writeSnapshot(blockIndex);
                snapshotWriter.commit();

                state = openState;
            }
            catch (Exception e)
            {
                e.printStackTrace();

                if (snapshotWriter != null)
                {
                    snapshotWriter.abort();
                }

                gotoFailingState(lastEventLogPosition);
            }

            return 1;
        }
    }

    protected void gotoFailingState(final long postion)
    {
        state = failingState;
        failingState.failedPosition = postion;
    }

    public CompletableFuture<Void> openAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            if (state == closedState)
            {
                openFuture = future;
                state = openingState;
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot open log stream. State is not closed."));
            }
        });

        if (isRunning.compareAndSet(false, true))
        {
            try
            {
                agentRunnerService.run(this);
            }
            catch (Exception e)
            {
                isRunning.set(false);
                openFuture.completeExceptionally(e);
            }
        }

        return future;
    }

    public CompletableFuture<Void> closeAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            if (state == openState || state == failedState)
            {
                closeFuture = future;
                this.state = closingState;
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot close log stream. State is not open."));
            }
        });

        return future;
    }

    public boolean isClosed()
    {
        return state == closedState;
    }

    public boolean isOpen()
    {
        return state == openState;
    }

    public boolean isFailed()
    {
        return state == failedState;
    }

    public void registerFailureListener(LogStreamFailureListener listener)
    {
        cmdQueue.add(() ->
        {
            failureListeners.add(listener);
        });
    }

    public void removeFailureListener(LogStreamFailureListener listener)
    {
        cmdQueue.add(() ->
        {
            failureListeners.remove(listener);
        });
    }

}
