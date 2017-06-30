package org.camunda.tngp.logstreams.impl;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;
import static org.camunda.tngp.logstreams.impl.LogStateMachineAgent.TRANSITION_CLOSE;
import static org.camunda.tngp.logstreams.impl.LogStateMachineAgent.TRANSITION_DEFAULT;
import static org.camunda.tngp.logstreams.impl.LogStateMachineAgent.TRANSITION_OPEN;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.logstreams.log.LogStreamFailureListener;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.Actor;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class LogStreamController implements Actor
{
    protected static final int TRANSITION_FAIL = 3;
    protected static final int TRANSITION_RECOVER = 5;

    // STATES /////////////////////////////////////////////////////////

    protected final OpeningState openingState = new OpeningState();
    protected final OpenState openState = new OpenState();
    protected final FailingState failingState = new FailingState();
    protected final FailedState failedState = new FailedState();
    protected final RecoveredState recoveredState = new RecoveredState();
    protected final ClosingState closingState = new ClosingState();
    protected final ClosedState closedState = new ClosedState();

    protected final LogStateMachineAgent stateMachine;

    //  MANDATORY //////////////////////////////////////////////////
    protected final String name;
    protected final LogStorage logStorage;
    protected final ActorScheduler actorScheduler;
    protected ActorReference controllerRef;
    protected ActorReference writeBufferRef;

    protected final BlockPeek blockPeek = new BlockPeek();
    protected final int maxAppendBlockSize;
    protected final Dispatcher writeBuffer;
    protected Subscription writeBufferSubscription;
    protected final Runnable openStateRunnable;
    protected final Runnable closedStateRunnable;

    protected List<LogStreamFailureListener> failureListeners = new ArrayList<>();

    public LogStreamController(LogStreamImpl.LogStreamBuilder logStreamBuilder)
    {
        this.name = logStreamBuilder.getLogName();
        this.logStorage = logStreamBuilder.getLogStorage();
        this.actorScheduler = logStreamBuilder.getActorScheduler();

        this.maxAppendBlockSize = logStreamBuilder.getMaxAppendBlockSize();
        this.writeBuffer = logStreamBuilder.getWriteBuffer();

        this.openStateRunnable = () ->
        {
            controllerRef = actorScheduler.schedule(this);
        };
        this.closedStateRunnable = () -> controllerRef.close();
        this.stateMachine = new LogStateMachineAgent(
            StateMachine.<LogContext>builder(s -> new LogContext(s))
                .initialState(closedState)
                .from(openingState).take(TRANSITION_DEFAULT).to(openState)
                .from(openingState).take(TRANSITION_FAIL).to(failingState)
                .from(openState).take(TRANSITION_FAIL).to(failingState)
                .from(openState).take(TRANSITION_CLOSE).to(closingState)
                .from(failingState).take(TRANSITION_DEFAULT).to(failedState)
                .from(failedState).take(TRANSITION_CLOSE).to(closingState)
                .from(failedState).take(TRANSITION_RECOVER).to(recoveredState)
                .from(recoveredState).take(TRANSITION_DEFAULT).to(openState)
                .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                .from(closedState).take(TRANSITION_OPEN).to(openingState)
                .build(), openStateRunnable, closedStateRunnable);
    }

    @Override
    public int doWork()
    {
        return getStateMachine().doWork();
    }

    @Override
    public String name()
    {
        return name;
    }

    protected LogStateMachineAgent getStateMachine()
    {
        return stateMachine;
    }

    protected class OpeningState implements TransitionState<LogContext>
    {
        @Override
        public void work(LogContext context)
        {
            try
            {
                if (!logStorage.isOpen())
                {
                    logStorage.open();
                }

                writeBufferSubscription = writeBuffer.getSubscriptionByName("log-appender");
                writeBufferRef = actorScheduler.schedule(writeBuffer.getConductor());

                context.take(TRANSITION_DEFAULT);
                stateMachine.completeOpenFuture(null);
            }
            catch (Exception e)
            {
                context.take(TRANSITION_FAIL);
                stateMachine.completeOpenFuture(e);
            }
        }
    }

    protected class OpenState implements State<LogContext>
    {
        @Override
        public int doWork(LogContext context)
        {
            final int bytesAvailable = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

            if (bytesAvailable > 0)
            {
                final ByteBuffer nioBuffer = blockPeek.getRawBuffer();
                final MutableDirectBuffer buffer = blockPeek.getBuffer();

                final long position = buffer.getLong(positionOffset(messageOffset(0)));
                context.setFirstEventPosition(position);

                final long address = logStorage.append(nioBuffer);

                if (address >= 0)
                {
                    blockPeek.markCompleted();
                }
                else
                {
                    blockPeek.markFailed();

                    context.take(TRANSITION_FAIL);
                }

                return 1;
            }
            else
            {
                return 0;
            }
        }
    }

    protected class FailingState implements TransitionState<LogContext>
    {
        @Override
        public void work(LogContext context)
        {
            for (int i = 0; i < failureListeners.size(); i++)
            {
                final LogStreamFailureListener logStreamWriteErrorListener = failureListeners.get(i);
                try
                {
                    logStreamWriteErrorListener.onFailed(context.getFirstEventPosition());
                }
                catch (Exception e)
                {
                    System.err.println("Exception while invoking " + logStreamWriteErrorListener + ".");
                }
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    protected class FailedState implements State<LogContext>
    {
        @Override
        public int doWork(LogContext context)
        {
            final int available = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

            if (available > 0)
            {
                blockPeek.markFailed();
            }

            return available;
        }
    }

    protected class RecoveredState implements TransitionState<LogContext>
    {
        @Override
        public void work(LogContext context)
        {
            for (int i = 0; i < failureListeners.size(); i++)
            {
                final LogStreamFailureListener logStreamWriteErrorListener = failureListeners.get(i);
                try
                {
                    logStreamWriteErrorListener.onRecovered();
                }
                catch (Exception e)
                {
                    System.err.println("Exception while invoking " + logStreamWriteErrorListener + ".");
                }
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    protected class ClosingState implements TransitionState<LogContext>
    {
        @Override
        public void work(LogContext context)
        {
            writeBufferRef.close();
            context.take(TRANSITION_DEFAULT);
        }
    }

    protected class ClosedState implements WaitState<LogContext>
    {
        @Override
        public void work(LogContext logContext) throws Exception
        {
            getStateMachine().closing();
        }
    }

    public boolean isRunning()
    {
        return getStateMachine().isRunning();
    }

    public void open()
    {
        getStateMachine().open();
    }

    public CompletableFuture<Void> openAsync()
    {
        return getStateMachine().openAsync();
    }

    public void close()
    {
        getStateMachine().close();
    }

    public CompletableFuture<Void> closeAsync()
    {
        return getStateMachine().closeAsync();
    }

    public void recover()
    {
        // TODO who take care of the log storage and invoke this method?

        stateMachine.addCommand(context ->
        {
            context.take(TRANSITION_RECOVER);
        });
    }

    public long getCurrentAppenderPosition()
    {
        if (writeBufferSubscription != null)
        {
            return writeBufferSubscription.getPosition();
        }
        else
        {
            return -1;
        }
    }

    public boolean isClosed()
    {
        return stateMachine.getCurrentState() == closedState;
    }

    public boolean isOpen()
    {
        return stateMachine.getCurrentState() == openState;
    }

    public boolean isFailed()
    {
        return stateMachine.getCurrentState() == failedState;
    }

    public void registerFailureListener(LogStreamFailureListener listener)
    {
        stateMachine.addCommand(context -> failureListeners.add(listener));
    }

    public void removeFailureListener(LogStreamFailureListener listener)
    {
        stateMachine.addCommand(context -> failureListeners.remove(listener));
    }

    public Dispatcher getWriteBuffer()
    {
        return writeBuffer;
    }
}
