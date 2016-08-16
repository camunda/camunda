package org.camunda.tngp.log;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.DispatcherBuilder;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.log.appender.LogAppender;
import org.camunda.tngp.log.appender.LogSegmentAllocationDescriptor;
import org.camunda.tngp.log.conductor.LogConductor;

import uk.co.real_logic.agrona.ErrorHandler;
import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.agrona.concurrent.BackoffIdleStrategy;
import uk.co.real_logic.agrona.concurrent.CompositeAgent;
import uk.co.real_logic.agrona.concurrent.CountersManager;

public class LogBuilder
{
    static final ErrorHandler DEFAULT_ERROR_HANDLER = (t) ->
    {
        t.printStackTrace();
    };

    public enum ThreadingMode
    {
        SHARED,
        DEDICATED;
    }

    protected final String name;
    protected final int id;

    protected Dispatcher writeBuffer;

    protected int writeBufferSize = 1024  * 1024 * 16;

    protected String logRootPath;
    protected String logDirectory;

    protected int logSegmentSize = 1024 * 1024 * 128;

    protected int initialLogSegmentId = 0;

    protected CountersManager countersManager;

    protected ThreadingMode threadingMode = ThreadingMode.SHARED;

    protected boolean writeBufferExternallyManaged = false;
    protected boolean agentsExternallyManaged = false;
    protected LogAgentContext logAgentContext;

    protected LogConductor logConductor;
    protected LogAppender logAppender;
    protected boolean deleteOnClose;

    public LogBuilder(String name, int id)
    {
        this.name = name;
        this.id = id;
    }

    public LogBuilder writeBuffer(Dispatcher writeBuffer)
    {
        this.writeBuffer = writeBuffer;
        this.writeBufferExternallyManaged = true;
        return this;
    }

    public LogBuilder writeBufferSize(int writeBfferSize)
    {
        this.writeBufferSize = writeBfferSize;
        return this;
    }

    public LogBuilder logRootPath(String logRootPath)
    {
        this.logRootPath = logRootPath;
        return this;
    }

    public LogBuilder logDirectory(String logDir)
    {
        this.logDirectory = logDir;
        return this;
    }

    public LogBuilder initialLogSegmentId(int logFragmentId)
    {
        this.initialLogSegmentId = logFragmentId;
        return this;
    }

    public LogBuilder countersManager(CountersManager countersManager)
    {
        this.countersManager = countersManager;
        return this;
    }

    public LogBuilder threadingMode(ThreadingMode threadingMode)
    {
        this.threadingMode = threadingMode;
        return this;
    }

    public LogBuilder logSegmentSize(int logSegmentSize)
    {
        this.logSegmentSize = logSegmentSize;
        return this;
    }

    public LogBuilder logAgentContext(LogAgentContext logAgentContext)
    {
        this.logAgentContext = logAgentContext;
        this.agentsExternallyManaged = true;
        return this;
    }

    public LogBuilder deleteOnClose(boolean deleteOnClose)
    {
        this.deleteOnClose = deleteOnClose;
        return this;
    }

    public Log build()
    {
        final LogContext logContext = new LogContext(name, id);
        if (logDirectory == null)
        {
            logDirectory = logRootPath + File.separatorChar + name + File.separatorChar;
        }
        final File file = new File(logDirectory);
        file.mkdirs();

        logContext.setLogAllocationDescriptor(new LogSegmentAllocationDescriptor(logSegmentSize, logDirectory, initialLogSegmentId));

        logContext.setDeleteOnClose(deleteOnClose);

        if (!agentsExternallyManaged)
        {
            DispatcherConductor writeBufferConductor = null;
            if (!writeBufferExternallyManaged)
            {
                final DispatcherBuilder dispatcherBuilder = Dispatchers.create("log-write-buffer")
                        .bufferSize(writeBufferSize)
                        .conductorExternallyManaged();
                writeBuffer = dispatcherBuilder.build();
                writeBufferConductor = dispatcherBuilder.getConductorAgent();
            }
            logAgentContext = new LogAgentContext();

            logAgentContext.setWriteBuffer(writeBuffer);

            logConductor = new LogConductor(logAgentContext);
            logAppender = new LogAppender(logAgentContext);

            AgentRunner[] agentRunners = null;

            if (threadingMode == ThreadingMode.DEDICATED)
            {
                agentRunners = new AgentRunner[2];
                agentRunners[0] = startAgent(logAppender);
                if (writeBufferExternallyManaged)
                {
                    agentRunners[1] = startAgent(logConductor);
                }
                else
                {
                    agentRunners[1] = startAgent(new CompositeAgent(logConductor, writeBufferConductor));
                }
            }
            else
            {
                agentRunners = new AgentRunner[1];
                if (writeBufferExternallyManaged)
                {
                    agentRunners[0] = startAgent(new CompositeAgent(logAppender, logConductor));
                }
                else
                {
                    agentRunners[0] = startAgent(new CompositeAgent(logAppender, logConductor, writeBufferConductor));
                }
            }

            logAgentContext.setAgentRunners(agentRunners);
        }

        logContext.setWriteBuffer(logAgentContext.getWriteBuffer());
        logContext.setLogConductorCmdQueue(logAgentContext.getLogConductorCmdQueue());

        return new Log(logContext);
    }

    public LogAppender getLogAppender()
    {
        return logAppender;
    }

    public LogConductor getLogConductor()
    {
        return logConductor;
    }

    public String getLogDirectory()
    {
        return logDirectory;
    }

    private AgentRunner startAgent(Agent agent)
    {
        AtomicCounter errorCounter = null;
        if (countersManager != null)
        {
            errorCounter = countersManager.newCounter(String.format("%s.errorCounter", agent.roleName()));
        }

        final BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(100));
        final AgentRunner agentRunner = new AgentRunner(idleStrategy, DEFAULT_ERROR_HANDLER, errorCounter, agent);
        AgentRunner.startOnThread(agentRunner);

        return agentRunner;
    }

}
