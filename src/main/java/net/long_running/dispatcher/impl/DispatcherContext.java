package net.long_running.dispatcher.impl;

import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class DispatcherContext
{

    protected ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> dispatcherCommandQueue;
    protected AgentRunner agentRunner;

    public void init()
    {
        dispatcherCommandQueue = new ManyToOneConcurrentArrayQueue<>(100);
    }

    public ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> getDispatcherCommandQueue()
    {
        return dispatcherCommandQueue;
    }

    public void close()
    {
        agentRunner.close();
    }

    public void setAgentRunner(AgentRunner agentRunner)
    {
        this.agentRunner = agentRunner;
    }
}
