package io.zeebe.broker.system;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.agrona.LangUtil;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.FileUtil;

public class SystemContext implements AutoCloseable
{
    public static final Logger LOG = Loggers.SYSTEM_LOGGER;

    protected final ServiceContainer serviceContainer;

    protected final List<Component> components = new ArrayList<>();

    protected final ConfigurationManager configurationManager;

    protected final List<CompletableFuture<?>> requiredStartActions = new ArrayList<>();

    protected SystemContext(ConfigurationManager configurationManager)
    {
        this.serviceContainer = new ServiceContainerImpl();
        this.configurationManager = configurationManager;
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
            CompletableFuture.allOf(startActions).get(10 * 10000, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            LOG.error("Could not start broker", e);
            close();
            LangUtil.rethrowUnchecked(e);
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

}
