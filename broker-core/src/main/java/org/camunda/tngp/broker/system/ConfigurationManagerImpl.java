package org.camunda.tngp.broker.system;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.util.ReflectUtil;

import com.moandjiezana.toml.Toml;

public class ConfigurationManagerImpl implements ConfigurationManager
{
    protected Toml toml;
    protected GlobalConfiguration globalConfiguration;

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

        initGlobalConfiguration();

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

        initGlobalConfiguration();

    }

    public void initDefault()
    {
        System.out.println("No configuration provided, using default configuration.");
        toml = new Toml().read(ConfigurationManagerImpl.class.getClassLoader().getResourceAsStream("tngp.default.cfg.toml"));
    }

    private void initGlobalConfiguration()
    {
        globalConfiguration = createConfiguration("global", GlobalConfiguration.class);
        globalConfiguration.init();
    }

    @Override
    public <T> T readEntry(final String key, final Class<T> configurationType)
    {
        final T configuration = createConfiguration(key, configurationType);
        applyGlobalConfiguration(configuration);
        return configuration;
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
                final T configObject = toml.to(type);

                applyGlobalConfiguration(configObject);
            }
        }
        return result;
    }

    private <T> T createConfiguration(final String key, final Class<T> configurationType)
    {
        final Toml componentConfig = toml.getTable(key);
        final T configObject;

        if (componentConfig != null)
        {
            configObject = componentConfig.to(configurationType);
        }
        else
        {
            configObject = ReflectUtil.newInstance(configurationType);
        }

        return configObject;
    }

    private <T> void applyGlobalConfiguration(T configuration)
    {
        if (configuration instanceof ComponentConfiguration)
        {
            final ComponentConfiguration componentConfig = (ComponentConfiguration) configuration;
            componentConfig.applyGlobalConfiguration(globalConfiguration);
        }
    }

    public GlobalConfiguration getGlobalConfiguration()
    {
        return globalConfiguration;
    }

}
