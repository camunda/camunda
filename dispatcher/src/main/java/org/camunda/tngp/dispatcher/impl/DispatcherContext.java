package org.camunda.tngp.dispatcher.impl;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class DispatcherContext
{
    protected ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> dispatcherCommandQueue = new ManyToOneConcurrentArrayQueue<>(100);

    protected AgentRunner agentRunner;
    protected Agent conductorAgent;

    public void close()
    {
        if (agentRunner != null)
        {
            agentRunner.close();
        }
    }

    public ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> getDispatcherCommandQueue()
    {
        return dispatcherCommandQueue;
    }

    public void setAgentRunner(AgentRunner agentRunner)
    {
        this.agentRunner = agentRunner;
    }

    public Agent getConductorAgent()
    {
        return conductorAgent;
    }

    public void setConductorAgent(Agent conductorAgent)
    {
        this.conductorAgent = conductorAgent;
    }
}
