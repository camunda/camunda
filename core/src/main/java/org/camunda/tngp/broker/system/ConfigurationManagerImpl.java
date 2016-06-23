package org.camunda.tngp.broker.system;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.moandjiezana.toml.Toml;

public class ConfigurationManagerImpl implements ConfigurationManager
{
    protected final String configFileLocation;

    protected Toml toml;

    public ConfigurationManagerImpl(String configFileLocation)
    {
        this.configFileLocation = configFileLocation;
        if (configFileLocation == null)
        {
            System.out.println("No configuration file provided, using default configuration.");
            toml = new Toml().read(ConfigurationManagerImpl.class.getClassLoader().getResourceAsStream("tngp.default.cfg.toml"));
        }
        else
        {
            final File file = new File(configFileLocation);
            System.out.println("Using config file " + file.getAbsolutePath());
            toml = new Toml().read(file);
        }
    }

    @Override
    public <T> T readEntry(String key, Class<T> configObjectType)
    {
        final Toml componentConfig = toml.getTable(key);

        T configObject = null;

        if (componentConfig != null)
        {
            configObject = componentConfig.to(configObjectType);
        }

        return configObject;
    }

    @Override
    public <T> List<T> readList(String key, Class<T> type)
    {
        final List<T> result = new ArrayList<>();
        final List<Toml> tables = toml.getTables(key);
        for (Toml toml : tables)
        {
            result.add(toml.to(type));
        }
        return result;
    }

}
