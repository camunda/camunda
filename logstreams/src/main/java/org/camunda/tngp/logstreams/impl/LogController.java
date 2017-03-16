package org.camunda.tngp.logstreams.impl;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.WaitState;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents the base class for the log controller.
 *
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public abstract class LogController implements Agent
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;

    protected final AtomicBoolean isRunning = new AtomicBoolean(false);

    // MANDATORY /////////////////////////////////////////////////////////

    protected final String name;
    protected final LogStorage logStorage;
    protected final LogBlockIndex blockIndex;
    protected final AgentRunnerService agentRunnerService;

    // OPTIONAL /////////////////////////////////////////////////////////

    protected CompletableFuture<Void> closeFuture;
    protected CompletableFuture<Void> openFuture;

    protected LogController(LogControllerBuilder logControllerBuilder)
    {
        this.name = logControllerBuilder.getLogName();
        this.logStorage = logControllerBuilder.getLogStorage();
        this.blockIndex = logControllerBuilder.getBlockIndex();
        this.agentRunnerService = logControllerBuilder.getAgentRunnerService();
    }

    public interface LogControllerBuilder
    {
        String getLogName();

        LogStorage getLogStorage();

        LogBlockIndex getBlockIndex();

        AgentRunnerService getAgentRunnerService();
    }

    @Override
    public String roleName()
    {
        return name;
    }

    @Override
    public int doWork()
    {
        return getStateMachine().doWork();
    }

    protected abstract StateMachineAgent<Context> getStateMachine();

    public void open()
    {
        try
        {
            openAsync().get();
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void close()
    {
        try
        {
            closeAsync().get();
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<Void> openAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        getStateMachine().addCommand(context ->
        {
            final boolean opening = context.tryTake(TRANSITION_OPEN);
            if (opening)
            {
                openFuture = future;
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot open log stream. State is not closed."));
            }
        });

        if (isRunning.compareAndSet(false, true))
        {
            try
            {
                agentRunnerService.run(this);
            }
            catch (Exception e)
            {
                isRunning.set(false);
                openFuture.completeExceptionally(e);
            }
        }

        return future;
    }

    public CompletableFuture<Void> closeAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        getStateMachine().addCommand(context ->
        {
            final boolean closing = context.tryTake(TRANSITION_CLOSE);
            if (closing)
            {
                closeFuture = future;
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot close log stream. State is not open."));
            }
        });

        return future;
    }

    protected class Context extends SimpleStateMachineContext
    {
        private long currentBlockAddress = 0L;
        private long lastPosition = 0;

        Context(StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        public long getLastPosition()
        {
            return lastPosition;
        }

        public void setLastPosition(long lastPosition)
        {
            this.lastPosition = lastPosition;
        }

        public long getCurrentBlockAddress()
        {
            return currentBlockAddress;
        }

        public void setCurrentBlockAddress(long currentBlockAddress)
        {
            this.currentBlockAddress = currentBlockAddress;
        }

        public void reset()
        {
            this.lastPosition = 0;
            this.currentBlockAddress = 0;
        }
    }

    class ClosedState implements WaitState<Context>
    {
        @Override
        public void work(Context context)
        {
            if (isRunning.compareAndSet(true, false))
            {
                closeFuture.complete(null);
                closeFuture = null;

                agentRunnerService.remove(LogController.this);
            }
        }
    }

    public boolean isRunning()
    {
        return isRunning.get();
    }
}
