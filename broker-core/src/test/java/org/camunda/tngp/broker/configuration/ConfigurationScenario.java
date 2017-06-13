package org.camunda.tngp.broker.configuration;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.broker.clustering.management.config.ClusterManagementConfig;
import org.camunda.tngp.broker.event.processor.SubscriptionCfg;
import org.camunda.tngp.broker.logstreams.cfg.LogStreamsCfg;
import org.camunda.tngp.broker.logstreams.cfg.SnapshotStorageCfg;
import org.camunda.tngp.broker.logstreams.cfg.StreamProcessorCfg;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.DirectoryConfiguration;
import org.camunda.tngp.broker.system.metrics.cfg.MetricsCfg;

public class ConfigurationScenario
{
    private String configuration;

    private List<DirectorySpecification<?>> specifications = new ArrayList<>();
    private LogDirectoriesSpecification logDirectoriesSpecification = new LogDirectoriesSpecification();

    public static ConfigurationScenario scenario()
    {
        return new ConfigurationScenario();
    }

    public ConfigurationScenario withConfiguration(String configuration)
    {
        this.configuration = configuration;
        return this;
    }

    public ConfigurationScenario meta(String parent, String child)
    {
        withConfiguration("network.management", ClusterManagementConfig.class, parent, child);
        return this;
    }

    public ConfigurationScenario gossip(String parent, String child)
    {
        withConfiguration("network.gossip", GossipConfiguration.class, parent, child);
        return this;
    }

    public ConfigurationScenario log(String parent, String child)
    {
        logDirectoriesSpecification.addFileSpecification(new FileSpecification(parent, child));
        return this;
    }

    public ConfigurationScenario metrics(String parent, String child)
    {
        withConfiguration("metrics", MetricsCfg.class, parent, child);
        return this;
    }

    public ConfigurationScenario snapshot(String parent, String child)
    {
        withConfiguration("snapshot", SnapshotStorageCfg.class, parent, child);
        return this;
    }

    public ConfigurationScenario index(String parent, String child)
    {
        withConfiguration("index", StreamProcessorCfg.class, parent, child);
        return this;

    }

    public ConfigurationScenario subscriptions(String parent, String child)
    {
        withConfiguration("subscriptions", SubscriptionCfg.class, parent, child);
        return this;
    }

    private <T> void withConfiguration(String key, Class<T> cls, String parent, String child)
    {
        specifications.add(new DirectorySpecification<T>(key, cls, new FileSpecification(parent, child)));
    }

    public List<DirectorySpecification<?>> getSpecifications()
    {
        return specifications;
    }

    public LogDirectoriesSpecification getLogDirectoriesSpecification()
    {
        return logDirectoriesSpecification;
    }

    public String getConfiguration()
    {
        return configuration;
    }

    public void assertDirectories(ConfigurationManager configurationManager)
    {
        final List<DirectorySpecification<?>> specifications = getSpecifications();
        for (DirectorySpecification<?> spec : specifications)
        {
            final String key = spec.getKey();
            final Class<?> type = spec.getType();

            final FileSpecification fileSpecification = spec.getFileSpecification();
            final String parent = fileSpecification.getParent();
            final String child = fileSpecification.getChild();

            final DirectoryConfiguration configuration = (DirectoryConfiguration) configurationManager.readEntry(key, type);
            final String configurationDirectory = configuration.getDirectory();

            assertThat(configurationDirectory)
                .contains(parent)
                .endsWith(child + File.separator);
        }
    }

    public void assertLogDirectories(ConfigurationManager configurationManager)
    {
        final LogStreamsCfg configuration = configurationManager.readEntry("logs", LogStreamsCfg.class);
        final String[] logDirectories = configuration.directories;
        final LogDirectoriesSpecification logDirectoriesSpecification = getLogDirectoriesSpecification();
        final List<FileSpecification> fileSpecifications = logDirectoriesSpecification.getFileSpecifications();

        assertThat(fileSpecifications).hasSize(logDirectories.length);

        for (FileSpecification fileSpec : fileSpecifications)
        {
            boolean found = false;
            for (int i = 0; i < logDirectories.length; i++)
            {
                final String logDirectory = logDirectories[i];
                if (logDirectory.contains(fileSpec.getParent()) && logDirectory.endsWith(fileSpec.getChild() + File.separator))
                {
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }
    }

    public static class DirectorySpecification<T>
    {
        private String key;
        private Class<T> type;
        private FileSpecification fileSpecification;

        public DirectorySpecification(String key, Class<T> type, FileSpecification fileSpecification)
        {
            this.key = key;
            this.type = type;
            this.fileSpecification = fileSpecification;
        }

        public String getKey()
        {
            return key;
        }

        public Class<T> getType()
        {
            return type;
        }

        public FileSpecification getFileSpecification()
        {
            return fileSpecification;
        }

    }

    public static class LogDirectoriesSpecification
    {
        private List<FileSpecification> fileSpecifications = new ArrayList<>();

        public String getKey()
        {
            return "logs";
        }

        public Class<LogStreamsCfg> getType()
        {
            return LogStreamsCfg.class;
        }

        public List<FileSpecification> getFileSpecifications()
        {
            return fileSpecifications;
        }

        public void addFileSpecification(FileSpecification fileSpecification)
        {
            fileSpecifications.add(fileSpecification);
        }

    }

    public class FileSpecification
    {
        private String parent;
        private String child;

        public FileSpecification(String parent, String child)
        {
            this.parent = parent;
            this.child = child;
        }

        public String getParent()
        {
            return parent;
        }

        public String getChild()
        {
            return child;
        }

    }
}
