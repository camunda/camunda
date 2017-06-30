package org.camunda.tngp.logstreams.impl;

import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.getPosition;
import static org.camunda.tngp.logstreams.impl.LogStateMachineAgent.TRANSITION_CLOSE;
import static org.camunda.tngp.logstreams.impl.LogStateMachineAgent.TRANSITION_DEFAULT;
import static org.camunda.tngp.logstreams.impl.LogStateMachineAgent.TRANSITION_OPEN;
import static org.camunda.tngp.logstreams.log.LogStreamUtil.INVALID_ADDRESS;
import static org.camunda.tngp.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
import static org.camunda.tngp.logstreams.spi.LogStorage.OP_RESULT_INVALID_ADDR;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.Position;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.*;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.Actor;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

/**
 * Represents the log block index controller, which creates the log block index
 * for the given log storage.
 */
public class LogBlockIndexController implements Actor
{
    /**
     * The default deviation is 10%. That means for blocks which are filled 90%
     * a block index will be created.
     */
    public static final float DEFAULT_DEVIATION = 0.1f;

    protected static final int TRANSITION_SNAPSHOT = 3;
    protected static final int TRANSITION_TRUNCATE = 4;
    protected static final int TRANSITION_CREATE = 5;

    // STATES /////////////////////////////////////////////////////////

    protected final OpeningState openingState = new OpeningState();
    protected final OpenState openState = new OpenState();
    protected final SnapshottingState snapshottingState = new SnapshottingState();
    protected final ClosedState closedState = new ClosedState();
    protected final TruncateState truncateState = new TruncateState();
    protected final BlockIndexCreationState blockIndexCreationState = new BlockIndexCreationState();

    protected final LogStateMachineAgent stateMachine;

    //  MANDATORY //////////////////////////////////////////////////

    protected final String name;
    protected final LogStorage logStorage;
    protected final LogBlockIndex blockIndex;
    protected final ActorScheduler actorScheduler;
    protected ActorReference actorRef;

    /**
     * Defines the block size for which an index will be created.
     */
    protected final int indexBlockSize;

    /**
     * The deviation which will be used in calculation of the index block size.
     * It defines the allowable tolerance. That means if the deviation is set to 0.1f (10%),
     * an index will be created if the block is 90 % filled.
     */
    protected final float deviation;

    protected final SnapshotStorage snapshotStorage;
    protected final SnapshotPolicy snapshotPolicy;
    protected final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
    protected final CompleteEventsInBlockProcessor readResultProcessor = new CompleteEventsInBlockProcessor();
    protected final Runnable openStateRunnable;
    protected final Runnable closedStateRunnable;

    // INTERNAL ///////////////////////////////////////////////////

    protected long nextAddress = INVALID_ADDRESS;
    protected int bufferSize;
    protected ByteBuffer ioBuffer;
    protected CompletableFuture<Void> truncateFuture;
    protected Position commitPosition;

    public LogBlockIndexController(LogStreamImpl.LogStreamBuilder logStreamBuilder)
    {
        this(logStreamBuilder, null);
    }

    public LogBlockIndexController(LogStreamImpl.LogStreamBuilder logStreamBuilder, Position commitPosition)
    {
        this.name = logStreamBuilder.getLogName();
        this.logStorage = logStreamBuilder.getLogStorage();
        this.blockIndex = logStreamBuilder.getBlockIndex();
        this.actorScheduler = logStreamBuilder.getActorScheduler();
        this.commitPosition = commitPosition;

        this.deviation = logStreamBuilder.getDeviation();
        this.indexBlockSize = (int) (logStreamBuilder.getIndexBlockSize() * (1f - deviation));
        this.snapshotStorage = logStreamBuilder.getSnapshotStorage();
        this.snapshotPolicy = logStreamBuilder.getSnapshotPolicy();
        this.bufferSize = logStreamBuilder.getReadBlockSize();
        ioBuffer = ByteBuffer.allocateDirect(bufferSize);
        buffer.wrap(ioBuffer);

        this.openStateRunnable = () ->
        {
            actorRef = actorScheduler.schedule(this);
        };
        this.closedStateRunnable = () -> actorRef.close();

        this.stateMachine = new LogStateMachineAgent(
            StateMachine.<LogContext>builder(s -> new LogContext(s))
                .initialState(closedState)
                .from(openingState).take(TRANSITION_DEFAULT).to(openState)

                .from(openState).take(TRANSITION_TRUNCATE).to(truncateState)
                .from(truncateState).take(TRANSITION_DEFAULT).to(openState)
                .from(openState).take(TRANSITION_CLOSE).to(closedState)
                .from(closedState).take(TRANSITION_OPEN).to(openingState)
                .from(openState).take(TRANSITION_CREATE).to(blockIndexCreationState)

                .from(blockIndexCreationState).take(TRANSITION_SNAPSHOT).to(snapshottingState)
                .from(snapshottingState).take(TRANSITION_DEFAULT).to(openState)
                .from(blockIndexCreationState).take(TRANSITION_TRUNCATE).to(truncateState)
                .from(blockIndexCreationState).take(TRANSITION_CLOSE).to(closedState)
                .from(blockIndexCreationState).take(TRANSITION_DEFAULT).to(openState)

                .build(), openStateRunnable, closedStateRunnable);
    }

