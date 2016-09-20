package org.camunda.tngp.log;

import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.log.appender.LogAppenderCmd;
import org.camunda.tngp.log.conductor.LogConductorCmd;

public class LogAgentContext
{
    protected ManyToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue = new ManyToOneConcurrentArrayQueue<>(10);

    protected ManyToOneConcurrentArrayQueue<LogAppenderCmd> appenderCmdQueue = new ManyToOneConcurrentArrayQueue<>(10);

    protected Dispatcher writeBuffer;

    protected Subscription appenderSubscription;

    protected AgentRunner[] agentRunners;

    protected boolean isWriteBufferExternallyManaged = false;

    public ManyToOneConcurrentArrayQueue<LogConductorCmd> getLogConductorCmdQueue()
    {
        return logConductorCmdQueue;
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

    public ManyToOneConcurrentArrayQueue<LogAppenderCmd> getAppenderCmdQueue()
    {
        return appenderCmdQueue;
    }

    public void setAppenderCmdQueue(ManyToOneConcurrentArrayQueue<LogAppenderCmd> appenderCmdQueue)
    {
        this.appenderCmdQueue = appenderCmdQueue;
    }

    public Subscription getAppenderSubscription()
    {
        return appenderSubscription;
    }

    public void setAppenderSubscription(Subscription appenderSubscription)
    {
        this.appenderSubscription = appenderSubscription;
    }

    public void setWriteBufferExternallyManaged(boolean isWriteBufferExternallyManaged)
    {
        this.isWriteBufferExternallyManaged = isWriteBufferExternallyManaged;
    }

    public boolean isWriteBufferExternallyManaged()
    {
        return isWriteBufferExternallyManaged;
    }

}
