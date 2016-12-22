package org.camunda.tngp.util.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.camunda.tngp.util.EnsureUtil;

/**
 * {@link AgentRunnerService} which distributes the agents on a given number of threads.
 */
public class SharedAgentRunnerService implements AgentRunnerService
{
    protected static final Comparator<AgentGroup> GROUP_SIZE_COMPARATOR = Comparator.comparingInt(group -> group.size());

    protected final int threadCount;
    protected final List<AgentGroup> agentGroups = new ArrayList<>();
    protected final List<AgentRunner> agentRunners = new ArrayList<>();

    public SharedAgentRunnerService(AgentRunnerFactory agentRunnerFactory, String name)
    {
        this(agentRunnerFactory, name, 1);
    }

    public SharedAgentRunnerService(AgentRunnerFactory agentRunnerFactory, String name, int threadCount)
    {
        EnsureUtil.ensureGreaterThan("thread count", threadCount, 0);

        this.threadCount = threadCount;

        for (int i = 0; i < threadCount; i++)
        {
            final AgentGroup group = new AgentGroup(name + "-" + i);
            final AgentRunner runner = agentRunnerFactory.createAgentRunner(group);

            agentGroups.add(group);
            agentRunners.add(runner);
        }

        agentRunners.forEach(AgentRunner::startOnThread);
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

        final AgentGroup smallestGroup = agentGroups.stream().min(GROUP_SIZE_COMPARATOR).get();

        smallestGroup.addAgent(agent);
    }

    @Override
    public void remove(Agent agent)
    {
        Objects.requireNonNull(agent, "Agent cannot be null.");

        agentGroups.stream().forEach(group -> group.removeAgent(agent));
    }

}
