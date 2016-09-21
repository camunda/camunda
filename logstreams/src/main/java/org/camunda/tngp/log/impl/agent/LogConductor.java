package org.camunda.tngp.log.impl.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.ReadableLogEntry;
import org.camunda.tngp.log.impl.LogBlockIndex;
import org.camunda.tngp.log.impl.LogContext;
import org.camunda.tngp.log.impl.LogImpl;
import org.camunda.tngp.log.spi.LogStorage;

public class LogConductor implements Agent, Consumer<LogConductorCmd>
{
    protected final ManyToOneConcurrentArrayQueue<LogConductorCmd> cmdQueue;
    protected final ManyToOneConcurrentArrayQueue<LogAppenderCmd> appenderCmdQueue;
    protected final LogAgentContext agentContext;

    protected final List<LogImpl> logs = new ArrayList<>();

    public LogConductor(LogAgentContext agentContext)
    {
        this.agentContext = agentContext;
        this.cmdQueue = agentContext.getLogConductorCmdQueue();
        this.appenderCmdQueue = agentContext.getAppenderCmdQueue();
    }

    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(this);

        return workCount;
    }

    public String roleName()
    {
        return "log conductor";
    }

    @Override
    public void accept(LogConductorCmd c)
    {
        c.execute(this);
    }

    public void onLogOpened(LogImpl log, CompletableFuture<Log> future)
    {
        try
        {
            final LogContext logContext = log.getLogContext();
            final LogStorage logStorage = logContext.getLogStorage();

            logStorage.open();

            recoverBlockIndex(log);
            recoverPosition(log);

            logs.add(log);
            appenderCmdQueue.add((a) -> a.onLogOpened(log, future));
        }
        catch (Exception e)
        {
            future.completeExceptionally(e);
        }
    }

    protected void recoverBlockIndex(LogImpl log)
    {
        final LogContext logContext = log.getLogContext();
        final LogBlockIndex blockIndex = logContext.getBlockIndex();

        blockIndex.recover(log);
    }

    private void recoverPosition(LogImpl log)
    {
        long position = 0;

        final BufferedLogReader logReader = new BufferedLogReader(log);
        logReader.seekToLastEntry();

        if (logReader.hasNext())
        {
            final ReadableLogEntry lastEntry = logReader.next();
            position = lastEntry.getPosition() + 1;
        }

        log.getLogContext()
            .getPositionCounter()
            .set(position);
    }

    public void closeLog(final LogImpl log, final CompletableFuture<Void> closeFuture)
    {
        final CompletableFuture<Void> appenderCloseFuture = new CompletableFuture<>();

        appenderCloseFuture.whenComplete((l, t) ->
        {
            try
            {
                final LogContext logContext = log.getLogContext();
                final LogStorage logStorage = logContext.getLogStorage();

                logStorage.close();

                if (t == null)
                {
                    closeFuture.complete(null);
                }
                else
                {
                    closeFuture.completeExceptionally(t);
                }
            }
            catch (Exception e)
            {
                t.printStackTrace(); // log so it does not completely swallowed
                closeFuture.completeExceptionally(e);
            }

        });

        appenderCmdQueue.add((a) -> a.onLogClosed(log, appenderCloseFuture));
    }

    public CompletableFuture<Void> close()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        cmdQueue.add((c) -> c.close(future));

        return future;
    }

    public void close(final CompletableFuture<Void> future)
    {
        final Thread closeThread = new Thread("log-closer-thread")
        {
            @Override
            public void run()
            {

                if (!agentContext.isWriteBufferExternallyManaged())
                {
                    agentContext.getWriteBuffer().close();
                }

                try
                {
                    final AgentRunner[] agentRunners = agentContext.getAgentRunners();
                    if (agentRunners != null)
                    {
                        for (AgentRunner agentRunner : agentRunners)
                        {
                            agentRunner.close();
                        }
                    }
                }
                finally
                {
                    future.complete(null);
                }
            }
        };

        closeThread.start();
    }
}
