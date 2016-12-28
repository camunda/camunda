package org.camunda.tngp.broker.logstreams;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP;
import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.logStreamServiceName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;

import org.camunda.tngp.broker.logstreams.cfg.LogStreamCfg;
import org.camunda.tngp.broker.logstreams.cfg.LogStreamsComponentCfg;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.agent.DedicatedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;

public class LogStreamsManagerService implements Service<LogStreamsManagerService>
{
    protected ServiceStartContext serviceContext;
    protected LogStreamsComponentCfg logComponentConfig;
    protected List<LogStreamCfg> logCfgs;

    public LogStreamsManagerService(ConfigurationManager configurationManager)
    {
        logComponentConfig = configurationManager.readEntry("logs", LogStreamsComponentCfg.class);
        logCfgs = configurationManager.readList("log", LogStreamCfg.class);
    }

    public void createLogStream(LogStreamCfg config)
    {
        final String logName = config.name;
        if (logName == null || logName.isEmpty())
        {
            throw new IllegalArgumentException("logName cannot be null");
        }

        final int logId = config.id;
        if (logId < 0 || logId > Short.MAX_VALUE)
        {
            throw new IllegalArgumentException("log id cannot be null or greater than " + Short.MAX_VALUE);
        }

        String logDirectory = config.logDirectory;
        boolean deleteOnExit = false;
        if (config.useTempLogDirectory)
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
            if (logDirectory == null || logDirectory.isEmpty())
            {
                int assignedLogDirectory = 0;
                if (logComponentConfig.logDirectories.length == 0)
                {
                    throw new RuntimeException(String.format("Cannot start log %s, no log directory provided.", logName));
                }
                else if (logComponentConfig.logDirectories.length > 1)
                {
                    assignedLogDirectory = new Random().nextInt(logComponentConfig.logDirectories.length - 1);
                }
                logDirectory = logComponentConfig.logDirectories[assignedLogDirectory] + File.separator + logName;
            }
        }

        int logSegmentSize = config.logSegmentSize;
        if (logSegmentSize == -1)
        {
            logSegmentSize = logComponentConfig.defaultLogSegmentSize;
        }
        logSegmentSize = logSegmentSize * 1024 * 1024;

        final LogStream logStream = LogStreams.createFsLogStream(logName, logId)
            .deleteOnClose(deleteOnExit)
            .logDirectory(logDirectory)
            .agentRunnerService(new DedicatedAgentRunnerService(new SimpleAgentRunnerFactory()))
            .logSegmentSize(logSegmentSize)
            .build();

        final LogStreamService logService = new LogStreamService(logStream);
        serviceContext.createService(logStreamServiceName(logName), logService)
            .group(LOG_STREAM_SERVICE_GROUP)
            .install();
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;

        serviceContext.run(() ->
        {
            for (LogStreamCfg logCfg : logCfgs)
            {
                createLogStream(logCfg);
            }
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        // nothing to do
    }

    @Override
    public LogStreamsManagerService get()
    {
        return this;
    }

}
