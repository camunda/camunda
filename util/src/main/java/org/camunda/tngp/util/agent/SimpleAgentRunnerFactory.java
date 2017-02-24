package org.camunda.tngp.util.agent;

import java.util.concurrent.TimeUnit;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

public class SimpleAgentRunnerFactory implements AgentRunnerFactory
{
    protected static final ErrorHandler DEFAULT_ERROR_HANDLER = (t) ->
    {
        t.printStackTrace();
    };

    @Override
    public AgentRunner createAgentRunner(Agent agent)
    {
        final IdleStrategy idleStrategy = new BackoffIdleStrategy(1000, 100, 100, TimeUnit.MILLISECONDS.toNanos(10));

        return new AgentRunner(idleStrategy, DEFAULT_ERROR_HANDLER, null, agent);
    }
}
