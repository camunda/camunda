package org.camunda.tngp.log;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.appender.SegmentAllocationDescriptor;

import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class LogContext
{
    protected OneToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue = new OneToOneConcurrentArrayQueue<>(10);

    protected SegmentAllocationDescriptor logAllocationDescriptor;

    protected Dispatcher writeBuffer;

    protected AgentRunner[] agentRunners;

    protected int initialLogFragmentId = 0;

    public OneToOneConcurrentArrayQueue<LogConductorCmd> getLogConductorCmdQueue()
    {
        return logConductorCmdQueue;
    }

    public SegmentAllocationDescriptor getLogAllocationDescriptor()
    {
        return logAllocationDescriptor;
    }

    public void setLogAllocationDescriptor(SegmentAllocationDescriptor logAllocationDescriptor)
    {
        this.logAllocationDescriptor = logAllocationDescriptor;
    }

    public Dispatcher getWriteBuffer()
    {
        return writeBuffer;
    }

    public void setWriteBuffer(Dispatcher writeBuffer)
    {
        this.writeBuffer = writeBuffer;
    }

    public void setAgentRunners(AgentRunner[] agentRunners)
    {
        this.agentRunners = agentRunners;
    }

    public AgentRunner[] getAgentRunners()
    {
        return agentRunners;
    }

    public int getInitialLogSegementId()
    {
        return initialLogFragmentId;
    }

    public void setInitialLogFragmentId(int initialLogFragmentId)
    {
        this.initialLogFragmentId = initialLogFragmentId;
    }

}
