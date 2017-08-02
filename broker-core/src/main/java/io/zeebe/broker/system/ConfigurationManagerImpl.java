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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.moandjiezana.toml.Toml;

import io.zeebe.broker.Loggers;
import io.zeebe.util.ReflectUtil;

public class ConfigurationManagerImpl implements ConfigurationManager
{
    public static final Logger LOG = Loggers.SYSTEM_LOGGER;

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
            LOG.info("Using config file " + file.getAbsolutePath());
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
            LOG.info("Using provided configuration stream");
            toml = new Toml().read(configStream);
        }

        initGlobalConfiguration();

    }

    public void initDefault()
    {
        LOG.info("No configuration provided, using default configuration.");
        try (InputStream resourceAsStream = ConfigurationManagerImpl.class.getClassLoader().getResourceAsStream("zeebe.default.cfg.toml"))
        {
            toml = new Toml().read(resourceAsStream);
        }
        catch (final IOException e)
        {
            throw new RuntimeException("Failed to read configuration", e);
        }
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
