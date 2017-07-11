/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.impl;

import io.zeebe.dispatcher.BlockPeek;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.logstreams.log.LogStreamFailureListener;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.positionOffset;
import static io.zeebe.logstreams.impl.LogStateMachineAgent.*;

public class LogStreamController implements Actor
{
    public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

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
    protected String name;
    protected LogStorage logStorage;
    protected ActorScheduler actorScheduler;
    protected ActorReference controllerRef;
    protected ActorReference writeBufferRef;

    protected final BlockPeek blockPeek = new BlockPeek();
    protected int maxAppendBlockSize;
    protected Dispatcher writeBuffer;
    protected Subscription writeBufferSubscription;
    protected final Runnable openStateRunnable;
    protected final Runnable closedStateRunnable;

    protected List<LogStreamFailureListener> failureListeners = new ArrayList<>();

    public LogStreamController(LogStreamImpl.LogStreamBuilder logStreamBuilder)
    {
        wrap(logStreamBuilder);

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

    protected void wrap(LogStreamImpl.LogStreamBuilder logStreamBuilder)
    {
        this.name = logStreamBuilder.getLogName();
        this.logStorage = logStreamBuilder.getLogStorage();
        this.actorScheduler = logStreamBuilder.getActorScheduler();

        this.maxAppendBlockSize = logStreamBuilder.getMaxAppendBlockSize();
        this.writeBuffer = logStreamBuilder.getWriteBuffer();
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

    protected int getMaxAppendBlockSize()
    {
        return maxAppendBlockSize;
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
                    LOG.error("Exception while invoking {}", logStreamWriteErrorListener);
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
                    LOG.error("Exception while invoking {}", logStreamWriteErrorListener);
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
