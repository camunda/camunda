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

import io.zeebe.broker.Broker;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.system.threads.cfg.ThreadingCfg;
import io.zeebe.broker.transport.cfg.SocketBindingCfg;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.broker.util.BrokerArguments;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.FileUtil;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import org.slf4j.Logger;

public class SystemContext implements AutoCloseable
{
    private static final int MAX_THREAD_COUNT = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);

    public static final Logger LOG = Loggers.SYSTEM_LOGGER;
    public static final String BROKER_ID_LOG_PROPERTY = "broker-id";
    public static final int CLOSE_TIMEOUT = 10;

    protected ServiceContainer serviceContainer;

    protected final List<Component> components = new ArrayList<>();

    protected final ConfigurationManager configurationManager;

    protected final List<ActorFuture<?>> requiredStartActions = new ArrayList<>();

    protected Map<String, String> diagnosticContext;
    protected ActorScheduler scheduler;

    private MetricsManager metricsManager;
    private BrokerArguments brokerArguments;

    public SystemContext(String[] commandLineArgs, ActorClock clock)
    {
        this.brokerArguments = new BrokerArguments(commandLineArgs);
        this.configurationManager =
            new ConfigurationManagerImpl(brokerArguments.getConfigFile());

        initSystemContext(clock);
    }

    public SystemContext(String configFileLocation, ActorClock clock)
    {
        this(new String[]{"-config", configFileLocation}, clock);
    }

    public SystemContext(InputStream configStream, ActorClock clock)
    {
        this(new ConfigurationManagerImpl(configStream), clock);
    }

    public SystemContext(ConfigurationManager configurationManager, ActorClock clock)
    {
        this.configurationManager = configurationManager;
        this.brokerArguments = new BrokerArguments(new String[0]);
        initSystemContext(clock);
    }

    private void initSystemContext(ActorClock clock)
    {
        final String brokerId = readBrokerId(configurationManager);
        this.diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);

        // TODO: submit diagnosticContext to actor scheduler once supported
        this.metricsManager = initMetricsManager(brokerId);
        this.scheduler = initScheduler(clock, brokerId);
        this.serviceContainer = new ServiceContainerImpl(this.scheduler);
        this.scheduler.start();
        initBrokerInfoMetric();
    }

    private MetricsManager initMetricsManager(String brokerId)
    {
        final Map<String, String> globalLabels = new HashMap<>();
        globalLabels.put("cluster", "zeebe");
        globalLabels.put("node", brokerId);
        return new MetricsManager("zb_", globalLabels);
    }

    private void initBrokerInfoMetric()
    {
        // one-shot metric to submit metadata
        metricsManager.newMetric("broker_info")
                .type("counter")
                .label("version", Broker.VERSION)
                .create()
                .incrementOrdered();
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

        final int ioBoundThreads = 2;
        final int cpuBoundThreads = Math.max(1, numberOfThreads - ioBoundThreads);

        Loggers.SYSTEM_LOGGER.info("Scheduler configuration: Threads{cpu-bound: {}, io-bound: {}}.", cpuBoundThreads, ioBoundThreads);

        return ActorScheduler.newActorScheduler()
            .setActorClock(clock)
            .setMetricsManager(metricsManager)
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

    public BrokerArguments getBrokerArguments()
    {
        return brokerArguments;
    }
}
