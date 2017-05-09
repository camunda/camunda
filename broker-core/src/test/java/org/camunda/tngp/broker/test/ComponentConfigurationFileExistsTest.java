package org.camunda.tngp.broker.test;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.agrona.IoUtil;
import org.camunda.tngp.broker.Broker;
import org.camunda.tngp.broker.system.ConfigurationManagerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;




class ConfigurationHelper
{
    public HashMap<String, String[]> configMaps;
    public StringBuffer configBuffer = new StringBuffer();

    public ConfigurationHelper setConfigMaps(HashMap<String, String[]> c)
    {
        this.configMaps = c;
        return this;
    }


    private void lineHandler(String sCurrentLine)
    {
        final String[] subConfig;
        if ((subConfig = configMaps.get(sCurrentLine)) != null)
        {
            for (String each : subConfig)
            {
                configBuffer.append(each);
                configBuffer.append(System.lineSeparator());
            }
        }
    }
    public String generateConfig()
    {
        final ClassLoader classLoader = getClass().getClassLoader();
        final File cfgFile = new File(classLoader.getResource("tngp.unit-test.cfg.toml").getFile());
        BufferedReader br = null;
        FileReader fr = null;
        try
        {
            fr = new FileReader(cfgFile);
            br = new BufferedReader(fr);
            String sCurrentLine;
            br = new BufferedReader(new FileReader(cfgFile));
            while ((sCurrentLine = br.readLine()) != null)
            {

                configBuffer.append(sCurrentLine);
                configBuffer.append(System.lineSeparator());
                lineHandler(sCurrentLine);
            }

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (br != null)
                {
                    br.close();
                }

                if (fr != null)
                {
                    fr.close();
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

        }
//        System.out.println(configBuffer.toString());
        return configBuffer.toString();
    }
}

public class ComponentConfigurationFileExistsTest
{

    /*Test cases:
     * t set global data path, no  global use temp, no  local data path => use global data path √
     * √ no  global data path, no  global use temp, no  local data path => use global temp data path -unable to test
     * √ set global data path, set global use temp, no  local data path => use global temp data path √
     * √ no  global data path, set global use temp, no  local data path => use global temp data path √
     * √ set global data path, set global use temp, set local data path => use global temp data path for others, use local data path for specific part
     * x no  global data path, set global use temp, set local data path => use global temp data path for others, use local data path for specific part
     * x no  global data path, no  global use temp, set local data path => use global temp data path for others, use local data path for specific part
     * √ set global data path, no  global use temp, set local data path => use global data path for others, use local data path for specific part
     * */


    Broker broker;
    String configString;
    Thread brokerThreadHandler;
    String workSpace;


    @Before
    public void before() throws Exception
    {
        final Path path = Files.createTempDirectory("tngp-config-test-");
        workSpace = path.toString();

        System.out.println("------------->");

    }

    @Test
    public void shouldUseGlobalPath()
    {
        final HashMap<String, String[]> configMaps = new HashMap<String, String[]>();

        final ConfigurationHelper configHelper = new ConfigurationHelper();

        final String path = workSpace + "/tmp/global/";

        configMaps.put("[global]", new String[]{"globalDataDirectory = \"" + path + "\""});
        configString = configHelper.setConfigMaps(configMaps).generateConfig();

        bootBroker();


        assert (new File(path + "gossip").exists());
        assert (new File(path + "index").exists());
        assert (new File(path + "logs").exists());
        assert (new File(path + "meta").exists());
        assert (new File(path + "metrics/tngp.metrics").exists());
        assert (new File(path + "snapshot").exists());
        assert (new File(path + "subscription").exists());

    }

    @Test
    public void shouldUseGlobalTempPathIfGlobalPathExists()
    {
        final HashMap<String, String[]> configMaps = new HashMap<String, String[]>();
        final ConfigurationHelper configHelper = new ConfigurationHelper();
        configMaps.put("[global]", new String[]{"globalUseTemp = true", "globalDataDirectory = \"/tmp/global\""});
        configString = configHelper.setConfigMaps(configMaps).generateConfig();

        bootBroker();
        final String path = getGlobalDataPath();

        assert (new File(path + "gossip").exists());
        assert (new File(path + "index").exists());
        assert (new File(path + "logs").exists());
        assert (new File(path + "meta").exists());
        assert (new File(path + "metrics/tngp.metrics").exists());
        assert (new File(path + "snapshot").exists());
        assert (new File(path + "subscription").exists());


    }

