package org.camunda.tngp.broker.test;


import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.camunda.tngp.broker.Broker;
import org.camunda.tngp.broker.system.ConfigurationManagerImpl;
import org.camunda.tngp.util.FileUtil;
import org.camunda.tngp.util.LangUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;



public class ComponentConfigurationFileExistsTest
{

    /*Test cases:
     * 1. √ set global data path, no  global use temp, no  local data path => use global data path
     * 2. √ no  global data path, no  global use temp, doesn't care  local data path => use  ./tngp-data/
     * 3. √ set global data path, set global use temp, no  local data path => throw exception
     * 4. √ no  global data path, set global use temp, no  local data path => use global temp data path
     * 5. √ no  global data path, set global use temp, set local data path => use global temp data path for others, use local data path for specified part
     * 6. √ no  global data path, no  global use temp, set local data path => use  ./tngp-data/ for others, use local data path for specified part
     * 7. √ set global data path, no  global use temp, set local data path => use global data path for others, use local data path for specified part
     * 8. √ should automatically remove the temporary folder.
     * */


    @Rule
    public ExpectedException thrown = ExpectedException.none();

    Broker broker;
    EmbeddedBrokerRule embeddedBroker;
    String configString;
    Thread brokerThreadHandler;
    String workSpace;
    ClassLoader classLoader = getClass().getClassLoader();


    @Before
    public void before()
    {
    }

    @Test
    public void shouldUseGlobalPath()
    {

        //given
        final String fileName = "tngp.test.global-data-path.no-global-use-temp-data.no-local-data-path.cfg.toml";
        embeddedBroker = new EmbeddedBrokerRule(fileName);
        final Path path = Paths.get("/tmp/tngp-testing/global-data-path/");

        //when

        embeddedBroker.startBroker();
        //then

        assertThat(new File(path + "/gossip/gossip.tngp").exists()).isTrue();
        assertThat(new File(path + "/index").exists()).isTrue();
        assertThat(new File(path + "/logs").exists()).isTrue();
        assertThat(new File(path + "/meta").exists()).isTrue();
        assertThat(new File(path + "/metrics/metrics.tngp").exists()).isTrue();
        assertThat(new File(path + "/snapshot").exists()).isTrue();
        assertThat(new File(path + "/subscription").exists()).isTrue();


        //clean
        shutdownBroker();
        rudelyDeleteFolder(path.toString());


    }

    @Test
    public void shouldUseCurrentPath()
    {

        //given
        final String fileName = "tngp.test.no-global-data-path.no-global-use-temp-data.no-local-data-path.cfg.toml";
        embeddedBroker = new EmbeddedBrokerRule(fileName);

        //when

        embeddedBroker.startBroker();
        final String path = Paths.get("./tngp-data/").toAbsolutePath().normalize().toString();
        //then
        assertThat(new File(path + "/gossip/gossip.tngp").exists()).isTrue();
        assertThat(new File(path + "/index").exists()).isTrue();
        assertThat(new File(path + "/logs").exists()).isTrue();
        assertThat(new File(path + "/meta").exists()).isTrue();
        assertThat(new File(path + "/metrics/metrics.tngp").exists()).isTrue();
        assertThat(new File(path + "/snapshot").exists()).isTrue();
        assertThat(new File(path + "/subscription").exists()).isTrue();

        //clean

        shutdownBroker();



    }

    @Test
    public void shouldUseTempPath()
    {

        //given
        final String fileName = "tngp.test.no-global-data-path.global-use-temp-data.no-local-data-path.cfg.toml";
        embeddedBroker = new EmbeddedBrokerRule(fileName);

        //when

        embeddedBroker.startBroker();
        final String path = getGlobalDataPath();

        //then

        assertThat(new File(path + "/gossip/gossip.tngp").exists()).isTrue();
        assertThat(new File(path + "/index").exists()).isTrue();
        assertThat(new File(path + "/logs").exists()).isTrue();
        assertThat(new File(path + "/meta").exists()).isTrue();
        assertThat(new File(path + "/metrics/metrics.tngp").exists()).isTrue();
        assertThat(new File(path + "/snapshot").exists()).isTrue();
        assertThat(new File(path + "/subscription").exists()).isTrue();


        //clean

        shutdownBroker();


    }



    @Test
    public void shouldCleanTempDirectory()
    {

        //given
        final String fileName = "tngp.test.no-global-data-path.global-use-temp-data.no-local-data-path.cfg.toml";
        embeddedBroker = new EmbeddedBrokerRule(fileName);

        //when

        embeddedBroker.startBroker();
        final String path = getGlobalDataPath();

        shutdownBroker();

        //then

        assertThat(new File(path.toString()).exists()).isFalse();


    }


