package org.camunda.tngp.log.appender;

import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.fs.AppendableLogSegment;

public class LogAppendHandler
{
    protected Log log;

    protected AppendableLogSegment currentSegment;
    protected AppendableLogSegment nextSegment;

    protected int append(BlockPeek blockPeek, LogAppender logAppender)
    {
        int bytesWritten = 0;

        int newTail = currentSegment.append(blockPeek.getBuffer());

        if(newTail == -2)
        {
            onSegmentFilled(logAppender);
        }
        else if(newTail > 0)
        {
            blockPeek.markCompleted();
            bytesWritten = blockPeek.getBlockLength();
        }
        else
        {
            blockPeek.markFailed();
        }

        return bytesWritten;
    }

    protected void onSegmentFilled(LogAppender logAppender)
    {
        if(nextSegment != null)
        {
            currentSegment.closeSegment();
            currentSegment = nextSegment;
            allocateNextSegment(logAppender);
        }
    }

    public void init(AppendableLogSegment appendableLogSegment)
    {
        currentSegment = appendableLogSegment;
    }

    public void allocateNextSegment(LogAppender logAppender)
    {
        final int nextSegmentId = 1 + currentSegment.getSegmentId();
        logAppender.requestAllocateNextSegment(log, nextSegmentId);
    }

    public void onNextSegmentAllocated(AppendableLogSegment segment)
    {
        nextSegment = segment;
    }

    public void onNextSegmentAllocationFailed()
    {
        nextSegment = null;
    }

    public void close()
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

    public void setLog(Log log)
    {
        this.log = log;
    }

}
