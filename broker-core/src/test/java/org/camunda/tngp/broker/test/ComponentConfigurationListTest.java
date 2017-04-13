package org.camunda.tngp.broker.test;


import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.broker.logstreams.cfg.LogStreamCfg;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.ConfigurationManagerImpl;
import org.camunda.tngp.broker.taskqueue.cfg.TaskQueueCfg;
import org.camunda.tngp.broker.workflow.cfg.WorkflowQueueCfg;


@RunWith(Parameterized.class)
public class ComponentConfigurationListTest
{
    public boolean a = true;
    public GossipConfiguration goosip = new GossipConfiguration();
    public String globalPrefix = "global";
    public String nonglobalPrefix = "nonglobal";
    public String nonconfigPrefix = "nonconfig";

    public Object globalCfg;
    public Object nonglobalCfg;
    public Object nonconfigCfg;

    public static boolean runOnce = false;


    @Parameters(name = "{0}")
    public static Iterable<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
            {"log", LogStreamCfg.class, "logDirectory", "correctPath/logs/0-test/"},
            {"log", LogStreamCfg.class, "useTempLogDirectory", true},
            {"task-queue", TaskQueueCfg.class, "indexDirectory", "correctPath/task-queue-test/"},
            {"task-queue", TaskQueueCfg.class, "useTempIndexFile", true},
            {"workflow-queue", WorkflowQueueCfg.class, "indexDirectory", "correctPath/workflow-queue-test/"},
            {"workflow-queue", WorkflowQueueCfg.class, "useTempIndexFile", true},

        });
    }



    @Parameter(0)
    public String configurationSection;

    @Parameter(1)
    public Class<?> configurationClass;

    @Parameter(2)
    public String parameterName;

    @Parameter(3)
    public Object exceptedValue;



    @Test
    public void shouldUseConfigurationInGlobalSection()
    {
        //given

        final ClassLoader classLoader = getClass().getClassLoader();
        final File cfgFile = new File(classLoader.getResource("tngp." + globalPrefix + ".cfg.toml").getFile());
        final ConfigurationManager configurationManager = new ConfigurationManagerImpl(cfgFile.getAbsoluteFile().toString());
        final List<?> cfgList = configurationManager.readList(configurationSection, configurationClass);

        //testing
        for (Object cfg : cfgList)
        {
            assertThat(getValueFromParameterName(cfg, parameterName)).isEqualTo(exceptedValue);
        }
    }


    @Test
    public void shouldUseConfigurationInNonGlobalSection()
    {
        //given

        final ClassLoader classLoader = getClass().getClassLoader();
        final File cfgFile = new File(classLoader.getResource("tngp." + nonglobalPrefix + ".cfg.toml").getFile());
        final ConfigurationManager configurationManager = new ConfigurationManagerImpl(cfgFile.getAbsoluteFile().toString());
        final List<?> cfgList = configurationManager.readList(configurationSection, configurationClass);

        //testing
        for (Object cfg : cfgList)
        {
            assertThat(getValueFromParameterName(cfg, parameterName)).isEqualTo(exceptedValue);
        }

    }

    @Test
    public void shouldUseOriginalConfigurationIfNoConfiguration()
    {
        //given

        final ClassLoader classLoader = getClass().getClassLoader();
        final File cfgFile = new File(classLoader.getResource("tngp." + nonconfigPrefix + ".cfg.toml").getFile());
        final ConfigurationManager configurationManager = new ConfigurationManagerImpl(cfgFile.getAbsoluteFile().toString());
        final List<?> cfgList = configurationManager.readList(configurationSection, configurationClass);
        //except
        Object exceptObj = null;

        //testing
        for (Object cfg : cfgList)
        {
            try
            {
                exceptObj = configurationClass.newInstance();
            }
            catch (InstantiationException | IllegalAccessException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            //testing
            assertThat(getValueFromParameterName(cfg, parameterName)).isEqualTo(getValueFromParameterName(exceptObj, parameterName));
        }



    }


    protected static Object getValueFromParameterName(Object obj, String name)
    {

        final String[] parameterList = name.split("\\.");
        Object retObj = obj;
        for (int i = 0; i < parameterList.length; i++)
        {
            try
            {
                retObj = retObj.getClass().getField(parameterList[i]).get(retObj);
            }
            catch (SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return retObj;
    }

}
