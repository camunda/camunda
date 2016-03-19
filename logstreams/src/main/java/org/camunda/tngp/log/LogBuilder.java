package org.camunda.tngp.log;

import java.util.concurrent.TimeUnit;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.DispatcherConductor;
import org.camunda.tngp.dispatcher.impl.DispatcherContext;
import org.camunda.tngp.log.appender.SegmentAllocationDescriptor;
import org.camunda.tngp.log.appender.Appender;

import uk.co.real_logic.agrona.ErrorHandler;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.agrona.concurrent.BackoffIdleStrategy;
import uk.co.real_logic.agrona.concurrent.CompositeAgent;
import uk.co.real_logic.agrona.concurrent.CountersManager;

public class LogBuilder
{

    static ErrorHandler DEFAULT_ERROR_HANDLER = (t) -> {
        t.printStackTrace();
    };

    static enum ThreadingMode
    {
        SHARED,
        DEDICATED;
    }

    protected final String name;

    protected Dispatcher writeBuffer;

    protected int writeBufferSize = 1024  * 1024 * 16;

    protected String logRootPath;

    protected int logFragmentSize = 1024 * 1024 * 128;

    protected int initialLogFragmentId = 0;

    protected CountersManager countersManager;

    protected ThreadingMode threadingMode = ThreadingMode.SHARED;

    public LogBuilder(String name)
    {
        this.name = name;
    }

    public LogBuilder writeBuffer(Dispatcher writeBuffer)
    {
        this.writeBuffer = writeBuffer;
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

    public LogBuilder initialLogFragmentId(int logFragmentId)
    {
        this.initialLogFragmentId = logFragmentId;
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

    public LogBuilder logFragementSize(int logFragementSize)
    {
        this.logFragmentSize = logFragementSize;
        return this;
    }

    public Log build()
    {
        final LogContext logContext = new LogContext();
        logContext.setLogAllocationDescriptor(new SegmentAllocationDescriptor(logFragmentSize, logRootPath));

        final LogConductor logConductor = new LogConductor(logContext);
        Agent conductorAgent = logConductor;

        if(writeBuffer == null)
        {
            final DispatcherContext dispatcherContext = new DispatcherContext();

            writeBuffer = Dispatchers.create("log-write-buffer")
                    .bufferSize(writeBufferSize)
                    .context(dispatcherContext)
                    .subscriberGroups(2)
                    .build();

            final DispatcherConductor dispatcherConductor = new DispatcherConductor(dispatcherContext, true);
            conductorAgent = new CompositeAgent(logConductor, dispatcherConductor);
        }
        logContext.setWriteBuffer(writeBuffer);

        final Appender logAppender = new Appender(logContext);

        AgentRunner[] agentRunners = null;

        if(threadingMode == ThreadingMode.DEDICATED)
        {
            agentRunners = new AgentRunner[2];
            agentRunners[0] = startAgent(conductorAgent, String.format("log.%s.conductor.errorCounter", name));
            agentRunners[1] = startAgent(logAppender, String.format("log.%s.conductor.errorCounter", name));
        }
        else {
            agentRunners = new AgentRunner[1];
            agentRunners[0] = startAgent(new CompositeAgent(conductorAgent, logAppender), String.format("log.%s.errorCounter", name));
        }

        logContext.setAgentRunners(agentRunners);

        if(writeBuffer.getStatus() == Dispatcher.STATUS_NEW)
        {
            try
            {
                writeBuffer.startSync();
            }
            catch (InterruptedException e)
            {

                LangUtil.rethrowUnchecked(e);
            }
        }

        return new Log(logContext);
    }

    private AgentRunner startAgent(Agent agent, String counterName)
    {
        AtomicCounter errorCounter = null;
        if(countersManager != null)
        {
            errorCounter = countersManager.newCounter(counterName);
        }

        BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(100));
        AgentRunner agentRunner = new AgentRunner(idleStrategy, DEFAULT_ERROR_HANDLER, errorCounter, agent);
        AgentRunner.startOnThread(agentRunner);

        return agentRunner;
    }
}
