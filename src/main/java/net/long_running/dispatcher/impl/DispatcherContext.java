package net.long_running.dispatcher.impl;

import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class DispatcherContext
{

    protected ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> dispatcherCommandQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected AgentRunner agentRunner;

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
