package org.camunda.tngp.logstreams.impl;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.logstreams.log.LogStreamFailureListener;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;

public class LogStreamController extends LogController
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

    protected final StateMachineAgent<Context> stateMachine = new StateMachineAgent<>(
        StateMachine.<Context>builder(s -> new Context(s))
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
            .build()
    );

    //  MANDATORY //////////////////////////////////////////////////
    protected final BlockPeek blockPeek = new BlockPeek();
    protected final int maxAppendBlockSize;
    protected final Dispatcher writeBuffer;
    protected final AgentRunnerService writeBufferAgentRunnerService;
    protected Subscription writeBufferSubscription;

    protected List<LogStreamFailureListener> failureListeners = new ArrayList<>();

    public LogStreamController(LogStreamControllerBuilder logStreamControllerBuilder)
    {
        super(logStreamControllerBuilder);
        this.maxAppendBlockSize = logStreamControllerBuilder.getMaxAppendBlockSize();
        this.writeBuffer = logStreamControllerBuilder.getWriteBuffer();
        this.writeBufferAgentRunnerService = logStreamControllerBuilder.getWriteBufferAgentRunnerService();
    }

    public interface LogStreamControllerBuilder extends LogControllerBuilder
    {
        int getMaxAppendBlockSize();

        Dispatcher getWriteBuffer();

        AgentRunnerService getWriteBufferAgentRunnerService();
    }

    @Override
    protected StateMachineAgent<Context> getStateMachine()
    {
        return stateMachine;
    }

    class OpeningState implements TransitionState<Context>
    {
        @Override
        public void work(Context context)
        {
            try
            {
                if (!logStorage.isOpen())
                {
                    logStorage.open();
                }

                writeBufferSubscription = writeBuffer.getSubscriptionByName("log-appender");
                writeBufferAgentRunnerService.run(writeBuffer.getConductorAgent());

                context.take(TRANSITION_DEFAULT);
                openFuture.complete(null);
            }
            catch (Exception e)
            {
                context.take(TRANSITION_FAIL);

                openFuture.completeExceptionally(e);
            }
            finally
            {
                openFuture = null;
            }
        }
    }

    class OpenState implements State<Context>
    {
        @Override
        public int doWork(Context context)
        {
            final int bytesAvailable = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

            if (bytesAvailable > 0)
            {
                final ByteBuffer nioBuffer = blockPeek.getRawBuffer();
                final MutableDirectBuffer buffer = blockPeek.getBuffer();

                final long position = buffer.getLong(positionOffset(messageOffset(0)));
                context.setLastPosition(position);

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

    class FailingState implements TransitionState<Context>
    {
        @Override
        public void work(Context context)
        {
            for (int i = 0; i < failureListeners.size(); i++)
            {
                final LogStreamFailureListener logStreamWriteErrorListener = failureListeners.get(i);
                try
                {
                    logStreamWriteErrorListener.onFailed(context.getLastPosition());
                }
                catch (Exception e)
                {
                    System.err.println("Exception while invoking " + logStreamWriteErrorListener + ".");
                }
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    class FailedState implements State<Context>
    {
        @Override
        public int doWork(Context context)
        {
            final int available = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

            if (available > 0)
            {
                blockPeek.markFailed();
            }

            return available;
        }
    }

    class RecoveredState implements TransitionState<Context>
    {
        @Override
        public void work(Context context)
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

    class ClosingState implements TransitionState<Context>
    {

        @Override
        public void work(Context context)
        {
            final Agent conductorAgent = writeBuffer.getConductorAgent();
            writeBufferAgentRunnerService.remove(conductorAgent);
            context.take(TRANSITION_DEFAULT);
        }

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

}