    @Override
    public int doWork()
    {
        return getStateMachine().doWork();
    }

    @Override
    public String name()
    {
        return name;
    }

    protected LogStateMachineAgent getStateMachine()
    {
        return stateMachine;
    }

    protected class OpeningState implements TransitionState<LogContext>
    {
        @Override
        public void work(LogContext logContext)
        {
            try
            {
                if (!logStorage.isOpen())
                {
                    logStorage.open();
                }

                recoverBlockIndex();
            }
            catch (Exception e)
            {
                // snapshot could not been read - so we start with the first block
                nextAddress = logStorage.getFirstBlockAddress();
            }
            finally
            {
                logContext.take(TRANSITION_DEFAULT);

                stateMachine.completeOpenFuture(null);
            }
        }

        protected void recoverBlockIndex() throws Exception
        {
            final long recoveredAddress = logStorage.getFirstBlockAddress();
            final ReadableSnapshot lastSnapshot = snapshotStorage.getLastSnapshot(name);
            if (lastSnapshot != null)
            {
                lastSnapshot.recoverFromSnapshot(blockIndex);
                nextAddress = Math.max(blockIndex.lookupBlockAddress(lastSnapshot.getPosition()),
                    recoveredAddress);
            }
            else
            {
                nextAddress = recoveredAddress;
            }
        }
    }

    protected class OpenState implements State<LogContext>
    {
        private int currentBlockSize = 0;

        @Override
        public int doWork(LogContext logContext)
        {
            int result = 0;
            if (nextAddress != INVALID_ADDRESS)
            {
                final long currentAddress = nextAddress;

                // read buffer with only complete events
                final long nextAddressToRead = logStorage.read(ioBuffer, currentAddress, readResultProcessor);
                if (nextAddressToRead > currentAddress)
                {
                    tryToCreateBlockIndex(logContext, currentAddress);
                    // set next address
                    nextAddress = nextAddressToRead;
                    result = 1;
                }
                else if (nextAddressToRead == OP_RESULT_INVALID_ADDR)
                {
                    System.err.println(String.format("Can't read from illegal address: %d", currentAddress));
                }
                else if (nextAddressToRead == OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY)
                {
                    increaseBufferSize();
                    result = 1;
                }
            }
            else
            {
                nextAddress = resolveLastValidAddress();
            }
            return result;
        }

        private long resolveLastValidAddress()
        {
            long newAddress = 0;
            if (blockIndex.size() > 0)
            {
                newAddress = blockIndex.getAddress(blockIndex.size());
            }
            if (newAddress <= 0)
            {
                newAddress = logStorage.getFirstBlockAddress();
            }
            return newAddress;
        }

        private void tryToCreateBlockIndex(LogContext logContext, long currentAddress)
        {
            if (!logContext.hasCurrentBlockAddress())
            {
                logContext.setCurrentBlockAddress(currentAddress);
                logContext.setFirstEventPosition(getPosition(buffer, 0));
            }

            currentBlockSize += ioBuffer.position();
            // if block size is greater then or equals to index block size we will create an index
            if (currentBlockSize >= indexBlockSize)
            {
                logContext.take(TRANSITION_CREATE);
                currentBlockSize = 0;
            }
            else
            {
                // block was not filled enough
                // read next events into buffer after the current read events
                ioBuffer.clear();
            }
        }

