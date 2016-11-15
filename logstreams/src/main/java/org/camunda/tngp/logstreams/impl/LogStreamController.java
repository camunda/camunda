package org.camunda.tngp.logstreams.impl;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.logstreams.LogStreamFailureListener;
import org.camunda.tngp.logstreams.StreamContext;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class LogStreamController implements Agent
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;
    protected static final int TRANSITION_FAIL = 3;
    protected static final int TRANSITION_SNAPSHOT = 4;

    protected final OpeningState openingState = new OpeningState();
    protected final OpenState openState = new OpenState();
    protected final SnapshottingState snapshottingState = new SnapshottingState();
    protected final FailingState failingState = new FailingState();
    protected final FailedState failedState = new FailedState();
    protected final ClosingState closingState = new ClosingState();
    protected final ClosedState closedState = new ClosedState();

    protected final StateMachineAgent<Context> stateMachine = new StateMachineAgent<>(
            StateMachine.<Context> builder(s -> new Context(s))
            .initialState(closedState)
            .from(closedState).take(TRANSITION_OPEN).to(openingState)
            .from(openingState).take(TRANSITION_DEFAULT).to(openState)
            .from(openingState).take(TRANSITION_FAIL).to(failingState)
            .from(openState).take(TRANSITION_SNAPSHOT).to(snapshottingState)
            .from(openState).take(TRANSITION_FAIL).to(failingState)
            .from(openState).take(TRANSITION_CLOSE).to(closingState)
            .from(snapshottingState).take(TRANSITION_DEFAULT).to(openState)
            .from(snapshottingState).take(TRANSITION_FAIL).to(failingState)
            .from(failingState).take(TRANSITION_DEFAULT).to(failedState)
            .from(failedState).take(TRANSITION_CLOSE).to(closingState)
            .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
            .build()
            );

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

    @Override
    public int doWork()
    {
        return stateMachine.doWork();
    }

    private class Context extends SimpleStateMachineContext
    {
        private int currentBlockSize = 0;
        private long lastPosition = 0;

        Context(StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        public int getCurrentBlockSize()
        {
            return currentBlockSize;
        }

        public long getLastPosition()
        {
            return lastPosition;
        }

        public void setCurrentBlockSize(int currentBlockSize)
        {
            this.currentBlockSize = currentBlockSize;
        }

        public void setLastPosition(long lastPosition)
        {
            this.lastPosition = lastPosition;
        }

    }

    class OpeningState implements TransitionState<Context>
    {
        @Override
        public void work(Context context)
        {
            try
            {
                final Dispatcher writeBuffer = streamContext.getWriteBuffer();
                writeBufferSubscription = writeBuffer.getSubscriptionByName("log-appender");

                recoverBlockIndex();

                context.take(TRANSITION_DEFAULT);
                openFuture.complete(null);
            }
            catch (Exception e)
            {
                context.take(TRANSITION_FAIL);

                openFuture.completeExceptionally(e);
            }
            finally
            {
                openFuture = null;
            }
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

    class OpenState implements State<Context>
    {
        @Override
        public int doWork(Context context)
        {
            final int bytesAvailable = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

            if (bytesAvailable > 0)
            {
                final ByteBuffer nioBuffer = blockPeek.getRawBuffer();
                final MutableDirectBuffer buffer = blockPeek.getBuffer();

                final long postion = buffer.getLong(positionOffset(messageOffset(0)));
                context.setLastPosition(postion);

                final long address = logStorage.append(nioBuffer);

                if (address >= 0)
                {
                    onBlockWritten(context, postion, address, blockPeek.getBlockLength());
                    blockPeek.markCompleted();

                    if (snapshotPolicy.apply(postion))
                    {
                        context.take(TRANSITION_SNAPSHOT);
                    }
                }
                else
                {
                    blockPeek.markFailed();

                    context.take(TRANSITION_FAIL);
                }

                return 1;
            }
            else
            {
                return 0;
            }
        }

        protected void onBlockWritten(Context context, long postion, long addr, int blockLength)
        {
            int currentBlockSize = context.getCurrentBlockSize();
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
            context.setCurrentBlockSize(currentBlockSize);
        }
    }

    class FailingState implements TransitionState<Context>
    {
        @Override
        public void work(Context context)
        {
            for (int i = 0; i < failureListeners.size(); i++)
            {
                final LogStreamFailureListener logStreamWriteErrorListener = failureListeners.get(i);
                try
                {
                    logStreamWriteErrorListener.onFailed(context.getLastPosition());
                }
                catch (Exception e)
                {
                    System.err.println("Exception while invoking " + logStreamWriteErrorListener + ".");
                }
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    class FailedState implements State<Context>
    {
        @Override
        public int doWork(Context context)
        {
            final int available = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

            if (available > 0)
            {
                blockPeek.markFailed();
            }

            return available;
        }
    }

    class ClosingState implements TransitionState<Context>
    {

        @Override
        public void work(Context context)
        {
            try
            {
                logStorage.close();
            }
            finally
            {
                context.take(TRANSITION_DEFAULT);

                closeFuture.complete(null);
                closeFuture = null;
            }
        }

    }

    class ClosedState implements WaitState<Context>
    {
        @Override
        public void work(Context context)
        {
            if (isRunning.compareAndSet(true, false))
            {
                agentRunnerService.remove(LogStreamController.this);
            }
        }

    }

    class SnapshottingState implements TransitionState<Context>
    {

        @Override
        public void work(Context context)
        {
            SnapshotWriter snapshotWriter = null;

            try
            {
                // TODO should do recovery if fails to flush because of corrupted block index - see #8

                // flush the log to ensure that the snapshot doesn't contains indexes of unwritten events
                logStorage.flush();

                snapshotWriter = snapshotStorage.createSnapshot(name, context.getLastPosition());

                snapshotWriter.writeSnapshot(blockIndex);
                snapshotWriter.commit();

                context.take(TRANSITION_DEFAULT);
            }
            catch (Exception e)
            {
                e.printStackTrace();

                if (snapshotWriter != null)
                {
                    snapshotWriter.abort();
                }

                context.take(TRANSITION_FAIL);
            }
        }
    }

    public CompletableFuture<Void> openAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        stateMachine.addCommand(context ->
        {
            final boolean opening = context.tryTake(TRANSITION_OPEN);
            if (opening)
            {
                openFuture = future;
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

        stateMachine.addCommand(context ->
        {
            final boolean closing = context.tryTake(TRANSITION_CLOSE);
            if (closing)
            {
                closeFuture = future;
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
        return stateMachine.getCurrentState() == closedState;
    }

    public boolean isOpen()
    {
        return stateMachine.getCurrentState() == openState;
    }

    public boolean isFailed()
    {
        return stateMachine.getCurrentState() == failedState;
    }

    public void registerFailureListener(LogStreamFailureListener listener)
    {
        stateMachine.addCommand(context -> failureListeners.add(listener));
    }

    public void removeFailureListener(LogStreamFailureListener listener)
    {
        stateMachine.addCommand(context -> failureListeners.remove(listener));
    }

}
