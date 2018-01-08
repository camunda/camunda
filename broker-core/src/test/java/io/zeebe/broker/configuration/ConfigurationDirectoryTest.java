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

import static io.zeebe.broker.configuration.ConfigurationScenario.scenario;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.ConfigurationManagerImpl;
import io.zeebe.broker.system.GlobalConfiguration;
import io.zeebe.util.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConfigurationDirectoryTest
{
    private static final String GLOBAL_DIRECTORY = String.format("%szeebe-testing%sglobal-data-path", File.separator, File.separator);
    private static final String GLOBAL_DIRECTORY_DEFAULT = String.format("%sdata", File.separator);
    private static final String GLOBAL_DIRECTORY_TEMP = String.format("%s%s", File.separator, GlobalConfiguration.GLOBAL_DIRECTORY_TEMP);
    private static final String LOCAL_DIRECTORY = String.format("%smy-local-path", File.separator);

    @Parameters(name = "Scenario {index}")
    public static Collection<ConfigurationScenario> configurations()
    {
        return Arrays.asList(

            scenario()
                .withConfiguration("zeebe.test.global-directory.cfg.toml")
                .metrics(GLOBAL_DIRECTORY, "metrics")
                .meta(GLOBAL_DIRECTORY, "meta")
                .map(GLOBAL_DIRECTORY, "map")
                .snapshot(GLOBAL_DIRECTORY, "snapshot")
                .subscriptions(GLOBAL_DIRECTORY, "subscription")
                .log(GLOBAL_DIRECTORY, "logs"),

            scenario()
                .withConfiguration("zeebe.test.global-default-directory.cfg.toml")
                .metrics(GLOBAL_DIRECTORY_DEFAULT, "metrics")
                .meta(GLOBAL_DIRECTORY_DEFAULT, "meta")
                .map(GLOBAL_DIRECTORY_DEFAULT, "map")
                .snapshot(GLOBAL_DIRECTORY_DEFAULT, "snapshot")
                .subscriptions(GLOBAL_DIRECTORY_DEFAULT, "subscription")
                .log(GLOBAL_DIRECTORY_DEFAULT, "logs"),

            scenario()
                .withConfiguration("zeebe.test.global-temp-directory.cfg.toml")
                .metrics(GLOBAL_DIRECTORY_TEMP, "metrics")
                .meta(GLOBAL_DIRECTORY_TEMP, "meta")
                .map(GLOBAL_DIRECTORY_TEMP, "map")
                .snapshot(GLOBAL_DIRECTORY_TEMP, "snapshot")
                .subscriptions(GLOBAL_DIRECTORY_TEMP, "subscription")
                .log(GLOBAL_DIRECTORY_TEMP, "logs"),

            scenario()
                .withConfiguration("zeebe.test.local-directories.cfg.toml")
                .metrics(LOCAL_DIRECTORY, "my-metrics")
                .meta(LOCAL_DIRECTORY, "my-meta")
                .map(LOCAL_DIRECTORY, "my-map")
                .snapshot(LOCAL_DIRECTORY, "my-snapshot")
                .subscriptions(LOCAL_DIRECTORY, "my-subscription")
                .log(LOCAL_DIRECTORY, "my-log-1")
                .log(LOCAL_DIRECTORY, "my-log-2")
                .log(LOCAL_DIRECTORY, "my-log-3")
        );
    }

    @Parameter
    public ConfigurationScenario scenario;

    private ConfigurationManager configurationManager;

    @Before
    public void before()
    {
        final String configuration = scenario.getConfiguration();
        final InputStream is = ConfigurationDirectoryTest.class
                .getClassLoader()
                .getResourceAsStream(configuration);

        configurationManager = new ConfigurationManagerImpl(is);
    }

    @After
    public void after() throws IOException
    {
        final GlobalConfiguration globalConfiguration = configurationManager.getGlobalConfiguration();
        final String globalDirectory = globalConfiguration.getDirectory();
        final File tempDirectory = new File(globalDirectory);

        if (globalConfiguration.isTempDirectory() && tempDirectory.exists())
        {
            FileUtil.deleteFolder(globalDirectory);
        }
    }

    @Test
    public void shouldResolveDirectories()
    {
        scenario.assertDirectories(configurationManager);
    }

    @Test
    public void shouldResolveLogDirectories()
    {
        scenario.assertLogDirectories(configurationManager);
    }

}
