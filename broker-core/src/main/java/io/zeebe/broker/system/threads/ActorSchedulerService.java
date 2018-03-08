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

import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.threads.cfg.ThreadingCfg;
import io.zeebe.broker.system.threads.cfg.ThreadingCfg.BrokerIdleStrategy;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.ActorScheduler;
import org.slf4j.Logger;

import java.util.Map;

public class ActorSchedulerService implements Service<ActorScheduler>
{
    public static final Logger LOG = Loggers.SYSTEM_LOGGER;

    static int maxThreadCount = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);

    protected final int availableThreads;

    protected final BrokerIdleStrategy brokerIdleStrategy;
    protected final int maxIdleTimeMs;

    protected ActorScheduler scheduler;
    protected final Map<String, String> diagnosticContext;

    public ActorSchedulerService(Map<String, String> diagnosticContext, ConfigurationManager configurationManager)
    {
        this.diagnosticContext = diagnosticContext;

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

        LOG.info("Created {}", this);
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        LOG.debug("Start scheduler with {} threads", availableThreads);
//        scheduler = new ZbActorScheduler(availableThreads);
//        scheduler.start();
        scheduler = serviceContext.getScheduler();
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        try
        {
//            scheduler.stop();
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

    @Override
    public String toString()
    {
        return "ActorSchedulerService{" +
            "availableThreads=" + availableThreads +
            ", brokerIdleStrategy=" + brokerIdleStrategy +
            ", maxIdleTimeMs=" + maxIdleTimeMs +
            ", context='" + diagnosticContext + '\'' +
            '}';
    }
}
