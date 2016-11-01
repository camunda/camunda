package org.camunda.tngp.logstreams.impl.processor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.status.Position;
import org.camunda.tngp.logstreams.EventLogger;
import org.camunda.tngp.logstreams.LogStreamFailureListener;
import org.camunda.tngp.logstreams.LogStreamReader;
import org.camunda.tngp.logstreams.LoggedEvent;
import org.camunda.tngp.logstreams.processor.ControlledEventLogger;
import org.camunda.tngp.logstreams.processor.RecoveryHandler;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class StreamProcessorControllerImpl implements Agent, StreamProcessorController
{
    private final ClosedState closedState = new ClosedState();
    private final OpeningState openingState = new OpeningState();
    private final RecoveringState recoveringState = new RecoveringState();
    private final PollStreamState pollStreamState = new PollStreamState();
    private final InvokeProcessorState invokeProcessorState = new InvokeProcessorState();
    private final WriteEventState writeEventState = new WriteEventState();
    private final FailedState failedState = new FailedState();
    private final ClosingState closingState = new ClosingState();

    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(16);
    protected final Consumer<Runnable> cmdConsumer = (r) ->
    {
        r.run();
    };

    protected final AtomicBoolean isRunning = new AtomicBoolean(false);

    protected final StreamProcessorContextImpl context;
    protected final LogStreamReader streamReader;
    protected final RecoveryHandler recoveryHandler;
    protected final StreamProcessor streamProcessor;
    protected final Position position;
    protected final ControlledEventLogger eventLogger;
    protected final FailureListener failureListener = new FailureListener();
    protected final AgentRunnerService agentRunnerService;
    protected final String name;

    protected LoggedEvent currentEvent;
    protected long lastWrittenPosition;

    private CompletableFuture<Void> openFuture;

    protected volatile ControllerState state = closedState;

    public StreamProcessorControllerImpl(String name, StreamProcessorContextImpl context)
    {
        this.name = name;
        this.context = context;
        this.streamReader = context.getStreamReader();
        this.recoveryHandler = context.getRecoveryHandler();
        this.streamProcessor = context.getStreamProcessor();
        this.position = context.getPosition();
        this.eventLogger = new ControlledEventLogger(context.getEventWriter());
        this.agentRunnerService = context.getAgentRunnerService();
    }

    @Override
    public String roleName()
    {
        return name;
    }

    public int doWork()
    {
        int workCount = 0;

        if (state.acceptsCmd())
        {
            workCount += cmdQueue.drain(cmdConsumer);
        }

        workCount += state.doWork();

        return workCount;
    }

    interface ControllerState
    {
        int doWork();

        default boolean acceptsCmd()
        {
            return false;
        }
    }

    class PollStreamState implements ControllerState
    {
        public int doWork()
        {
            int workCount = 0;

            if (streamReader.hasNext())
            {
                final LoggedEvent evt = streamReader.next();
                position.setOrdered(evt.getPosition());

                currentEvent = evt;
                state = invokeProcessorState;

                ++workCount;
            }

            return workCount;
        }

        @Override
        public boolean acceptsCmd()
        {
            return true;
        }
    }

    class InvokeProcessorState implements ControllerState
    {
        public int doWork()
        {
            int workCount = 0;

            try
            {
                streamProcessor.onEvent(currentEvent, eventLogger);

                if (eventLogger.isWriteRequested())
                {
                    state = writeEventState;
                }
                else
                {
                    state = pollStreamState;
                }

                ++workCount;
            }
            catch (Exception e)
            {
                e.printStackTrace();
                // TODO: if this is an unrecoverable error we should probably
                // transition into a "failed" state at some point
            }

            return workCount;
        }
    }

    class WriteEventState implements ControllerState
    {
        @Override
        public boolean acceptsCmd()
        {
            return true;
        }

        @Override
        public int doWork()
        {
            int workCount = 0;

            final EventLogger delegateLogger = eventLogger.getDelegate();

            final long position = delegateLogger.tryWrite();
            final boolean isWritten = position >= 0;

            if (isWritten)
            {
                lastWrittenPosition = position;
                eventLogger.reset();
                state = pollStreamState;

                ++workCount;
            }

            return workCount;
        }
    }

    class RecoveringState implements ControllerState
    {
        @Override
        public int doWork()
        {
            state = openingState;

            return 1;
        }
    }

    class OpeningState implements ControllerState
    {
        @Override
        public int doWork()
        {
            try
            {
                context.getTargetStream().registerFailureListener(failureListener);
                streamProcessor.onOpen(context);
                state = recoveringState;
            }
            catch (RuntimeException e)
            {
                state = closingState;
                openFuture.completeExceptionally(e);
                System.err.println("Exception while invoking streamProcessor.onOpen()");
                e.printStackTrace();
            }

            return 1;
        }
    }


    class ClosingState implements ControllerState
    {
        @Override
        public int doWork()
        {
            try
            {
                context.getTargetStream().removeFailureListener(failureListener);
                streamProcessor.onClose();
            }
            catch (RuntimeException e)
            {
                System.err.println("Exception while invoking streamProcessor.onClose()");
                e.printStackTrace();
            }
            finally
            {
                state = closedState;
            }

            return 1;
        }
    }

    class ClosedState implements ControllerState
    {
        @Override
        public boolean acceptsCmd()
        {
            return true;
        }

        @Override
        public int doWork()
        {
            if (isRunning.compareAndSet(true, false))
            {
                agentRunnerService.remove(StreamProcessorControllerImpl.this);
            }
            return 0;
        }
    }

    class FailedState implements ControllerState
    {
        @Override
        public boolean acceptsCmd()
        {
            return true;
        }

        @Override
        public int doWork()
        {
            return 0;
        }
    }

    class FailureListener implements LogStreamFailureListener
    {
        @Override
        public void onFailed(long failedPosition)
        {
            cmdQueue.add(() ->
            {
                if (state == pollStreamState || state == writeEventState)
                {
                    state = failedState;
                }
            });
        }

        @Override
        public void onRecovered()
        {
            cmdQueue.add(() ->
            {
                if (state == failedState)
                {
                    state = recoveringState;
                }
            });
        }
    }

    public CompletableFuture<Void> openAsync()
    {
        final CompletableFuture<Void> openFuture = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            if (state == closedState)
            {
                this.openFuture = openFuture;
                state = openingState;
            }
            else
            {
                openFuture.completeExceptionally(new IllegalStateException("Cannot open Stream processor: not closed."));
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

        return openFuture;

    }

    public void close()
    {
        cmdQueue.add(() ->
        {
            state = closingState;
        });
    }

}
