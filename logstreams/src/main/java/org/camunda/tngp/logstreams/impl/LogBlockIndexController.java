package org.camunda.tngp.logstreams.impl;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.*;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.*;
import static org.camunda.tngp.logstreams.impl.LogStateMachineAgent.*;
import static org.camunda.tngp.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
import static org.camunda.tngp.logstreams.spi.LogStorage.OP_RESULT_INVALID_ADDR;

/**
 * Represents the log block index controller, which creates the log block index
 * for the given log storage.
 */
public class LogBlockIndexController implements Agent
{
    /**
     * The default deviation is 10%. That means for blocks which are filled 90%
     * a block index will be created.
     */
    public static final float DEFAULT_DEVIATION = 0.1f;

    private static final int ILLEGAL_ADDRESS = -1;
    protected static final int TRANSITION_SNAPSHOT = 3;
    protected static final int TRANSITION_TRUNCATE = 4;
    private static final int POSITION_LENGTH = positionOffset(messageOffset(0)) + SIZE_OF_LONG;

    // STATES /////////////////////////////////////////////////////////

    protected final OpeningState openingState = new OpeningState();
    protected final OpenState openState = new OpenState();
    protected final SnapshottingState snapshottingState = new SnapshottingState();
    protected final ClosedState closedState = new ClosedState();
    protected final TruncateState truncateState = new TruncateState();
    protected final LogStateMachineAgent stateMachine;

    //  MANDATORY //////////////////////////////////////////////////

    protected final String name;
    protected final LogStorage logStorage;
    protected final LogBlockIndex blockIndex;
    protected final AgentRunnerService agentRunnerService;

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
    protected final CompleteAndCommittedEventsInBlockProcessor readResultProcessor = new CompleteAndCommittedEventsInBlockProcessor();
    protected final Runnable openStateRunnable;
    protected final Runnable closedStateRunnable;

    // INTERNAL ///////////////////////////////////////////////////

    protected long nextAddress = ILLEGAL_ADDRESS;
    protected int bufferSize;
    protected ByteBuffer ioBuffer;
    protected CompletableFuture<Void> truncateFuture;
    protected long truncatePosition;

    public LogBlockIndexController(LogStreamImpl.LogStreamBuilder logStreamBuilder)
    {
        this.name = logStreamBuilder.getLogName();
        this.logStorage = logStreamBuilder.getLogStorage();
        this.blockIndex = logStreamBuilder.getBlockIndex();
        this.agentRunnerService = logStreamBuilder.getAgentRunnerService();
        this.readResultProcessor.setCommitPosition(logStreamBuilder.getCommitPosition().get());

        this.deviation = logStreamBuilder.getDeviation();
        this.indexBlockSize = (int) ((float) logStreamBuilder.getIndexBlockSize() * (1f - deviation));
        this.snapshotStorage = logStreamBuilder.getSnapshotStorage();
        this.snapshotPolicy = logStreamBuilder.getSnapshotPolicy();
        this.bufferSize = logStreamBuilder.getReadBlockSize();
        ioBuffer = ByteBuffer.allocateDirect(bufferSize);
        buffer.wrap(ioBuffer);

        this.openStateRunnable = () -> agentRunnerService.run(this);
        this.closedStateRunnable = () -> agentRunnerService.remove(this);
        this.stateMachine = new LogStateMachineAgent(
            StateMachine.<LogContext>builder(s -> new LogContext(s))
                .initialState(closedState)
                .from(openingState).take(TRANSITION_DEFAULT).to(openState)
                .from(openState).take(TRANSITION_SNAPSHOT).to(snapshottingState)
                .from(openState).take(TRANSITION_TRUNCATE).to(truncateState)
                .from(truncateState).take(TRANSITION_SNAPSHOT).to(snapshottingState)
                .from(truncateState).take(TRANSITION_DEFAULT).to(openState)
                .from(snapshottingState).take(TRANSITION_DEFAULT).to(openState)
                .from(openState).take(TRANSITION_CLOSE).to(closedState)
                .from(closedState).take(TRANSITION_OPEN).to(openingState)
                .build(), openStateRunnable, closedStateRunnable);
    }

    @Override
    public int doWork()
    {
        return getStateMachine().doWork();
    }

    @Override
    public String roleName()
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
            // open state
            if (nextAddress == ILLEGAL_ADDRESS)
            {
                nextAddress = logStorage.getFirstBlockAddress();
            }

