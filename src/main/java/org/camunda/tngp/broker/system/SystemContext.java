package org.camunda.tngp.broker.system;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.servicecontainer.ServiceContainer;
import org.camunda.tngp.broker.servicecontainer.impl.ServiceContainerImpl;

public class SystemContext implements AutoCloseable
{
    protected final ServiceContainer serviceContainer;

    protected final List<Component> components = new ArrayList<>();

    protected final ConfigurationManager configurationManager;

    public SystemContext(String configFileLocation)
    {
        this.serviceContainer = new ServiceContainerImpl();
        configurationManager = new ConfigurationManagerImpl(configFileLocation);
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
        for (Component brokerComponent : components)
        {
            try
            {
                brokerComponent.init(this);
                // explicitly run gc after startup
                for(int i = 0; i<5; i++)    System.gc();
            }
            catch(RuntimeException e)
            {
                close();
                throw e;
            }
        }
    }

    public void close()
    {
        serviceContainer.stop();
    }

    public ConfigurationManager getConfigurationManager()
    {
        return configurationManager;
    }

}
