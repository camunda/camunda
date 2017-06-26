package org.camunda.tngp.broker.system.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.status.AtomicCounter;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.threads.cfg.ThreadingCfg;
import org.camunda.tngp.broker.system.threads.cfg.ThreadingCfg.BrokerIdleStrategy;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerBuilder;

public class ActorSchedulerService implements Service<ActorScheduler>
{
    static int maxThreadCount = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);

    protected final int availableThreads;

    protected final List<AtomicCounter> errorCounters = new ArrayList<>();

    protected final BrokerIdleStrategy brokerIdleStrategy;
    protected final int maxIdleTimeMs;

    protected ActorScheduler scheduler;

    public ActorSchedulerService(ConfigurationManager configurationManager)
    {
        final ThreadingCfg cfg = configurationManager.readEntry("threading", ThreadingCfg.class);

        int numberOfThreads = cfg.numberOfThreads;

        if (numberOfThreads > maxThreadCount)
        {
            System.err.println("WARNING: configured thread count (" + numberOfThreads + ") is larger than maxThreadCount " +
                    maxThreadCount + "). Falling back max thread count.");
            numberOfThreads = maxThreadCount;
        }
        else if (numberOfThreads < 1)
        {
            // use max threads by default
            numberOfThreads = maxThreadCount;
        }

        availableThreads = numberOfThreads;
        brokerIdleStrategy = cfg.idleStrategy;
        maxIdleTimeMs = cfg.maxIdleTimeMs;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final IdleStrategy idleStrategy = createIdleStrategy(brokerIdleStrategy);
        final ErrorHandler errorHandler = t -> t.printStackTrace();

        scheduler = new ActorSchedulerBuilder()
                .threadCount(availableThreads)
                .runnerIdleStrategy(idleStrategy)
                .runnerErrorHander(errorHandler)
                .baseIterationsPerActor(10)
                .build();
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        try
        {
            scheduler.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public ActorScheduler get()
    {
        return scheduler;
    }

    protected IdleStrategy createIdleStrategy(BrokerIdleStrategy idleStrategy)
    {
        switch (idleStrategy)
        {
            case BUSY_SPIN:
                return new BusySpinIdleStrategy();
            default:
                return new BackoffIdleStrategy(1000, 100, 100, TimeUnit.MILLISECONDS.toNanos(maxIdleTimeMs));
        }
    }

}
