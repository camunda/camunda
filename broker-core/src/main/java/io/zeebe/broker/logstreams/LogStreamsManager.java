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
package io.zeebe.broker.logstreams;

import static io.zeebe.util.EnsureUtil.ensureGreaterThanOrEqual;
import static io.zeebe.util.EnsureUtil.ensureLessThanOrEqual;
import static io.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.zeebe.broker.logstreams.cfg.LogStreamsCfg;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.fs.FsLogStreamBuilder;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.actor.ActorScheduler;


public class LogStreamsManager
{
    protected LogStreamsCfg logStreamsCfg;
    protected ActorScheduler actorScheduler;
    protected Map<DirectBuffer, Int2ObjectHashMap<LogStream>> logStreams;

    public LogStreamsManager(final LogStreamsCfg logStreamsCfg, final ActorScheduler actorScheduler)
    {
        this.logStreamsCfg = logStreamsCfg;
        this.actorScheduler = actorScheduler;
        this.logStreams = new HashMap<>();
    }

    public void forEachLogStream(Consumer<LogStream> consumer)
    {
        // TODO(menski): probably not garbage free
        logStreams.forEach((topicName, partitions) ->
            partitions.forEach((partitionId, logStream) ->
                consumer.accept(logStream))
        );
    }


    public LogStream getLogStream(final DirectBuffer topicName, final int partitionId)
    {
        final Int2ObjectHashMap<LogStream> logStreamPartitions = logStreams.get(topicName);

        if (logStreamPartitions != null)
        {
            return logStreamPartitions.get(partitionId);
        }

        return null;
    }

    /**
     * Creates a new log stream but does not open it. The caller has to call {@link LogStream#openAsync()} or
     * {@link LogStream#open()} before using it.
     *
     * @return the newly created log stream
     */
    public LogStream createLogStream(final DirectBuffer topicName, final int partitionId)
    {
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
        ensureLessThanOrEqual("partition id", partitionId, Short.MAX_VALUE);

        final FsLogStreamBuilder logStreamBuilder = LogStreams.createFsLogStream(topicName, partitionId);
        final String logName = logStreamBuilder.getLogName();

        final String logDirectory;
        final boolean deleteOnExit = false;

        int assignedLogDirectory = 0;
        if (logStreamsCfg.directories.length == 0)
        {
            throw new RuntimeException(String.format("Cannot start log %s, no log directory provided.", logName));
        }
        else if (logStreamsCfg.directories.length > 1)
        {
            assignedLogDirectory = new Random().nextInt(logStreamsCfg.directories.length - 1);
        }
        logDirectory = logStreamsCfg.directories[assignedLogDirectory] + File.separator + logName;


        final int logSegmentSize = logStreamsCfg.defaultLogSegmentSize * 1024 * 1024;

        final LogStream logStream = logStreamBuilder
            .deleteOnClose(deleteOnExit)
            .logDirectory(logDirectory)
            .actorScheduler(actorScheduler)
            .logSegmentSize(logSegmentSize)
            .logStreamControllerDisabled(true)
            .build();

        addLogStream(logStream);

        return logStream;
    }

    public LogStream createLogStream(final DirectBuffer topicName, final int partitionId, final String logDirectory)
    {
        final LogStream logStream =
            LogStreams.createFsLogStream(topicName, partitionId)
                      .deleteOnClose(false)
                      .logDirectory(logDirectory)
                      .actorScheduler(actorScheduler)
                      .logSegmentSize(logStreamsCfg.defaultLogSegmentSize * 1024 * 1024)
                      .logStreamControllerDisabled(true)
                      .build();

        addLogStream(logStream);

        return logStream;
    }

    private void addLogStream(final LogStream logStream)
    {
        logStreams
            .computeIfAbsent(logStream.getTopicName(), k -> new Int2ObjectHashMap<>())
            .put(logStream.getPartitionId(), logStream);
    }
}
