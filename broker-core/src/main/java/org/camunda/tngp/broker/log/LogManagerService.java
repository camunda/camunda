package org.camunda.tngp.broker.log;

import static org.camunda.tngp.broker.log.LogServiceNames.LOG_SERVICE_GROUP;
import static org.camunda.tngp.broker.log.LogServiceNames.logServiceName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.camunda.tngp.broker.log.cfg.LogCfg;
import org.camunda.tngp.broker.log.cfg.LogComponentCfg;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.logstreams.FsLogStreamBuilder;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.agent.DedicatedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;

public class LogManagerService implements Service<LogManager>, LogManager
{
    protected ServiceStartContext serviceContext;
    protected LogComponentCfg logComponentConfig;
    protected List<LogCfg> logCfgs;

    protected volatile LogStream[] logs = new LogStream[0];

    protected final ServiceGroupReference<LogStream> logServicesReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, service) ->
            {
                final ArrayList<LogStream> list = new ArrayList<>(Arrays.asList(logs));
                list.add(service);
                logs = list.toArray(new LogStream[list.size()]);
            })
            .onRemove((name, service) ->
            {
                final ArrayList<LogStream> list = new ArrayList<>(Arrays.asList(logs));
                list.remove(service);
                logs = list.toArray(new LogStream[list.size()]);
            })
            .build();

    public LogManagerService(ConfigurationManager configurationManager)
    {
        logComponentConfig = configurationManager.readEntry("logs", LogComponentCfg.class);
        logCfgs = configurationManager.readList("log", LogCfg.class);
    }

    @Override
    public void createLog(LogCfg config)
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

        final FsLogStreamBuilder logBuilder = LogStreams.createFsLogStream(logName, logId)
            .deleteOnClose(deleteOnExit)
            .logDirectory(logDirectory)
            .agentRunnerService(new DedicatedAgentRunnerService(new SimpleAgentRunnerFactory()))
            .logSegmentSize(logSegmentSize);

        final LogService logService = new LogService(logBuilder);
        serviceContext.createService(logServiceName(logName), logService)
            .group(LOG_SERVICE_GROUP)
            .install();
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        this.serviceContext = serviceContext;

        serviceContext.run(() ->
        {
            for (LogCfg logCfg : logCfgs)
            {
                createLog(logCfg);
            }
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        // nothing to do
    }

    @Override
    public LogManager get()
    {
        return this;
    }

    public LogStream getLogById(int id)
    {
        final LogStream[] logsCopy = logs;

        for (int i = 0; i < logsCopy.length; i++)
        {
            final LogStream log = logsCopy[i];
            if (log.getId() == id)
            {
                return log;
            }
        }

        return null;
    }

    public ServiceGroupReference<LogStream> getLogServicesReference()
    {
        return logServicesReference;
    }

}
