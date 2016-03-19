package org.camunda.tngp.log;

import java.util.function.Consumer;

import org.camunda.tngp.log.appender.AppendableSegment;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class LogConductor implements Agent, Consumer<LogConductorCmd>
{
    protected final OneToOneConcurrentArrayQueue<LogConductorCmd> cmdQueue;

    public LogConductor(LogContext logContext)
    {
        cmdQueue = logContext.getLogConductorCmdQueue();
    }

    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(this);

        return workCount;
    }

    public String roleName()
    {
        return "log conductor";
    }

    @Override
    public void accept(LogConductorCmd c)
    {
        c.execute(this);
    }

    public void allocateFragment(AppendableSegment nextFragment)
    {
        nextFragment.allocate();
    }

}