        private void increaseBufferSize()
        {
            // increase buffer and try again
            bufferSize *= 2;
            ioBuffer = ByteBuffer.allocateDirect(bufferSize);
            buffer.wrap(ioBuffer);
        }

        public void reset(LogContext context)
        {
            currentBlockSize = 0;
            nextAddress = context.getCurrentBlockAddress();
        }
    }

    protected class SnapshottingState implements TransitionState<LogContext>
    {
        @Override
        public void work(LogContext logContext)
        {
            SnapshotWriter snapshotWriter = null;
            try
            {
                // should do recovery if fails to flush because of corrupted block index - see #8

                // flush the log to ensure that the snapshot doesn't contains indexes of unwritten events
                logStorage.flush();

                snapshotWriter = snapshotStorage.createSnapshot(name, logContext.getFirstEventPosition());

                snapshotWriter.writeSnapshot(blockIndex);
                snapshotWriter.commit();
            }
            catch (Exception e)
            {
                e.printStackTrace();

                if (snapshotWriter != null)
                {
                    snapshotWriter.abort();
                }
            }
            finally
            {
                logContext.setFirstEventPosition(0);
                // regardless whether the writing of the snapshot was successful or not we go to the open state
                logContext.take(TRANSITION_DEFAULT);
            }
        }
    }

    protected class ClosedState implements WaitState<LogContext>
    {
        @Override
        public void work(LogContext logContext) throws Exception
        {
            getStateMachine().closing();
        }
    }

    public boolean isClosed()
    {
        return getStateMachine().getCurrentState() == closedState;
    }

    public boolean isOpen()
    {
        return getStateMachine().getCurrentState() == openState;
    }

    public boolean isRunning()
    {
        return getStateMachine().isRunning();
    }

    public boolean isInCreateState()
    {
        return getStateMachine().getCurrentState() == blockIndexCreationState;
    }

    public void open()
    {
        getStateMachine().open();
    }

    public CompletableFuture<Void> openAsync()
    {
        return getStateMachine().openAsync();
    }

    public void close()
    {
        getStateMachine().close();
    }

    public CompletableFuture<Void> closeAsync()
    {
        return getStateMachine().closeAsync();
    }

    public long getNextAddress()
    {
        return nextAddress;
    }

    public int getIndexBlockSize()
    {
        return indexBlockSize;
    }

    public long getCommitPosition()
    {
        if (commitPosition == null)
        {
            return INVALID_ADDRESS;
        }
        else
        {
            return commitPosition.get();
        }
    }

    public CompletableFuture<Void> truncate()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        getStateMachine().addCommand(LogContext ->
        {
            final boolean possibleToTakeTransition = LogContext.tryTake(TRANSITION_TRUNCATE);
            if (possibleToTakeTransition)
            {
                truncateFuture = future;
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot truncate log stream. State is neither open nor create."));
            }
        });

        return future;
    }

    private class BlockIndexCreationState implements State<LogContext>
    {
        @Override
        public int doWork(LogContext logContext) throws Exception
        {
            int result = 0;
            if (readResultProcessor.getLastReadEventPosition() <= getCommitPosition())
            {
                createBlockIdx(logContext, logContext.getCurrentBlockAddress());

                // reset buffer position and limit for reuse
                ioBuffer.clear();

                // reset cached block address
                logContext.resetCurrentBlockAddress();
                result = 1;
            }
            return result;
        }

        private void createBlockIdx(LogContext logContext, long addressOfFirstEventInBlock)
        {
            // write block IDX
            final long position = logContext.getFirstEventPosition();
            blockIndex.addBlock(position, addressOfFirstEventInBlock);

            // check if snapshot should be created
            if (snapshotPolicy.apply(position))
            {
                logContext.take(TRANSITION_SNAPSHOT);
            }
            else
            {
                logContext.resetLastPosition();
                logContext.take(TRANSITION_DEFAULT);
            }
        }
    }

    private class TruncateState implements State<LogContext>
    {
        @Override
        public int doWork(LogContext logContext) throws Exception
        {
            try
            {
                openState.reset(logContext);
                logContext.reset();
            }
            finally
            {
                truncateFuture.complete(null);
                truncateFuture = null;
                logContext.take(TRANSITION_DEFAULT);
            }
            return 0;
        }
    }
}
