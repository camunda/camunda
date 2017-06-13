package org.camunda.tngp.broker.system;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.agrona.LangUtil;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.impl.ServiceContainerImpl;
import org.camunda.tngp.util.FileUtil;

public class SystemContext implements AutoCloseable
{
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
            CompletableFuture.allOf(startActions).get(10, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            System.err.format("Could not start broker: %s\n", e.getMessage());
//            e.printStackTrace();
            close();
            LangUtil.rethrowUnchecked(e);
        }

    }

    public void close()
    {
        System.out.println("Closing...");

        try
        {
            serviceContainer.close(10, TimeUnit.SECONDS);
        }
        catch (TimeoutException e)
        {
            System.err.println("Failed to close broker within 10 seconds.");
        }
        catch (ExecutionException | InterruptedException e)
        {
            System.err.println("Exception while closing broker:");
            e.printStackTrace();
        }
        finally
        {
            final GlobalConfiguration config = configurationManager.getGlobalConfiguration();
            final String directory = config.getDirectory();
            if (config.isTempDirectory())
            {
                FileUtil.deleteFolder(directory);
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
