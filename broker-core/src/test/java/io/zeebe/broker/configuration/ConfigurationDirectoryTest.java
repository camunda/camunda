package io.zeebe.broker.configuration;

import static io.zeebe.broker.configuration.ConfigurationScenario.*;

import java.io.File;
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
                .gossip(GLOBAL_DIRECTORY, "gossip")
                .meta(GLOBAL_DIRECTORY, "meta")
                .index(GLOBAL_DIRECTORY, "index")
                .snapshot(GLOBAL_DIRECTORY, "snapshot")
                .subscriptions(GLOBAL_DIRECTORY, "subscription")
                .log(GLOBAL_DIRECTORY, "logs"),

            scenario()
                .withConfiguration("zeebe.test.global-default-directory.cfg.toml")
                .metrics(GLOBAL_DIRECTORY_DEFAULT, "metrics")
                .gossip(GLOBAL_DIRECTORY_DEFAULT, "gossip")
                .meta(GLOBAL_DIRECTORY_DEFAULT, "meta")
                .index(GLOBAL_DIRECTORY_DEFAULT, "index")
                .snapshot(GLOBAL_DIRECTORY_DEFAULT, "snapshot")
                .subscriptions(GLOBAL_DIRECTORY_DEFAULT, "subscription")
                .log(GLOBAL_DIRECTORY_DEFAULT, "logs"),

            scenario()
                .withConfiguration("zeebe.test.global-temp-directory.cfg.toml")
                .metrics(GLOBAL_DIRECTORY_TEMP, "metrics")
                .gossip(GLOBAL_DIRECTORY_TEMP, "gossip")
                .meta(GLOBAL_DIRECTORY_TEMP, "meta")
                .index(GLOBAL_DIRECTORY_TEMP, "index")
                .snapshot(GLOBAL_DIRECTORY_TEMP, "snapshot")
                .subscriptions(GLOBAL_DIRECTORY_TEMP, "subscription")
                .log(GLOBAL_DIRECTORY_TEMP, "logs"),

            scenario()
                .withConfiguration("zeebe.test.local-directories.cfg.toml")
                .metrics(LOCAL_DIRECTORY, "my-metrics")
                .gossip(LOCAL_DIRECTORY, "my-gossip")
                .meta(LOCAL_DIRECTORY, "my-meta")
                .index(LOCAL_DIRECTORY, "my-index")
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
    public void after()
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
