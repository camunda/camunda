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
package io.zeebe.broker.system;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.threads.cfg.ThreadingCfg;
import io.zeebe.broker.transport.cfg.SocketBindingCfg;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.ConcurrentCountersManager;
import org.slf4j.Logger;

public class SystemContext implements AutoCloseable
{
    private static final int MAX_THREAD_COUNT = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);

    public static final Logger LOG = Loggers.SYSTEM_LOGGER;
    public static final String BROKER_ID_LOG_PROPERTY = "broker-id";
    public static final int CLOSE_TIMEOUT = 10;

    protected final ServiceContainer serviceContainer;

    protected final List<Component> components = new ArrayList<>();

    protected final ConfigurationManager configurationManager;

    protected final List<ActorFuture<?>> requiredStartActions = new ArrayList<>();

    protected Map<String, String> diagnosticContext;
    protected final ActorScheduler scheduler;


    public SystemContext(String configFileLocation, ActorClock clock)
    {
        this(new ConfigurationManagerImpl(configFileLocation), clock);
    }

    public SystemContext(InputStream configStream, ActorClock clock)
    {
        this(new ConfigurationManagerImpl(configStream), clock);
    }

    public SystemContext(ConfigurationManager configurationManager, ActorClock clock)
    {
        final String brokerId = readBrokerId(configurationManager);
        this.diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);

        this.configurationManager = configurationManager;
        // TODO: submit diagnosticContext to actor scheduler once supported
        this.scheduler = initScheduler(clock, brokerId);
        this.serviceContainer = new ServiceContainerImpl(this.scheduler);
        this.scheduler.start();
    }

    private ActorScheduler initScheduler(ActorClock clock, String brokerId)
    {
        final ThreadingCfg cfg = configurationManager.readEntry("threading", ThreadingCfg.class);
        int numberOfThreads = cfg.numberOfThreads;

        if (numberOfThreads > MAX_THREAD_COUNT)
        {
            LOG.warn("Configured thread count {} is larger than MAX_THREAD_COUNT {}. Falling back max thread count.", numberOfThreads, MAX_THREAD_COUNT);
            numberOfThreads = MAX_THREAD_COUNT;
        }
        else if (numberOfThreads < 1)
        {
            // use max threads by default
            numberOfThreads = MAX_THREAD_COUNT;
        }

        final UnsafeBuffer valueBuffer = new UnsafeBuffer(new byte[32 * 1024]);
        final UnsafeBuffer labelBuffer = new UnsafeBuffer(new byte[valueBuffer.capacity() * 2 + 1]);
        final ConcurrentCountersManager countersManager = new ConcurrentCountersManager(labelBuffer, valueBuffer);
        Loggers.SYSTEM_LOGGER.debug("Start scheduler with {} threads.", numberOfThreads);

        final int ioBoundThreads = 2;
        final int cpuBoundThreads = Math.max(1, numberOfThreads - ioBoundThreads);

        return ActorScheduler.newActorScheduler()
                             .setActorClock(clock)
                             .setCountersManager(countersManager)
                             .setCpuBoundActorThreadCount(cpuBoundThreads)
                             .setIoBoundActorThreadCount(ioBoundThreads)
                             .setSchedulerName(brokerId)
                             .build();
    }

    protected static String readBrokerId(ConfigurationManager configurationManager)
    {
        final TransportComponentCfg transportComponentCfg = configurationManager.readEntry("network", TransportComponentCfg.class);
        final SocketBindingCfg clientApiCfg = transportComponentCfg.clientApi;
        return clientApiCfg.getHost(transportComponentCfg.host) + ":" + clientApiCfg.getPort();
    }


    public ActorScheduler getScheduler()
    {
        return scheduler;
    }

    public ServiceContainer getServiceContainer()
    {
        return serviceContainer;
    }

    public void addComponent(Component component)
    {
        this.components.add(component);
    }

    public List<Component> getComponents()
    {
        return components;
    }

    public void init()
    {
        serviceContainer.start();

        for (Component brokerComponent : components)
        {
            try
            {
                brokerComponent.init(this);
            }
            catch (RuntimeException e)
            {
                close();
                throw e;
            }
        }

        try
        {
            for (ActorFuture<?> requiredStartAction : requiredStartActions)
            {
                requiredStartAction.get(20, TimeUnit.SECONDS);
            }
        }
        catch (Exception e)
        {
            LOG.error("Could not start broker", e);
            close();
            throw new RuntimeException(e);
        }

    }

    @Override
    public void close()
    {
        LOG.info("Closing...");

        try
        {
            serviceContainer.close(CLOSE_TIMEOUT, TimeUnit.SECONDS);
        }
        catch (TimeoutException e)
        {
            LOG.error("Failed to close broker within {} seconds.", CLOSE_TIMEOUT, e);
        }
        catch (ExecutionException | InterruptedException e)
        {
            LOG.error("Exception while closing broker", e);
        }
        finally
        {
            try
            {
                scheduler.stop().get(CLOSE_TIMEOUT, TimeUnit.SECONDS);
            }
            catch (TimeoutException e)
            {
                LOG.error("Failed to close scheduler within {} seconds", CLOSE_TIMEOUT, e);
            }
            catch (ExecutionException | InterruptedException e)
            {
                LOG.error("Exception while closing scheduler", e);
            }
            finally
            {
                final GlobalConfiguration config = configurationManager.getGlobalConfiguration();
                final String directory = config.getDirectory();
                if (config.isTempDirectory())
                {
                    try
                    {
                        FileUtil.deleteFolder(directory);
                    }
                    catch (IOException e)
                    {
                        LOG.error("Exception while deleting temp folder", e);
                    }
                }

            }

        }
    }

    public ConfigurationManager getConfigurationManager()
    {
        return configurationManager;
    }

    public void addRequiredStartAction(ActorFuture<?> future)
    {
        requiredStartActions.add(future);
    }

    public Map<String, String> getDiagnosticContext()
    {
        return diagnosticContext;
    }

}
