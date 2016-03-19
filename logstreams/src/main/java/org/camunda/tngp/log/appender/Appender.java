package org.camunda.tngp.log.appender;

import static org.camunda.tngp.log.appender.AppendableSegment.*;

import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.LogConductorCmd;
import org.camunda.tngp.log.LogContext;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

/**
 * Reads blocks from the logs write buffer and appends them
 * to the log.
 */
public class Appender implements Agent
{
    protected final SegmentAppender segmentAppender;

    protected final SegmentAllocationDescriptor logAllocationDescriptor;

    protected final OneToOneConcurrentArrayQueue<LogConductorCmd> toLogConductorCmdQueue;

    protected final Dispatcher writeBuffer;

    protected final BlockPeek blockPeek;

    protected AppendableSegment currentSegment;

    protected AppendableSegment nextSegment;

    protected int maxAppendSize = 1024 * 1024 * 16;

    int bytesWritten;

    public Appender(LogContext context)
    {
        segmentAppender = new SegmentAppender();
        logAllocationDescriptor = context.getLogAllocationDescriptor();
        toLogConductorCmdQueue = context.getLogConductorCmdQueue();
        writeBuffer = context.getWriteBuffer();
        blockPeek = new BlockPeek();

        initInitialSegments(context.getInitialLogSegementId());
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += append();

        return workCount;
    }

    private int append()
    {

        final int bytesAvailable = writeBuffer.peekBlock(0, blockPeek, maxAppendSize, false);

        if(bytesAvailable > 0)
        {

            int newTail = -1;
            do
            {
                newTail = appendBlock(blockPeek.getBuffer());
            }
            while(newTail == -2);

            if(newTail > 0)
            {
                blockPeek.markCompleted();
                bytesWritten += bytesAvailable;
            }
            else
            {
                blockPeek.markFailed();
            }
        }

        return bytesAvailable;
    }

    public int appendBlock(ByteBuffer block)
    {
        int newTail = segmentAppender.append(currentSegment, block);

        if(newTail == -2)
        {
            newTail = onLogFragementFilled(currentSegment);
        }

        if(newTail >=0)
        {
            currentSegment.setTailPosition(currentSegment.getTailPosition() + newTail);
        }

        return newTail;
    }

    protected int onLogFragementFilled(AppendableSegment filledFragment)
    {
        filledFragment.setStateVolatile(STATE_FILLED);

        final int nextFragmentState = nextSegment.getStateVolatile();
        final int nextNextFragmentId = 1 + nextSegment.getSegmentId();

        int newTail = -1;

        if(nextFragmentState == STATE_ALLOCATED)
        {
            // move to next fragment
            currentSegment = nextSegment;
            nextSegment.setStateVolatile(STATE_ACTIVE);

            // request allocation of next next fragment from conductor
            nextSegment = new AppendableSegment(nextNextFragmentId, logAllocationDescriptor);
            requestAllocation(nextSegment);

            newTail = -2;
        }
        else if(nextFragmentState == STATE_NEW)
        {
            // still allocating
            newTail = -2;
        }

        return newTail;
    }

    protected void initInitialSegments(final int initalFragementId)
    {
        final int nextFragmentId = 1 + initalFragementId;

        currentSegment = new AppendableSegment(initalFragementId, logAllocationDescriptor);
        // allocate in this thread
        currentSegment.allocate();

        nextSegment = new AppendableSegment(nextFragmentId, logAllocationDescriptor);
        requestAllocation(nextSegment);
    }

    protected void requestAllocation(AppendableSegment logFragement)
    {
        toLogConductorCmdQueue.add((c) ->
        {
           c.allocateFragment(logFragement);
        });
    }

    @Override
    public String roleName()
    {
        return "log-appender";
    }

}
