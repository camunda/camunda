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
package io.zeebe.broker.clustering.base.raft.config;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.cfg.LogStreamsCfg;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

/**
 * Manages {@link RaftPersistentConfiguration} instances.
 * When the broker is started, it loads the stored files.
 * Knows where to put new configuration files when a new raft is started.
 */
public class RaftPersistentConfigurationManager extends Actor
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final List<RaftPersistentConfiguration> configurations = new ArrayList<>();
    private final String configurationStoreDirectory;
    private final LogStreamsCfg logStreamsCfg;

    public RaftPersistentConfigurationManager(String configurationStoreDirectory, LogStreamsCfg logStreamsCfg)
    {
        this.configurationStoreDirectory = configurationStoreDirectory;
        this.logStreamsCfg = logStreamsCfg;
    }

    @Override
    protected void onActorStarting()
    {
        final File[] configFiles = new File(configurationStoreDirectory).listFiles();

        if (configFiles != null && configFiles.length > 0)
        {
            for (int i = 0; i < configFiles.length; i++)
            {
                final String path = configFiles[i].getAbsolutePath();

                try
                {
                    configurations.add(new RaftPersistentConfiguration(path));
                }
                catch (Exception e)
                {
                    LOG.error("Could not load persistent raft configuration '" +
                            path + "', this broker will not join raft group.", e);
                }
            }
        }
    }

    public ActorFuture<List<RaftPersistentConfiguration>> getConfigurations()
    {
        return actor.call(() -> new ArrayList<>(configurations));
    }

    public ActorFuture<RaftPersistentConfiguration> createConfiguration(DirectBuffer topicName,
        int partitionId,
        int replicationFactor,
        List<SocketAddress> members)
    {
        final ActorFuture<RaftPersistentConfiguration> future = new CompletableActorFuture<>();
        actor.run(() ->
        {
            final boolean partitionExists = configurations.stream()
                    .anyMatch((config) -> config.getPartitionId() == partitionId);

            if (partitionExists)
            {
                future.completeExceptionally(new RuntimeException(String.format("Partition with with %d already exists", partitionId)));
            }
            else
            {

                final String logName = String.format("%s-%d", BufferUtil.bufferAsString(topicName), partitionId);
                final String filename = String.format("%s%s.meta", configurationStoreDirectory, logName);
                final RaftPersistentConfiguration storage = new RaftPersistentConfiguration(filename);

                final String[] logDirectories = logStreamsCfg.directories;
                final int assignedLogDirectory = ThreadLocalRandom.current().nextInt(logDirectories.length);

                storage.setLogDirectory(logDirectories[assignedLogDirectory].concat(logName))
                    .setTopicName(topicName)
                    .setPartitionId(partitionId)
                    .setReplicationFactor(replicationFactor)
                    .setMembers(members)
                    .save();

                configurations.add(storage);

                future.complete(storage);
            }
        });

        return future;
    }

    public ActorFuture<Void> deleteConfiguration(RaftPersistentConfiguration configuration)
    {
        return actor.call(() ->
        {
            configurations.remove(configuration);
            configuration.delete();
        });
    }

    public ActorFuture<Void> close()
    {
        return actor.close();
    }
}
