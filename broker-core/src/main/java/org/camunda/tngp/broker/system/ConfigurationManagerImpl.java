package org.camunda.tngp.broker.system;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.moandjiezana.toml.Toml;

public class ConfigurationManagerImpl implements ConfigurationManager
{
    protected Toml toml;

    public ConfigurationManagerImpl(final String filePath)
    {
        if (filePath == null)
        {
            initDefault();
        }
        else
        {
            final File file = new File(filePath);
            System.out.println("Using config file " + file.getAbsolutePath());
            toml = new Toml().read(file);
        }
    }

    public ConfigurationManagerImpl(final InputStream configStream)
    {
        if (configStream == null)
        {
            initDefault();
        }
        else
        {
            System.out.println("Using provided configuration stream");
            toml = new Toml().read(configStream);
        }
    }

    public void initDefault()
    {
        System.out.println("No configuration provided, using default configuration.");
        toml = new Toml().read(ConfigurationManagerImpl.class.getClassLoader().getResourceAsStream("tngp.default.cfg.toml"));
    }

    @Override
    public <T> T readEntry(final String key, final Class<T> configObjectType)
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
    public <T> List<T> readList(final String key, final Class<T> type)
    {
        final List<T> result = new ArrayList<>();
        final List<Toml> tables = toml.getTables(key);
        if (tables != null)
        {
            for (final Toml toml : tables)
            {
                result.add(toml.to(type));
            }
        }
        return result;
    }

}
