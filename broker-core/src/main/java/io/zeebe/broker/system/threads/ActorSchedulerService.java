/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.threads.cfg.ThreadingCfg;
import io.zeebe.broker.system.threads.cfg.ThreadingCfg.BrokerIdleStrategy;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.*;
import org.agrona.concurrent.status.AtomicCounter;
import org.slf4j.Logger;

public class ActorSchedulerService implements Service<ActorScheduler>
{
    public static final Logger LOG = Loggers.SYSTEM_LOGGER;

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
            LOG.warn("Configured thread count {} is larger than maxThreadCount {}. Falling back max thread count.", numberOfThreads, maxThreadCount);
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
                .name("broker")
                .threadCount(availableThreads)
                .runnerIdleStrategy(idleStrategy)
                .runnerErrorHander(errorHandler)
                .baseIterationsPerActor(50)
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
            LOG.error("Unable to stop actor scheduler", e);
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
