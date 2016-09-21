package org.camunda.tngp.log;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.CompositeAgent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.DispatcherBuilder;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.log.fs.FsLogStorage;
import org.camunda.tngp.log.fs.FsStorageConfiguration;
import org.camunda.tngp.log.impl.LogBlockIndex;
import org.camunda.tngp.log.impl.LogContext;
import org.camunda.tngp.log.impl.LogImpl;
import org.camunda.tngp.log.impl.agent.LogAgentContext;
import org.camunda.tngp.log.impl.agent.LogAppendHandler;
import org.camunda.tngp.log.impl.agent.LogAppender;
import org.camunda.tngp.log.impl.agent.LogConductor;
import org.camunda.tngp.log.impl.agent.LogConductorCmd;

public class FsLogBuilder
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

    public FsLogBuilder(String name, int id)
    {
        this.name = name;
        this.id = id;
    }

    public FsLogBuilder writeBuffer(Dispatcher writeBuffer)
    {
        this.writeBuffer = writeBuffer;
        this.writeBufferExternallyManaged = true;
        return this;
    }

    public FsLogBuilder writeBufferSize(int writeBfferSize)
    {
        this.writeBufferSize = writeBfferSize;
        return this;
    }

    public FsLogBuilder logRootPath(String logRootPath)
    {
        this.logRootPath = logRootPath;
        return this;
    }

    public FsLogBuilder logDirectory(String logDir)
    {
        this.logDirectory = logDir;
        return this;
    }

    public FsLogBuilder initialLogSegmentId(int logFragmentId)
    {
        this.initialLogSegmentId = logFragmentId;
        return this;
    }

    public FsLogBuilder countersManager(CountersManager countersManager)
    {
        this.countersManager = countersManager;
        return this;
    }

    public FsLogBuilder threadingMode(ThreadingMode threadingMode)
    {
        this.threadingMode = threadingMode;
        return this;
    }

    public FsLogBuilder logSegmentSize(int logSegmentSize)
    {
        this.logSegmentSize = logSegmentSize;
        return this;
    }

    public FsLogBuilder logAgentContext(LogAgentContext logAgentContext)
    {
        this.logAgentContext = logAgentContext;
        this.agentsExternallyManaged = true;
        return this;
    }

    public FsLogBuilder deleteOnClose(boolean deleteOnClose)
    {
        this.deleteOnClose = deleteOnClose;
        return this;
    }

    public Future<Log> build()
    {
        final LogContext logContext = new LogContext();

        logContext.setLogId(id);
        logContext.setLogName(name);

        if (logDirectory == null)
        {
            logDirectory = logRootPath + File.separatorChar + name + File.separatorChar;
        }
        final File file = new File(logDirectory);
        file.mkdirs();

        final FsStorageConfiguration storageConfig = new FsStorageConfiguration(logSegmentSize,
                logDirectory,
                initialLogSegmentId,
                deleteOnClose);

        final FsLogStorage storage = new FsLogStorage(logContext, storageConfig);

        logContext.setLogStorage(storage);

        if (!agentsExternallyManaged)
        {
            DispatcherConductor writeBufferConductor = null;
            if (!writeBufferExternallyManaged)
            {
                final DispatcherBuilder dispatcherBuilder = Dispatchers.create("log-write-buffer")
                        .bufferSize(writeBufferSize)
                        .subscriptions("log-appender")
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

        logContext.setBlockIndex(new LogBlockIndex(100000, (c) ->
        {
            return new UnsafeBuffer(ByteBuffer.allocate(c));
        }));

        final UnsafeBuffer countersBuffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
        final UnsafeBuffer metafataBuffer = new UnsafeBuffer(ByteBuffer.allocate(2048));

        final CountersManager countersManager = new CountersManager(metafataBuffer, countersBuffer);
        logContext.setPositionCounter(countersManager.newCounter(String.format("%s.position", name)));

        final ManyToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue = logAgentContext.getLogConductorCmdQueue();
        logContext.setToConductorCmdQueue(logConductorCmdQueue);

        logContext.setLogAppendHandler(new LogAppendHandler(logContext));

        final CompletableFuture<Log> logFuture = new CompletableFuture<>();
        final LogImpl logImpl = new LogImpl(logContext);

        logConductorCmdQueue.add((c) ->
        {
            c.onLogOpened(logImpl, logFuture);
        });

        return logFuture;
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
