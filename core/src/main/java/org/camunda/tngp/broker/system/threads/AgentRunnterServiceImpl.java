package org.camunda.tngp.broker.system.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.broker.services.Counters;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.threads.cfg.ThreadingCfg;
import org.camunda.tngp.broker.system.threads.cfg.ThreadingCfg.BrokerIdleStrategy;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.agrona.concurrent.BackoffIdleStrategy;
import uk.co.real_logic.agrona.concurrent.CompositeAgent;
import uk.co.real_logic.agrona.concurrent.CountersManager;
import uk.co.real_logic.agrona.concurrent.IdleStrategy;
import uk.co.real_logic.agrona.concurrent.NoOpIdleStrategy;

public class AgentRunnterServiceImpl implements AgentRunnerService, Service<AgentRunnerService>
{
    static int maxThreadCount = Runtime.getRuntime().availableProcessors() - 1;

    protected final Injector<Counters> countersInjector = new Injector<>();

    protected final AgentGroup logAgents = new AgentGroup("log");
    protected final AgentGroup networkingAgents = new AgentGroup("networking");
    protected final AgentGroup conductorAgents = new AgentGroup("conductor");
    protected final AgentGroup workerAgents = new AgentGroup("workers");

    protected final int availableThreads;

    protected final List<AgentRunner> agentRunners = new ArrayList<>();

    protected final List<AtomicCounter> errorCounters = new ArrayList<>();

    protected final BrokerIdleStrategy idleStrategy;
    protected final int maxIdleTimeMs;

    public AgentRunnterServiceImpl(ConfigurationManager configurationManager)
    {
        final ThreadingCfg cfg = configurationManager.readEntry("threading", ThreadingCfg.class);

        int numberOfThreads = cfg.numberOfThreads;

        if(numberOfThreads > maxThreadCount)
        {
            System.err.println("WARNING: configured thread count ("+numberOfThreads+") is larger than maxThreadCount "+maxThreadCount+"). Falling back max thread count.");
            numberOfThreads = maxThreadCount;
        }

        availableThreads = numberOfThreads;
        idleStrategy = cfg.idleStrategy;
        maxIdleTimeMs = cfg.maxIdleTimeMs;
    }

    private AgentRunner createAgentRunner(Agent agent, CountersManager countersManager)
    {
        final IdleStrategy idleStrategy;

        switch (this.idleStrategy)
        {
        case BUSY_SPIN:
            idleStrategy = new NoOpIdleStrategy();
            break;

        default:
            idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(maxIdleTimeMs));
        }

        final String errorCounterName = String.format("%s.errorCounter", agent.roleName());
        final AtomicCounter errorCounter = countersManager.newCounter(errorCounterName);
        errorCounters.add(errorCounter);
        return new AgentRunner(idleStrategy, (t)-> t.printStackTrace(), errorCounter, agent);
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final CountersManager countersManager = countersInjector.getValue().getCountersManager();

        // TODO: implement this in a better way !!!

        if(availableThreads >= 4)
        {
            agentRunners.add(createAgentRunner(logAgents, countersManager));
            agentRunners.add(createAgentRunner(networkingAgents, countersManager));
            agentRunners.add(createAgentRunner(workerAgents, countersManager));
            agentRunners.add(createAgentRunner(conductorAgents, countersManager));
        }
        else if(availableThreads == 3)
        {
            agentRunners.add(createAgentRunner(logAgents, countersManager));
            agentRunners.add(createAgentRunner(conductorAgents, countersManager));
            agentRunners.add(createAgentRunner(new CompositeAgent(networkingAgents, workerAgents), countersManager));
        }
        else if(availableThreads == 2)
        {
            agentRunners.add(createAgentRunner(new CompositeAgent(networkingAgents, conductorAgents, workerAgents), countersManager));
            agentRunners.add(createAgentRunner(logAgents, countersManager));
        }
        else
        {
            agentRunners.add(createAgentRunner(new CompositeAgent(networkingAgents, logAgents, conductorAgents, workerAgents), countersManager));
        }

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
        for (AtomicCounter atomicCounter : errorCounters)
        {
            atomicCounter.close();
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

    public Injector<Counters> getCountersManagerInjector()
    {
        return countersInjector;
    }

}
