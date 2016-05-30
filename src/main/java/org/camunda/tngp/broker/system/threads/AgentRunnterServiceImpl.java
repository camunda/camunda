package org.camunda.tngp.broker.system.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.threads.cfg.ThreadingCfg;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.BackoffIdleStrategy;
import uk.co.real_logic.agrona.concurrent.CompositeAgent;

public class AgentRunnterServiceImpl implements AgentRunnerService, Service<AgentRunnerService>
{
    static int maxThreadCount = Runtime.getRuntime().availableProcessors() + 1;

    protected final AgentGroup logAgents = new AgentGroup("log");
    protected final AgentGroup networkingAgents = new AgentGroup("networking");
    protected final AgentGroup conductorAgents = new AgentGroup("conductor");
    protected final AgentGroup workerAgents = new AgentGroup("workers");

    protected final List<AgentRunner> agentRunners = new ArrayList<>();


    public AgentRunnterServiceImpl(ConfigurationManager configurationManager)
    {
        final ThreadingCfg cfg = configurationManager.readEntry("threading", ThreadingCfg.class);

        int numberOfThreads = cfg.numberOfThreads;

        if(numberOfThreads > maxThreadCount)
        {
            System.err.println("WARNING: configured thread count ("+numberOfThreads+") is larger than maxThreadCount "+maxThreadCount+"). Falling back max thread count.");
            numberOfThreads = maxThreadCount;
        }

        final int availableThreads = numberOfThreads;

        // TODO: implement this in a better way !!!

        if(availableThreads >= 4)
        {
            agentRunners.add(createAgentRunner(logAgents));
            agentRunners.add(createAgentRunner(networkingAgents));
            agentRunners.add(createAgentRunner(workerAgents));
            agentRunners.add(createAgentRunner(networkingAgents));
        }
        else if(availableThreads == 3)
        {
            agentRunners.add(createAgentRunner(logAgents));
            agentRunners.add(createAgentRunner(networkingAgents));
            agentRunners.add(createAgentRunner(new CompositeAgent(conductorAgents, workerAgents)));
        }
        else if(availableThreads == 2)
        {
            agentRunners.add(createAgentRunner(new CompositeAgent(networkingAgents, conductorAgents, workerAgents)));
            agentRunners.add(createAgentRunner(logAgents));
        }
        else
        {
            agentRunners.add(createAgentRunner(new CompositeAgent(networkingAgents, logAgents, conductorAgents, workerAgents)));
        }

    }

    private AgentRunner createAgentRunner(Agent agent)
    {
        final BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(100));
        return new AgentRunner(idleStrategy, (t)-> t.printStackTrace(), null, agent);
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        for (AgentRunner agentRunner : agentRunners)
        {
            AgentRunner.startOnThread(agentRunner);
        }
    }

    @Override
    public void stop()
    {
        for (AgentRunner agentRunner : agentRunners)
        {
            agentRunner.close();
        }
    }

    @Override
    public void runConductorAgent(Agent agent)
    {
        conductorAgents.addAgent(agent);
    }

    @Override
    public void runNetworkingAgent(Agent agent)
    {
        networkingAgents.addAgent(agent);
    }

    @Override
    public void runLogAgent(Agent agent)
    {
        logAgents.addAgent(agent);
    }

    @Override
    public void runWorkerAgent(Agent agent)
    {
        workerAgents.addAgent(agent);
    }

    @Override
    public void removeConductorAgent(Agent agent)
    {
        conductorAgents.removeAgent(agent);
    }

    @Override
    public void removeNetworkingAgent(Agent agent)
    {
        networkingAgents.removeAgent(agent);
    }

    @Override
    public void removeLogAgent(Agent agent)
    {
        logAgents.removeAgent(agent);
    }

    @Override
    public void removeWorkerAgent(Agent agent)
    {
        workerAgents.removeAgent(agent);
    }

    @Override
    public AgentRunnerService get()
    {
        return this;
    }

}
