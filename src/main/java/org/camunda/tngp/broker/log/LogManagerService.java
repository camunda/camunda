package org.camunda.tngp.broker.log;

import static org.camunda.tngp.broker.log.LogServiceNames.*;
import static org.camunda.tngp.broker.system.SystemServiceNames.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;

import org.camunda.tngp.broker.log.cfg.LogCfg;
import org.camunda.tngp.broker.log.cfg.LogComponentCfg;
import org.camunda.tngp.broker.servicecontainer.Service;
import org.camunda.tngp.broker.servicecontainer.ServiceContext;
import org.camunda.tngp.broker.services.DispatcherService;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.dispatcher.DispatcherBuilder;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.log.LogBuilder;
import org.camunda.tngp.log.Logs;

public class LogManagerService implements Service<LogManager>, LogManager
{
    protected ServiceContext serviceContext;
    protected LogComponentCfg logComponentConfig;
    protected List<LogCfg> logCfgs;

    public LogManagerService(ConfigurationManager configurationManager)
    {
        logComponentConfig = configurationManager.readEntry("logs", LogComponentCfg.class);
        logCfgs = configurationManager.readList("log", LogCfg.class);
    }

    @Override
    public void createLog(LogCfg config)
    {
        final String logName = config.name;
        if(logName == null || logName.isEmpty())
        {
            throw new IllegalArgumentException("logName cannot be null");
        }

        final int logId = config.id;
        if(logId < 0 || logId > Short.MAX_VALUE)
        {
            throw new IllegalArgumentException("log id cannot be null or greater than "+Short.MAX_VALUE);
        }

        String logDirectory = config.logDirectory;
        if(config.useTempLogDirectory)
        {
            try
            {
                final File tempDir = Files.createTempDirectory("tngp-log-").toFile();
                tempDir.deleteOnExit();
                System.out.println("Created temp directory for log "+logName + " at location "+tempDir);
                logDirectory = tempDir.getAbsolutePath();
            }
            catch (IOException e)
            {
                throw new RuntimeException("Could not create temp directory for log "+logName, e);
            }
        }
        else
        {
            if(logDirectory == null || logDirectory.isEmpty())
            {
                int assignedLogDirectory = 0;
                if(logComponentConfig.logDirectories.length ==0)
                {
                    throw new RuntimeException(String.format("Cannot start log %s, no log directory provided.", logName));
                }
                else if(logComponentConfig.logDirectories.length > 1)
                {
                    assignedLogDirectory = new Random().nextInt(logComponentConfig.logDirectories.length -1);
                }
                logDirectory = logComponentConfig.logDirectories[assignedLogDirectory] + File.separator + logName;
            }
        }

        int logSegmentSize = config.logSegmentSize;
        if(logSegmentSize == -1)
        {
            logSegmentSize = logComponentConfig.defaultLogSegmentSize;
        }
        logSegmentSize = logSegmentSize * 1024 * 1024;

        LogBuilder logBuilder = Logs.createLog(logName, logId)
            .logDirectory(logDirectory)
            .logSegmentSize(logSegmentSize);

        final LogService logService = new LogService(logBuilder);
        serviceContext.installService(logServiceName(logName), logService)
            .dependency(LOG_AGENT_CONTEXT_SERVICE, logService.getLogAgentContext())
            .done();
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        this.serviceContext = serviceContext;

        final int logWriteBufferSize = logComponentConfig.logWriteBufferSize * 1024 * 1024;

        final DispatcherBuilder writeBufferBuilder = Dispatchers.create(null)
                .bufferSize(logWriteBufferSize)
                .modePipeline(); // log write buffer is in pipeline mode

        final DispatcherService logWriteBufferService = new DispatcherService(writeBufferBuilder);
        serviceContext.installService(LOG_WRITE_BUFFER_SERVICE, logWriteBufferService)
            .dependency(AGENT_RUNNER_SERVICE, logWriteBufferService.getAgentRunnerInjector())
            .done();

        final LogAgentContextService logAgentContextService = new LogAgentContextService();
        serviceContext.installService(LOG_AGENT_CONTEXT_SERVICE, logAgentContextService)
            .dependency(LOG_WRITE_BUFFER_SERVICE, logAgentContextService.getLogWriteBufferInjector())
            .dependency(AGENT_RUNNER_SERVICE, logAgentContextService.getAgentRunnerServiceInjector())
            .done();

        for (LogCfg logCfg : logCfgs)
        {
            createLog(logCfg);
        }
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public LogManager get()
    {
        return this;
    }

}
