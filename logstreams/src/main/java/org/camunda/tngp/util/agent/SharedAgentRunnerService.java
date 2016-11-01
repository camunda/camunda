package org.camunda.tngp.util.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;

public class SharedAgentRunnerService implements AgentRunnerService
{
    protected final int threadCount;
    protected final AgentRunnerFactory agentRunnerFactory;
    protected final List<AgentGroup> agentGroups = new ArrayList<>();
    protected final List<AgentRunner> agentRunners = new ArrayList<>();

    public SharedAgentRunnerService(String nameTemplate, int threadCount, AgentRunnerFactory agentRunnerFactory)
    {
        this.threadCount = threadCount;
        this.agentRunnerFactory = agentRunnerFactory;

        for (int i = 0; i < threadCount; i++)
        {
            final AgentGroup group = new AgentGroup(String.format(nameTemplate, i));
            final AgentRunner runner = agentRunnerFactory.createAgentRunner(group);
            AgentRunner.startOnThread(runner);

            agentGroups.add(group);
            agentRunners.add(runner);
        }
    }

    @Override
    public void close() throws Exception
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

        agentGroups.clear();
    }

    @Override
    public void run(Agent agent)
    {
        Objects.requireNonNull(agent, "Agent cannot be null.");

        final int groupId = agent.hashCode() % threadCount;
        final AgentGroup group = agentGroups.get(groupId);

        group.addAgent(agent);
    }

    @Override
    public void remove(Agent agent)
    {
        Objects.requireNonNull(agent, "Agent cannot be null.");

        final int groupId = agent.hashCode() % threadCount;
        final AgentGroup group = agentGroups.get(groupId);

        group.removeAgent(agent);
    }

}