    @Test
    public void shouldUseTempPathForOthersUseLocalPathForLogs()
    {

        //given
        final String fileName = "tngp.test.no-global-data-path.global-use-temp-data.local-data-path.cfg.toml";
        embeddedBroker = new EmbeddedBrokerRule(fileName);

        //when

        embeddedBroker.startBroker();
        final String path = getGlobalDataPath();
        final Path logsPath = Paths.get("/tmp/test/logs");

        //then

        assertThat(new File(path + "/gossip/gossip.tngp").exists()).isTrue();
        assertThat(new File(path + "/index").exists()).isTrue();
        assertThat(new File(logsPath.toString()).exists()).isTrue();
        assertThat(new File(path + "/meta").exists()).isTrue();
        assertThat(new File(path + "/metrics/metrics.tngp").exists()).isTrue();
        assertThat(new File(path + "/snapshot").exists()).isTrue();
        assertThat(new File(path + "/subscription").exists()).isTrue();

        //clean

        shutdownBroker();
        rudelyDeleteFolder(logsPath.toString());


    }

    @Test
    public void shouldUseGlobalPathForOthersUseLocalPathForLogs()
    {

        //given
        final String fileName = "tngp.test.global-data-path.no-global-use-temp-data.local-data-path.cfg.toml";
        embeddedBroker = new EmbeddedBrokerRule(fileName);

        //when

        embeddedBroker.startBroker();
        final Path path = Paths.get("/tmp/tngp-testing/global-data-path/");
        final Path logsPath = Paths.get("/tmp/test/logs");

        //then

        assertThat(new File(path + "/gossip/gossip.tngp").exists()).isTrue();
        assertThat(new File(path + "/index").exists()).isTrue();
        assertThat(new File(logsPath.toString()).exists()).isTrue();
        assertThat(new File(path + "/meta").exists()).isTrue();
        assertThat(new File(path + "/metrics/metrics.tngp").exists()).isTrue();
        assertThat(new File(path + "/snapshot").exists()).isTrue();
        assertThat(new File(path + "/subscription").exists()).isTrue();

        //clean

        shutdownBroker();
        rudelyDeleteFolder(logsPath.toString());
        rudelyDeleteFolder(path.toString());

    }

    @Test
    public void shouldUseCurrentPathForOthersUseLocalPathForLogs()
    {

        //given
        final String fileName = "tngp.test.no-global-data-path.no-global-use-temp-data.local-data-path.cfg.toml";
        embeddedBroker = new EmbeddedBrokerRule(fileName);

        //when

        embeddedBroker.startBroker();
        final String path = Paths.get("./tngp-data/").toAbsolutePath().normalize().toString();
        final Path logsPath = Paths.get("/tmp/test/logs");

        //then

        assertThat(new File(path + "/gossip/gossip.tngp").exists()).isTrue();
        assertThat(new File(path + "/index").exists()).isTrue();
        assertThat(new File(logsPath.toString()).exists()).isTrue();
        assertThat(new File(path + "/meta").exists()).isTrue();
        assertThat(new File(path + "/metrics/metrics.tngp").exists()).isTrue();
        assertThat(new File(path + "/snapshot").exists()).isTrue();
        assertThat(new File(path + "/subscription").exists()).isTrue();

        //clean

        shutdownBroker();
        rudelyDeleteFolder(logsPath.toString());
    }

    @Test
    public void shouldThrowException()
    {

        //given
        final String fileName = "tngp.test.global-data-path.global-use-temp-data.-.cfg.toml";
        embeddedBroker = new EmbeddedBrokerRule(fileName);

        //except

        thrown.expect(RuntimeException.class);
        //when
        embeddedBroker.startBroker();

        shutdownBroker();

    }


    @After
    public void after()
    {
    }

    private void rudelyDeleteFolder(String path)
    {
        try
        {
            FileUtil.deleteFolder(path.toString());
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }
    private void shutdownBroker()
    {
        if (embeddedBroker != null)
        {
            try
            {
                System.out.println("shutting down broker..");
                embeddedBroker.stopBroker();
//                Thread.sleep(5000);
                System.out.println("sleep done");
            }
            catch (Exception e)
            {
                //do nothing
            }
        }
    }

    private String getGlobalDataPath()
    {
        if (this.embeddedBroker == null)
        {
            throw new RuntimeException("embedded broker should be initialized");
        }
        final ConfigurationManagerImpl cfgManager = (ConfigurationManagerImpl) embeddedBroker.getBroker().getBrokerContext().getConfigurationManager();
        return cfgManager.getGlobalConfiguration().getGlobalDataDirectory();
    }


}
