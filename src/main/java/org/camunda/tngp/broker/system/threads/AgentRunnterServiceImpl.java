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

    protected final AgentGroup ioAgents = new AgentGroup("io");
    protected final AgentGroup conductorAgents = new AgentGroup("conductor");
    protected final AgentGroup workerAgents = new AgentGroup("workers");

    protected final List<AgentRunner> agentRunners = new ArrayList<>();


    public AgentRunnterServiceImpl(ConfigurationManager configurationManager)
    {
        ThreadingCfg cfg = configurationManager.readEntry("threading", ThreadingCfg.class);

        int numberOfThreads = cfg.numberOfThreads;

        if(numberOfThreads > maxThreadCount)
        {
            System.err.println("WARNING: configured thread count ("+numberOfThreads+") is larger than maxThreadCount "+maxThreadCount+"). Falling back max thread count.");
            numberOfThreads = maxThreadCount;
        }

        int availableThreads = numberOfThreads;

        // TODO: implement this in a better way !!!

        if(availableThreads >= 3)
        {
            agentRunners.add(createAgentRunner(ioAgents));
            agentRunners.add(createAgentRunner(workerAgents));
            agentRunners.add(createAgentRunner(conductorAgents));
        }
        else if(availableThreads == 2)
        {
            agentRunners.add(createAgentRunner(ioAgents));
            agentRunners.add(createAgentRunner(new CompositeAgent(conductorAgents, workerAgents)));
        }
        else
        {
            agentRunners.add(createAgentRunner(new CompositeAgent(ioAgents, conductorAgents, workerAgents)));
        }

    }

    private AgentRunner createAgentRunner(Agent agent)
    {
        BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(200));
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
    public void runIoAgent(Agent agent)
    {
        ioAgents.addAgent(agent);
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
    public void removeIoAgent(Agent agent)
    {
        ioAgents.removeAgent(agent);
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
