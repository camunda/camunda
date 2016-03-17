package org.camunda.tngp.log;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.appender.LogAllocationDescriptor;

import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class LogContext
{

    protected OneToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue = new OneToOneConcurrentArrayQueue<>(10);

    protected LogAllocationDescriptor logAllocationDescriptor;

    protected Dispatcher writeBuffer;

    protected AgentRunner[] agentRunners;

    protected int initialLogFragmentId = 0;

    public OneToOneConcurrentArrayQueue<LogConductorCmd> getLogConductorCmdQueue()
    {
        return logConductorCmdQueue;
    }

    public LogAllocationDescriptor getLogAllocationDescriptor()
    {
        return logAllocationDescriptor;
    }

    public void setLogAllocationDescriptor(LogAllocationDescriptor logAllocationDescriptor)
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

    public int getInitialLogFragmentId()
    {
        return initialLogFragmentId;
    }

    public void setInitialLogFragmentId(int initialLogFragmentId)
    {
        this.initialLogFragmentId = initialLogFragmentId;
    }
}
