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
package io.zeebe.broker.configuration;

import java.io.InputStream;

import io.zeebe.broker.system.ConfigurationManagerImpl;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class ConfigurationRule extends TestWatcher
{

    private ConfigurationManagerImpl configurationManager;

    @Override
    protected void starting(final Description description)
    {
        super.starting(description);
        final ConfigurationFile annotation = description.getAnnotation(ConfigurationFile.class);
        if (annotation != null)
        {
            final String fileName = annotation.value();
            final InputStream configurationStream = description.getTestClass().getClassLoader().getResourceAsStream(fileName);
            configurationManager = new ConfigurationManagerImpl(configurationStream);
        }
    }

    @Override
    protected void finished(final Description description)
    {
        super.finished(description);
        configurationManager = null;
    }

    public ConfigurationManagerImpl getConfigurationManager()
    {
        return configurationManager;
    }

    public <T> T getComponent(final String componentName, final Class<T> componentClass)
    {
        return configurationManager.readEntry(componentName, componentClass);
    }

}