    @Test
    public void shouldUseGlobalTempPath()
    {
        final HashMap<String, String[]> configMaps = new HashMap<String, String[]>();

        final ConfigurationHelper configHelper = new ConfigurationHelper();
        configMaps.put("[global]", new String[]{"globalUseTemp = true"});

        configString = configHelper.setConfigMaps(configMaps).generateConfig();

        bootBroker();
        final String path = getGlobalDataPath();

        assert (new File(path + "gossip").exists());
        assert (new File(path + "index").exists());
        assert (new File(path + "logs").exists());
        assert (new File(path + "meta").exists());
        assert (new File(path + "metrics/tngp.metrics").exists());
        assert (new File(path + "snapshot").exists());
        assert (new File(path + "subscription").exists());


    }

    @Test
    public void shouldUseGlobalTempPathIfNoGlobalProperty()
    {
        final HashMap<String, String[]> configMaps = new HashMap<String, String[]>();

        final ConfigurationHelper configHelper = new ConfigurationHelper();

        configString = configHelper.setConfigMaps(configMaps).generateConfig();

        bootBroker();
        final String path = getGlobalDataPath();

        assert (new File(path + "gossip").exists());
        assert (new File(path + "index").exists());
        assert (new File(path + "logs").exists());
        assert (new File(path + "meta").exists());
        assert (new File(path + "metrics/tngp.metrics").exists());
        assert (new File(path + "snapshot").exists());
        assert (new File(path + "subscription").exists());


    }

    @Test
    public void shouldUseSpecificPathOnLogsAndGlobalPath()
    {
        final HashMap<String, String[]> configMaps = new HashMap<String, String[]>();

        final ConfigurationHelper configHelper = new ConfigurationHelper();

        final String path = workSpace + "/tmp/global/";
        final String logsPath = workSpace + "/tmp/local/logs/";

        configMaps.put("[global]", new String[]{"globalDataDirectory = \"" + path + "\""});
        configMaps.put("[logs]", new String[]{"logDirectories = [ \"" + logsPath + "\" ]"});
        configString = configHelper.setConfigMaps(configMaps).generateConfig();
        bootBroker();


        assert (new File(path + "gossip").exists());
        assert (new File(path + "index").exists());
        assert (new File(logsPath).exists());
        assert (new File(path + "meta").exists());
        assert (new File(path + "metrics/tngp.metrics").exists());
        assert (new File(path + "snapshot").exists());
        assert (new File(path + "subscription").exists());

    }

    @Test
    public void shouldUseSpecificPathOnLogsAndGlobalTempPath()
    {
        final HashMap<String, String[]> configMaps = new HashMap<String, String[]>();

        final ConfigurationHelper configHelper = new ConfigurationHelper();

        final String logsPath = workSpace + "/tmp/local/logs/";

        configMaps.put("[global]", new String[]{"globalUseTemp = true", "globalDataDirectory = \"/tmp/global\""});
        configMaps.put("[logs]", new String[]{"logDirectories = [ \"" + logsPath + "\" ]"});
        configString = configHelper.setConfigMaps(configMaps).generateConfig();
        bootBroker();

        final String path = getGlobalDataPath();


        assert (new File(path + "gossip").exists());
        assert (new File(path + "index").exists());
        assert (new File(logsPath).exists());
        assert (new File(path + "meta").exists());
        assert (new File(path + "metrics/tngp.metrics").exists());
        assert (new File(path + "snapshot").exists());
        assert (new File(path + "subscription").exists());

    }


    @After
    public void after()
    {
        shutdownBroker();
        deleteDir(new File(workSpace));
    }



    private void shutdownBroker()
    {
        if (broker != null)
        {
            broker.close();
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            broker = null;
        }

    }
    private void bootBroker()
    {
        broker = new Broker(new ByteArrayInputStream(configString.getBytes()));
        try
        {
            Thread.sleep(4000);
        }
        catch (InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    private void deleteDir(File file)
    {
        final File[] contents = file.listFiles();
        if (contents != null)
        {
            for (File f : contents)
            {
                deleteDir(f);
            }
        }
        IoUtil.deleteIfExists(file);
    }


    private String getGlobalDataPath()
    {
        if (this.broker == null)
        {
            throw new RuntimeException("broker should be initialized");
        }
        final ConfigurationManagerImpl cfgManager = (ConfigurationManagerImpl) broker.getBrokerContext().getConfigurationManager();
        return cfgManager.getGlobalConfiguration().getGlobalDataDirectory();
    }


}
