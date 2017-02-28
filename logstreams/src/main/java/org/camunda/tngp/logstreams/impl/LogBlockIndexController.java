package org.camunda.tngp.logstreams.impl;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.log.LoggedEventImpl;
import org.camunda.tngp.logstreams.spi.*;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;

import java.nio.ByteBuffer;

import static org.camunda.tngp.logstreams.spi.LogStorage.*;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class LogBlockIndexController extends LogController
{
    protected static final int TRANSITION_SNAPSHOT = 3;

    // STATES /////////////////////////////////////////////////////////

    protected final OpeningState openingState = new OpeningState();
    protected final OpenState openState = new OpenState();
    protected final SnapshottingState snapshottingState = new SnapshottingState();
    protected final ClosingState closingState = new ClosingState();
    protected final ClosedState closedState = new ClosedState();

    protected final StateMachineAgent<Context> stateMachine = new StateMachineAgent<>(
        StateMachine.<Context>builder(s -> new Context(s))
            .initialState(closedState)
            .from(openingState).take(TRANSITION_DEFAULT).to(openState)
            .from(openState).take(TRANSITION_SNAPSHOT).to(snapshottingState)
            .from(openState).take(TRANSITION_CLOSE).to(closingState)
            .from(snapshottingState).take(TRANSITION_DEFAULT).to(openState)
            .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
            .from(closedState).take(TRANSITION_OPEN).to(openingState)
            .build()
    );

    //  MANDATORY //////////////////////////////////////////////////

    protected final int indexBlockSize;
    protected final SnapshotStorage snapshotStorage;
    protected final SnapshotPolicy snapshotPolicy;
    protected final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
    protected final ReadResultProcessor readResultProcessor = new CompleteEventsInBlockProcessor();

    // INTERNAL ///////////////////////////////////////////////////

    protected long nextAddress = -1;
    protected int bufferSize;
    protected ByteBuffer ioBuffer;

    public LogBlockIndexController(LogBlockIndexControllerBuilder logBlockIndexControllerBuilder)
    {
        super(logBlockIndexControllerBuilder);
        this.indexBlockSize = logBlockIndexControllerBuilder.getIndexBlockSize();
        this.snapshotStorage = logBlockIndexControllerBuilder.getSnapshotStorage();
        this.snapshotPolicy = logBlockIndexControllerBuilder.getSnapshotPolicy();
        this.bufferSize = indexBlockSize;
        ioBuffer = ByteBuffer.allocate(bufferSize);
        buffer.wrap(ioBuffer);
    }

    public interface LogBlockIndexControllerBuilder extends LogControllerBuilder
    {
        int getIndexBlockSize();

        SnapshotStorage getSnapshotStorage();

        SnapshotPolicy getSnapshotPolicy();
    }

    @Override
    protected StateMachineAgent<Context> getStateMachine()
    {
        return stateMachine;
    }


    protected class OpeningState implements TransitionState<LogController.Context>
    {
        @Override
        public void work(LogController.Context context)
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
                // snasphot could not been read - so we start with the first block
                nextAddress = logStorage.getFirstBlockAddress();
            }
            finally
            {
                context.take(TRANSITION_DEFAULT);
                openFuture.complete(null);
                openFuture = null;
            }
        }

        protected void recoverBlockIndex() throws Exception
        {
            final long recoveredAddress = logStorage.getFirstBlockAddress();
            final ReadableSnapshot lastSnapshot = snapshotStorage.getLastSnapshot(name);
            if (lastSnapshot != null)
            {
                lastSnapshot.recoverFromSnapshot(blockIndex);
                nextAddress = Math.max(blockIndex.lookupBlockAddress(lastSnapshot.getPosition()), recoveredAddress);
            }
            else
            {
                nextAddress = recoveredAddress;
            }
        }

    }

    protected class OpenState implements State<LogStreamController.Context>
    {

        @Override
        public int doWork(Context context)
        {
            // open state
            if (nextAddress == -1)
            {
                nextAddress = logStorage.getFirstBlockAddress();
            }

            if (nextAddress == -1)
            {
                return 0;
            }

            final long currentAddress = nextAddress;

            // read buffer with only complete events
            final long opResult = logStorage.read(ioBuffer, currentAddress, readResultProcessor);
            if (opResult == OP_RESULT_NO_DATA)
            {
                return 0;
            }
            else if (opResult == OP_RESULT_INVALID_ADDR)
            {
                throw new IllegalStateException(String.format("Can't read from illegal address: %d", currentAddress));
            }
            else if (opResult == OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY)
            {
                increaseBufferSize();
                return 1;
            }
            else
            {
                tryToCreateBlockIndex(context, currentAddress, opResult);
                return 1;
            }
        }

        private void tryToCreateBlockIndex(Context context, long currentAddress, long opResult)
        {
            // the block size is equal to the position in the buffer
            final int blockSize = ioBuffer.position();

            // if remaining block size is less then current block size we will create an index
            // this means will create indices after the block is half full
            final int remainingIndexBlockSize = indexBlockSize - blockSize;
            if (blockSize >= remainingIndexBlockSize)
            {
                final long currentBlockAddress = context.getCurrentBlockAddress();

                // if current block address is zero then a complete block was read at once
                // so we have to use the currentAddress which corresponds to the begin of the block
                // otherwise we use the cached block address if block was partly read
                createBlockIdx(context, currentBlockAddress == 0 ? currentAddress : currentBlockAddress);

                // reset buffer position and limit for reuse
                ioBuffer.clear();

                // reset cached block address
                context.setCurrentBlockAddress(0);
            }
            else
            {
                // block was not filled enough
                // read next events into buffer after the current read events
                ioBuffer.position(blockSize);
                ioBuffer.limit(indexBlockSize);

                // cache address of block begin
                if (context.getCurrentBlockAddress() == 0)
                {
                    context.setCurrentBlockAddress(currentAddress);
                }
            }
            // set next address
            nextAddress = opResult;
        }

        private void increaseBufferSize()
        {
            // increase buffer and try again
            bufferSize *= 2;
            final int pos = ioBuffer.position();
            final ByteBuffer newBuffer = ByteBuffer.allocateDirect(bufferSize);

            if (pos > 0)
            {
                // copy remaining data
                ioBuffer.flip();
                newBuffer.put(ioBuffer);
            }

            newBuffer.limit(newBuffer.capacity());
            newBuffer.position(pos);

            ioBuffer = newBuffer;
            buffer.wrap(ioBuffer);
        }

        private void createBlockIdx(Context context, long addressOfFirstEventInBlock)
        {
            // wrap buffer to access first event
            final LoggedEventImpl firstEventInBlock = new LoggedEventImpl();
            buffer.wrap(ioBuffer);
            firstEventInBlock.wrap(buffer, 0);

            // write block IDX
            final long position = firstEventInBlock.getPosition();
            blockIndex.addBlock(position, addressOfFirstEventInBlock);

            // check if snapshot should be created
            if (snapshotPolicy.apply(position))
            {
                context.setLastPosition(position);
                context.take(TRANSITION_SNAPSHOT);
            }
        }
    }

    protected class ClosingState implements TransitionState<LogStreamController.Context>
    {
        @Override
        public void work(Context context)
        {
            context.take(TRANSITION_DEFAULT);
        }

    }

    protected class SnapshottingState implements TransitionState<Context>
    {

        @Override
        public void work(Context context)
        {
            SnapshotWriter snapshotWriter = null;
            try
            {
                // should do recovery if fails to flush because of corrupted block index - see #8

                // flush the log to ensure that the snapshot doesn't contains indexes of unwritten events
                logStorage.flush();

                snapshotWriter = snapshotStorage.createSnapshot(name, context.getLastPosition());

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
                // regardless whether the writing of the snapshot was successful or not we go to the open state
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    public boolean isClosed()
    {
        return stateMachine.getCurrentState() == closedState;
    }

    public boolean isOpen()
    {
        return stateMachine.getCurrentState() == openState;
    }

    public long getNextAddress()
    {
        return nextAddress;
    }

    public int getIndexBlockSize()
    {
        return indexBlockSize;
    }
}
