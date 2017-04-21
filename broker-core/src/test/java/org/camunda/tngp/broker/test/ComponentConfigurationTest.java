package org.camunda.tngp.broker.test;


import java.io.File;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.broker.event.processor.SubscriptionCfg;
import org.camunda.tngp.broker.logstreams.cfg.LogStreamsCfg;
import org.camunda.tngp.broker.logstreams.cfg.SnapshotStorageCfg;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.ConfigurationManagerImpl;
import org.camunda.tngp.broker.system.metrics.cfg.MetricsCfg;
import org.camunda.tngp.broker.transport.cfg.TransportComponentCfg;


@RunWith(Parameterized.class)
public class ComponentConfigurationTest
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
            {"network", TransportComponentCfg.class, "gossip.peersStorageFile", "correctPath/gossip/tngp.cluster"},
            {"network", TransportComponentCfg.class, "gossip.useTempFile", true},
            {"network", TransportComponentCfg.class, "management.metaDirectory", "correctPath/meta/"},
            {"network", TransportComponentCfg.class, "management.useTempDirectory", true},
            {"metrics", MetricsCfg.class, "countersFileName", "correctPath/metrics/tngp.metrics"},
            {"metrics", MetricsCfg.class, "useTempCountersFile", true},
            {"logs", LogStreamsCfg.class, "indexDirectory", "correctPath/logs/"},
            {"logs", LogStreamsCfg.class, "useTempLogDirectory", true},
            {"logs", LogStreamsCfg.class, "useTempIndexFile", true},
            {"snapshot", SnapshotStorageCfg.class, "snapshotDirectory", "correctPath/snapshot/"},
            {"snapshot", SnapshotStorageCfg.class, "useTempSnapshotDirectory", true},
            {"subscriptions", SubscriptionCfg.class, "snapshotDirectory", "correctPath/subscription/"},
            {"subscriptions", SubscriptionCfg.class, "useTempSnapshotFile", true},

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
        final Object cfg = configurationManager.readEntry(configurationSection, configurationClass);

        //testing
        assertThat(getValueFromParameterName(cfg, parameterName)).isEqualTo(exceptedValue);
    }

    @Test
    public void shouldUseConfigurationInNonGlobalSection()
    {
        //given

        final ClassLoader classLoader = getClass().getClassLoader();
        final File cfgFile = new File(classLoader.getResource("tngp." + nonglobalPrefix + ".cfg.toml").getFile());
        final ConfigurationManager configurationManager = new ConfigurationManagerImpl(cfgFile.getAbsoluteFile().toString());
        final Object cfg = configurationManager.readEntry(configurationSection, configurationClass);

        //testing
        assertThat(getValueFromParameterName(cfg, parameterName)).isEqualTo(exceptedValue);
    }

    @Test
    public void shouldUseOriginalConfigurationIfNoConfiguration()
    {
        //given

        final ClassLoader classLoader = getClass().getClassLoader();
        final File cfgFile = new File(classLoader.getResource("tngp." + nonconfigPrefix + ".cfg.toml").getFile());
        final ConfigurationManager configurationManager = new ConfigurationManagerImpl(cfgFile.getAbsoluteFile().toString());
        final Object cfg = configurationManager.readEntry(configurationSection, configurationClass);

        //except
        Object exceptObj = null;
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
