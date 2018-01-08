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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.transport.cfg.SocketBindingCfg;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.FileUtil;

public class SystemContext implements AutoCloseable
{
    public static final Logger LOG = Loggers.SYSTEM_LOGGER;
    public static final String BROKER_ID_LOG_PROPERTY = "broker-id";

    protected final ServiceContainer serviceContainer;

    protected final List<Component> components = new ArrayList<>();

    protected final ConfigurationManager configurationManager;

    protected final List<CompletableFuture<?>> requiredStartActions = new ArrayList<>();

    protected Map<String, String> diagnosticContext;

    public SystemContext(ConfigurationManager configurationManager)
    {
        final String brokerId = readBrokerId(configurationManager);
        this.diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);
        this.serviceContainer = new ServiceContainerImpl(Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId));
        this.configurationManager = configurationManager;
    }

    protected static String readBrokerId(ConfigurationManager configurationManager)
    {
        final TransportComponentCfg transportComponentCfg = configurationManager.readEntry("network", TransportComponentCfg.class);
        final SocketBindingCfg clientApiCfg = transportComponentCfg.clientApi;
        return clientApiCfg.getHost(transportComponentCfg.host) + ":" + clientApiCfg.getPort();
    }

    public SystemContext(String configFileLocation)
    {
        this(new ConfigurationManagerImpl(configFileLocation));
    }

    public SystemContext(InputStream configStream)
    {
        this(new ConfigurationManagerImpl(configStream));
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
            final CompletableFuture<?>[] startActions = requiredStartActions.toArray(new CompletableFuture[requiredStartActions.size()]);
            CompletableFuture.allOf(startActions).get(500, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            LOG.error("Could not start broker", e);
            close();
            throw new RuntimeException(e);
        }

    }

    public void close()
    {
        LOG.info("Closing...");

        try
        {
            serviceContainer.close(10, TimeUnit.SECONDS);
        }
        catch (TimeoutException e)
        {
            LOG.error("Failed to close broker within 10 seconds", e);
        }
        catch (ExecutionException | InterruptedException e)
        {
            LOG.error("Exception while closing broker", e);
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

    public ConfigurationManager getConfigurationManager()
    {
        return configurationManager;
    }

    public void addRequiredStartAction(CompletableFuture<?> future)
    {
        requiredStartActions.add(future);
    }

    public Map<String, String> getDiagnosticContext()
    {
        return diagnosticContext;
    }

}
