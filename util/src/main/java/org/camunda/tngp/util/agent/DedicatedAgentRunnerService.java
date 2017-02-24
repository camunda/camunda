package org.camunda.tngp.util.agent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;

/**
 * {@link AgentRunnerService} which starts each agent on a new thread.
 */
public class DedicatedAgentRunnerService implements AgentRunnerService
{
    protected final AgentRunnerFactory agentRunnerFactory;
    protected final List<AgentRunner> agentRunners = new ArrayList<>();

    public DedicatedAgentRunnerService(AgentRunnerFactory agentRunnerFactory)
    {
        this.agentRunnerFactory = agentRunnerFactory;
    }

    @Override
    public synchronized void close() throws Exception
    {
        while (!agentRunners.isEmpty())
        {
            final AgentRunner runner = agentRunners.remove(agentRunners.size() - 1);
            try
            {
                runner.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public synchronized void run(Agent agent)
    {
        final AgentRunner runner = agentRunnerFactory.createAgentRunner(agent);
        AgentRunner.startOnThread(runner);
        agentRunners.add(runner);
    }

    @Override
    public synchronized void remove(Agent agent)
    {
        final Iterator<AgentRunner> iterator = agentRunners.iterator();

        while (iterator.hasNext())
        {
            final AgentRunner runner = iterator.next();

            if (runner.agent() == agent)
            {
                iterator.remove();
                try
                {
                    runner.close();
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

}
