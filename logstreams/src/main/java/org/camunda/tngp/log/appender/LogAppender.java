package org.camunda.tngp.log.appender;

import java.util.function.Consumer;

import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.LogContext;
import org.camunda.tngp.log.conductor.LogConductorCmd;
import org.camunda.tngp.log.fs.AppendableLogSegment;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class LogAppender implements Agent, Consumer<LogAppenderCmd>
{
    private static final String NAME = "log-appender";

    protected final LogSegmentAllocationDescriptor logAllocationDescriptor;

    protected final OneToOneConcurrentArrayQueue<LogConductorCmd> toLogConductorCmdQueue;

    protected final OneToOneConcurrentArrayQueue<LogAppenderCmd> cmdQueue;

    protected final Dispatcher writeBuffer;

    protected final BlockPeek blockPeek;

    protected AppendableLogSegment currentSegment;
    protected AppendableLogSegment nextSegment;

    protected int maxAppendSize = 1024 * 1024;

    public LogAppender(LogContext context)
    {
        logAllocationDescriptor = context.getLogAllocationDescriptor();
        toLogConductorCmdQueue = context.getLogConductorCmdQueue();
        cmdQueue = context.getAppenderCmdQueue();
        writeBuffer = context.getWriteBuffer();
        blockPeek = new BlockPeek();
    }

    @Override
    public String roleName()
    {
        return NAME;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(this);

        if(currentSegment != null)
        {
            workCount += doAppend();
        }

        return workCount;
    }

    protected int doAppend()
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
        if(nextSegment != null)
        {
            final AppendableLogSegment filledSegement = currentSegment;
            toLogConductorCmdQueue.add((c) ->
            {
                c.closeSegment(filledSegement);
            });

            currentSegment = nextSegment;

            allocateNextSegment();
        }
    }

    public void init(AppendableLogSegment appendableLogSegment)
    {
        currentSegment = appendableLogSegment;
        allocateNextSegment();
    }

    protected void allocateNextSegment()
    {
        final int nextSegmentId = 1 + currentSegment.getSegmentId();

        toLogConductorCmdQueue.add((c) ->
        {
           c.allocate(nextSegmentId);
        });
    }

    public void onNextSegmentAllocated(AppendableLogSegment segment)
    {
        nextSegment = segment;
    }

    public void onNextSegmentAllocationFailed()
    {
        nextSegment = null;
    }

    @Override
    public void accept(LogAppenderCmd t)
    {
        t.execute(this);
    }

    @Override
    public void onClose()
    {
        if(currentSegment != null)
        {
            currentSegment.closeSegment();
        }
        if(nextSegment != null)
        {
            nextSegment.closeSegment();
            nextSegment.delete();
        }
    }

}
