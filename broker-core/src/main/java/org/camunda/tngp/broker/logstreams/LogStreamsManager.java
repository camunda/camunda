package org.camunda.tngp.broker.logstreams;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Random;

import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.broker.logstreams.cfg.LogStreamsCfg;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.LogStream;

public class LogStreamsManager
{
    protected LogStreamsCfg logStreamsCfg;
    protected AgentRunnerServices agentRunner;
    protected Int2ObjectHashMap<LogStream> logStreams;

    public LogStreamsManager(final LogStreamsCfg logStreamsCfg, final AgentRunnerServices agentRunner)
    {
        this.logStreamsCfg = logStreamsCfg;
        this.agentRunner = agentRunner;
        this.logStreams = new Int2ObjectHashMap<>();
    }

    public Collection<LogStream> getLogStreams()
    {
        return logStreams.values();
    }

    public LogStream getLogStream(final int id)
    {
        return logStreams.get(id);
    }

    public LogStream createLogStream(final int logId, final String logName)
    {
        if (logName == null || logName.isEmpty())
        {
            throw new IllegalArgumentException("logName cannot be null");
        }

        if (logId < 0 || logId > Short.MAX_VALUE)
        {
            throw new IllegalArgumentException("log id cannot be null or greater than " + Short.MAX_VALUE);
        }

        final String logDirectory;
        boolean deleteOnExit = false;
        if (logStreamsCfg.useTempLogDirectory)
        {
            deleteOnExit = true;
            try
            {
                final File tempDir = Files.createTempDirectory("tngp-log-").toFile();
                System.out.format("Created temp directory for log %s at location %s. Will be deleted on exit.\n", logName, tempDir);
                logDirectory = tempDir.getAbsolutePath();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Could not create temp directory for log " + logName, e);
            }
        }
        else
        {
            int assignedLogDirectory = 0;
            if (logStreamsCfg.logDirectories.length == 0)
            {
                throw new RuntimeException(String.format("Cannot start log %s, no log directory provided.", logName));
            }
            else if (logStreamsCfg.logDirectories.length > 1)
            {
                assignedLogDirectory = new Random().nextInt(logStreamsCfg.logDirectories.length - 1);
            }
            logDirectory = logStreamsCfg.logDirectories[assignedLogDirectory] + File.separator + logName;
        }

        final int logSegmentSize = logStreamsCfg.defaultLogSegmentSize * 1024 * 1024;

        final LogStream logStream = LogStreams.createFsLogStream(logName, logId)
            .deleteOnClose(deleteOnExit)
            .logDirectory(logDirectory)
            .agentRunnerService(agentRunner.logAppenderAgentRunnerService())
            .logSegmentSize(logSegmentSize)
            .logStreamControllerDisabled(true)
            .build();

        logStreams.put(logId, logStream);
        logStream.open();

        return logStream;
    }

    public LogStream createLogStream(int id, String name, String logDirectory)
    {
        final LogStream logStream = LogStreams.createFsLogStream(name, id)
                .deleteOnClose(false)
                .logDirectory(logDirectory)
                .agentRunnerService(agentRunner.logAppenderAgentRunnerService())
                .logSegmentSize(logStreamsCfg.defaultLogSegmentSize * 1024 * 1024)
                .logStreamControllerDisabled(true)
                .build();

        logStreams.put(id, logStream);
        logStream.open();

        return logStream;
    }
}