            int result = 0;
            if (nextAddress != ILLEGAL_ADDRESS)
            {
                final long currentAddress = nextAddress;

                // read buffer with only complete events
                final long opResult = logStorage.read(ioBuffer, currentAddress, readResultProcessor);
                if (opResult == OP_RESULT_INVALID_ADDR)
                {
                    System.err.println(String.format("Can't read from illegal address: %d", currentAddress));
                }
                else
                {
                    if (opResult == OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY)
                    {
                        increaseBufferSize();
                        result = 1;
                    }
                    else if (opResult > currentAddress)
                    {
                        tryToCreateBlockIndex(logContext, currentAddress, opResult);
                        // set next address
                        nextAddress = opResult;
                        result = 1;
                    }
                }
            }
            return result;
        }

        private void tryToCreateBlockIndex(LogContext logContext, long currentAddress, long opResult)
        {
            currentBlockSize += ioBuffer.position();
            // if block size is greater then or equals to index block size we will create an index
            if (currentBlockSize >= indexBlockSize)
            {
                final long currentBlockAddress = logContext.getCurrentBlockAddress();

                // if current block address is zero then a complete block was read at once
                // so we have to use the currentAddress which corresponds to the begin of the block
                // otherwise we use the cached block address if block was partly read
                createBlockIdx(logContext, currentBlockAddress == 0 ? currentAddress : currentBlockAddress);

                // reset buffer position and limit for reuse
                ioBuffer.clear();

                // reset cached block address
                logContext.setCurrentBlockAddress(0);
                currentBlockSize = 0;
            }
            else
            {
                // block was not filled enough
                // read next events into buffer after the current read events
                ioBuffer.clear();

                // cache address of block begin
                if (logContext.getCurrentBlockAddress() == 0)
                {
                    logContext.setCurrentBlockAddress(currentAddress);
                    logContext.setLastPosition(getPosition(buffer, 0));
                }
            }
        }

        private void increaseBufferSize()
        {
            // increase buffer and try again
            bufferSize *= 2;
            ioBuffer = ByteBuffer.allocateDirect(bufferSize);
            buffer.wrap(ioBuffer);
        }

        private void createBlockIdx(LogContext logContext, long addressOfFirstEventInBlock)
        {
            // wrap buffer to access first event
            buffer.wrap(ioBuffer);

            // write block IDX
            final long contextPosition = logContext.getLastPosition();
            final long position = contextPosition == 0
                ? getPosition(buffer, 0)
                : contextPosition;
            blockIndex.addBlock(position, addressOfFirstEventInBlock);

            // check if snapshot should be created
            if (snapshotPolicy.apply(position))
            {
                logContext.setLastPosition(position);
                logContext.take(TRANSITION_SNAPSHOT);
            }
            else
            {
                logContext.setLastPosition(0);
            }
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

                snapshotWriter = snapshotStorage.createSnapshot(name, logContext.getLastPosition());

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
                logContext.setLastPosition(0);
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
        return this.readResultProcessor.getCommitPosition();
    }

    public void setCommitPosition(long commitPosition)
    {
        readResultProcessor.setCommitPosition(commitPosition);
    }

    public CompletableFuture<Void> truncate(long position)
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        getStateMachine().addCommand(LogContext ->
        {

            final boolean possibleToTakeTransition = LogContext.tryTake(TRANSITION_TRUNCATE);
            if (possibleToTakeTransition)
            {
                truncatePosition = position;
                truncateFuture = future;
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot truncate log stream. State is not open."));
            }
        });

        return future;
    }

    private class TruncateState implements State<LogContext>
    {

        public static final String EXCEPTION_MSG_TRUNCATE_FAILED = "Truncation failed! Position %d was not found.";

        @Override
        public int doWork(LogContext logContext) throws Exception
        {
            logContext.reset();
            int transition = TRANSITION_DEFAULT;
            try
            {
                long truncateAddress = blockIndex.size() > 0
                    ? blockIndex.lookupBlockAddress(truncatePosition)
                    : logStorage.getFirstBlockAddress();

                // find event with given position to calculate address
                truncateAddress = readTillTruncatePosition(truncateAddress);

                // truncate
                transition = truncate(logContext, truncateAddress);
            }
            finally
            {
                truncatePosition = 0;
                truncateFuture.complete(null);
                truncateFuture = null;
                logContext.take(transition);
            }
            return 0;
        }

        private long readTillTruncatePosition(long truncateAddress)
        {
            long currentAddress = truncateAddress;
            boolean foundPosition = false;

            while (currentAddress > 0 && !foundPosition)
            {
                ioBuffer.clear();
                currentAddress = logStorage.read(ioBuffer, currentAddress);

                int remainingBytes = ioBuffer.position();
                int position = 0;
                buffer.wrap(ioBuffer);

                while (remainingBytes >= POSITION_LENGTH)
                {
                    final int messageLength = getFragmentLength(buffer, position);
                    final long loggedEventPosition = getPosition(buffer, position);
                    if (loggedEventPosition == truncatePosition)
                    {
                        foundPosition = true;
                        currentAddress -= remainingBytes;
                        remainingBytes = 0;
                    }
                    else if (messageLength <= remainingBytes)
                    {
                        remainingBytes -= messageLength;
                        position += messageLength;
                    }
                    else
                    {
                        currentAddress -= remainingBytes;
                        remainingBytes = 0;
                    }
                }

                if (remainingBytes < POSITION_LENGTH)
                {
                    currentAddress -= remainingBytes;
                }
            }

            if (!foundPosition)
            {
                currentAddress = ILLEGAL_ADDRESS;
            }
            return currentAddress;
        }

        private int truncate(LogContext logContext, long truncateAddress)
        {
            int transition = TRANSITION_DEFAULT;
            if (truncateAddress != ILLEGAL_ADDRESS)
            {
                blockIndex.truncate(truncatePosition);
                logStorage.truncate(truncateAddress);

                // write new snapshot
                final int lastIdx = blockIndex.size() - 1;
                if (lastIdx >= 0)
                {
                    final long lastBlockPosition = blockIndex.getLogPosition(lastIdx);

                    logContext.setLastPosition(lastBlockPosition);
                    transition = TRANSITION_SNAPSHOT;
                }
                else
                {
                    // if all blocks are deleted we need to clean up the snapshots as well
                    snapshotStorage.purgeSnapshot(name);
                }
                nextAddress = truncateAddress;
            }
            else
            {
                truncateFuture.completeExceptionally(new IllegalArgumentException(String.format(EXCEPTION_MSG_TRUNCATE_FAILED, truncatePosition)));
            }
            return transition;
        }
    }
}
