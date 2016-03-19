package org.camunda.tngp.log.appender;

import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.LogConductorCmd;
import org.camunda.tngp.log.LogContext;
import org.camunda.tngp.log.fs.AppendableLogSegment;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class Appender implements Agent
{
    protected final SegmentAllocationDescriptor logAllocationDescriptor;

    protected final OneToOneConcurrentArrayQueue<LogConductorCmd> toLogConductorCmdQueue;

    protected final Dispatcher writeBuffer;

    protected final BlockPeek blockPeek;

    protected AppendableLogSegment currentSegment;

    protected int maxAppendSize = 1024 * 1024;

    public Appender(LogContext context)
    {
        logAllocationDescriptor = context.getLogAllocationDescriptor();
        toLogConductorCmdQueue = context.getLogConductorCmdQueue();
        writeBuffer = context.getWriteBuffer();
        blockPeek = new BlockPeek();

        openOrAllocateInitialSegment(context.getInitialLogSegementId());
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += append();

        return workCount;
    }

    protected int append()
    {
        final int bytesAvailable = writeBuffer.peekBlock(0, blockPeek, maxAppendSize, false);

        int bytesWritten = 0;

        if(bytesAvailable > 0)
        {
            int newTail = currentSegment.append(blockPeek.getBuffer());

            if(newTail == -2)
            {
                onSegmentFilled();
            }
            else if(newTail > 0)
            {
                blockPeek.markCompleted();
                bytesWritten = bytesAvailable;
            }
            else
            {
                blockPeek.markFailed();
            }
        }

        return bytesWritten;
    }

    protected void onSegmentFilled()
    {
        final int nextSegmentId = 1 + currentSegment.getSegmentId();
        final String nextSegmentFileName = logAllocationDescriptor.fileName(nextSegmentId);
        final AppendableLogSegment nextSegment = new AppendableLogSegment(nextSegmentFileName);

        if(nextSegment.allocate(nextSegmentId, logAllocationDescriptor.getSegmentSize()))
        {
            currentSegment.closeSegment();
            currentSegment = nextSegment;
        }
    }

    protected boolean openOrAllocateInitialSegment(final int initalSegmentId)
    {
        final String fileName = logAllocationDescriptor.fileName(initalSegmentId);
        final AppendableLogSegment segment = new AppendableLogSegment(fileName);

        boolean success = false;

        if(segment.openSegment(false) || segment.allocate(initalSegmentId, logAllocationDescriptor.getSegmentSize()))
        {
            currentSegment = segment;
        }

        return success;
    }

    @Override
    public String roleName()
    {
        return "log-appender";
    }

}
