package org.camunda.tngp.log.appender;

import static org.camunda.tngp.log.appender.AppendableLogFragment.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.BlockHandler;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.LogConductorCmd;
import org.camunda.tngp.log.LogContext;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class LogAppender implements Agent, BlockHandler
{
    protected final LogFragementAppender logFragmentAppender;

    protected final LogAllocationDescriptor logAllocationDescriptor;

    protected final OneToOneConcurrentArrayQueue<LogConductorCmd> toLogConductorCmdQueue;

    protected final Dispatcher writeBuffer;

    protected AppendableLogFragment currentFragment;

    protected AppendableLogFragment nextFragment;

    public LogAppender(LogContext context)
    {
        logFragmentAppender = new LogFragementAppender();
        logAllocationDescriptor = context.getLogAllocationDescriptor();
        toLogConductorCmdQueue = context.getLogConductorCmdQueue();
        writeBuffer = context.getWriteBuffer();

        initInitialFragments(context.getInitialLogFragmentId());
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += writeBuffer.pollBlock(this, Integer.MAX_VALUE);

        return workCount;
    }

    @Override
    public void onBlockAvailable(ByteBuffer buffer, int blockOffset, int blockLength, int streamId, long blockPosition)
    {
        long newTail = -1;

        try
        {
            newTail = -2;

            while(newTail == -2)
            {
                buffer.limit(blockOffset + blockLength);
                buffer.position(blockOffset);
                newTail = appendBlock(buffer);
            }

        }
        catch(Exception e)
        {
            // TODO
            e.printStackTrace();
        }

    }

    public long appendBlock(ByteBuffer block)
    {
        int newTail = logFragmentAppender.append(currentFragment, block);

        if(newTail == -1)
        {
            newTail = onLogFragementFilled(currentFragment);
        }
        else
        {
            currentFragment.setTailPosition(currentFragment.getTailPosition() + newTail);
        }

        return newTail;
    }

    protected int onLogFragementFilled(AppendableLogFragment filledFragment)
    {
        filledFragment.setStateVolatile(STATE_FILLED);

        final int nextFragmentState = nextFragment.getStateVolatile();
        final int nextNextFragmentId = 1 + nextFragment.getFragmentId();

        int newTail = -1;

        if(nextFragmentState == STATE_ALLOCATED)
        {
            // move to next fragment
            currentFragment = nextFragment;
            nextFragment.setStateVolatile(STATE_ACTIVE);

            // request allocation of next next fragment from conductor
            nextFragment = new AppendableLogFragment(nextNextFragmentId, logAllocationDescriptor);
            requestAllocation(nextFragment);

            newTail = -2;
        }
        else if(nextFragmentState == STATE_NEW)
        {
            // still allocating
            newTail = -2;
        }

        return newTail;
    }

    protected void initInitialFragments(final int initalFragementId)
    {
        final int nextFragmentId = 1 + initalFragementId;

        currentFragment = new AppendableLogFragment(initalFragementId, logAllocationDescriptor);
        // allocate in this thread
        currentFragment.allocate();

        nextFragment = new AppendableLogFragment(nextFragmentId, logAllocationDescriptor);
        requestAllocation(nextFragment);
    }

    protected void requestAllocation(AppendableLogFragment logFragement)
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
