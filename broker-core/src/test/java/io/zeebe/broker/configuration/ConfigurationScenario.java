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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.zeebe.broker.clustering.ClusterComponentConfiguration;
import io.zeebe.broker.clustering.base.gossip.BrokerGossipConfiguration;
import io.zeebe.broker.event.processor.SubscriptionCfg;
import io.zeebe.broker.logstreams.cfg.LogStreamsCfg;
import io.zeebe.broker.logstreams.cfg.SnapshotStorageCfg;
import io.zeebe.broker.logstreams.cfg.StreamProcessorCfg;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.DirectoryConfiguration;
import io.zeebe.broker.system.metrics.cfg.MetricsCfg;

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
        withConfiguration("network.management", ClusterComponentConfiguration.class, parent, child);
        return this;
    }

    public ConfigurationScenario gossip(String parent, String child)
    {
        withConfiguration("network.gossip", BrokerGossipConfiguration.class, parent, child);
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

    public ConfigurationScenario map(String parent, String child)
    {
        withConfiguration("map", StreamProcessorCfg.class, parent, child);
        return this;

    }

    public ConfigurationScenario subscriptions(String parent, String child)
    {
        withConfiguration("subscriptions", SubscriptionCfg.class, parent, child);
        return this;
    }

    private <T> void withConfiguration(String key, Class<T> cls, String parent, String child)
    {
        specifications.add(new DirectorySpecification<>(key, cls, new FileSpecification(parent, child)));
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

    public static class FileSpecification
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
