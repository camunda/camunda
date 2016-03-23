package org.camunda.tngp.log;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.appender.LogAppenderCmd;
import org.camunda.tngp.log.appender.LogSegmentAllocationDescriptor;
import org.camunda.tngp.log.conductor.LogConductorCmd;
import org.camunda.tngp.log.fs.LogSegments;

import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class LogContext
{
    protected OneToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue = new OneToOneConcurrentArrayQueue<>(10);

    protected OneToOneConcurrentArrayQueue<LogAppenderCmd> appenderCmdQueue = new OneToOneConcurrentArrayQueue<>(10);

    protected LogSegmentAllocationDescriptor logAllocationDescriptor;

    protected Dispatcher writeBuffer;

    protected AgentRunner[] agentRunners;

    protected LogSegments logSegments = new LogSegments();

    public OneToOneConcurrentArrayQueue<LogConductorCmd> getLogConductorCmdQueue()
    {
        return logConductorCmdQueue;
    }

    public LogSegmentAllocationDescriptor getLogAllocationDescriptor()
    {
        return logAllocationDescriptor;
    }

    public void setLogAllocationDescriptor(LogSegmentAllocationDescriptor logAllocationDescriptor)
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

    public LogSegments getLogSegments()
    {
        return logSegments;
    }

    public void setAvailableSegments(LogSegments availableSegments)
    {
        this.logSegments = availableSegments;
    }

    public OneToOneConcurrentArrayQueue<LogAppenderCmd> getAppenderCmdQueue()
    {
        return appenderCmdQueue;
    }

    public void setAppenderCmdQueue(OneToOneConcurrentArrayQueue<LogAppenderCmd> appenderCmdQueue)
    {
        this.appenderCmdQueue = appenderCmdQueue;
    }
}
