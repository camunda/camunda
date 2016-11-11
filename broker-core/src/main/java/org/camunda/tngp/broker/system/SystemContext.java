package org.camunda.tngp.broker.system;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.impl.ServiceContainerImpl;

public class SystemContext implements AutoCloseable
{
    protected final ServiceContainer serviceContainer;

    protected final List<Component> components = new ArrayList<>();

    protected final ConfigurationManager configurationManager;

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
    }

    public void close()
    {
        System.out.println("Closing...");
        serviceContainer.close(10, TimeUnit.SECONDS);
    }

    public ConfigurationManager getConfigurationManager()
    {
        return configurationManager;
    }

}
