package org.camunda.tngp.broker.test;


import java.io.File;
import java.nio.file.Paths;
import org.camunda.tngp.broker.Broker;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.ConfigurationManagerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;



public class MyTesting
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

    }


    @Test
    public void tests()
    {
        final ClassLoader classLoader = getClass().getClassLoader();
        final File cfgFile = new File(classLoader.getResource("tngp.unit-test.cfg.toml").getFile());
        final ConfigurationManager config = new ConfigurationManagerImpl(cfgFile.toString());
        System.out.println(Paths.get("/home/hello").toString());
        System.out.println(config.getGlobalConfiguration().directory);

    }

    @After
    public void after()
    {
    }



}
