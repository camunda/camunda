/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.logstreams.processor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamFailureListener;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.spi.ReadableSnapshot;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotWriter;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.state.ComposedState;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class StreamProcessorController implements Agent
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;
    protected static final int TRANSITION_FAIL = 3;
    protected static final int TRANSITION_PROCESS = 4;
    protected static final int TRANSITION_SNAPSHOT = 5;
    protected static final int TRANSITION_RECOVER = 6;

    protected final State<Context> openingState = new OpeningState();
    protected final State<Context> openedState = new OpenedState();
    protected final State<Context> processState = new ProcessState();
    protected final State<Context> snapshottingState = new SnapshottingState();
    protected final State<Context> recoveringState = new RecoveringState();
    protected final State<Context> reprocessingState = new ReprocessingState();
    protected final State<Context> closingSnapshottingState = new ClosingSnapshottingState();
    protected final State<Context> closingState = new ClosingState();
    protected final State<Context> closedState = new ClosedState();
    protected final State<Context> failedState = new FailedState();

    protected final StateMachineAgent<Context> stateMachineAgent = new StateMachineAgent<>(StateMachine.<Context> builder(s -> new Context(s))
            .initialState(closedState)
            .from(openingState).take(TRANSITION_DEFAULT).to(recoveringState)
            .from(openingState).take(TRANSITION_FAIL).to(failedState)
            .from(recoveringState).take(TRANSITION_DEFAULT).to(reprocessingState)
            .from(recoveringState).take(TRANSITION_FAIL).to(failedState)
            .from(reprocessingState).take(TRANSITION_DEFAULT).to(openedState)
            .from(reprocessingState).take(TRANSITION_FAIL).to(failedState)
            .from(openedState).take(TRANSITION_PROCESS).to(processState)
            .from(openedState).take(TRANSITION_CLOSE).to(closingSnapshottingState)
            .from(openedState).take(TRANSITION_FAIL).to(failedState)
            .from(processState).take(TRANSITION_DEFAULT).to(openedState)
            .from(processState).take(TRANSITION_SNAPSHOT).to(snapshottingState)
            .from(processState).take(TRANSITION_FAIL).to(failedState)
            .from(processState).take(TRANSITION_CLOSE).to(closingSnapshottingState)
            .from(snapshottingState).take(TRANSITION_DEFAULT).to(openedState)
            .from(snapshottingState).take(TRANSITION_FAIL).to(failedState)
            .from(snapshottingState).take(TRANSITION_CLOSE).to(closingSnapshottingState)
            .from(failedState).take(TRANSITION_CLOSE).to(closedState)
            .from(failedState).take(TRANSITION_OPEN).to(openedState)
            .from(failedState).take(TRANSITION_RECOVER).to(recoveringState)
            .from(closingSnapshottingState).take(TRANSITION_DEFAULT).to(closingState)
            .from(closingSnapshottingState).take(TRANSITION_FAIL).to(closingState)
            .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
            .from(closedState).take(TRANSITION_OPEN).to(openingState)
            .build());

    protected final StreamProcessor streamProcessor;
    protected final StreamProcessorContext streamProcessorContext;

    protected final ManyToOneConcurrentArrayQueue<StreamProcessorCommand> streamProcessorCmdQueue;
    protected final Consumer<StreamProcessorCommand> streamProcessorCmdConsumer = cmd -> cmd.execute();

    protected final LogStreamReader sourceLogStreamReader;
    protected final LogStreamReader targetLogStreamReader;
    protected final LogStreamWriter logStreamWriter;

    protected final SnapshotPolicy snapshotPolicy;
    protected final SnapshotStorage snapshotStorage;

    protected final LogStreamFailureListener targetLogStreamFailureListener = new TargetLogStreamFailureListener();

    protected final AgentRunnerService agentRunnerService;
    protected final AtomicBoolean isRunning = new AtomicBoolean(false);

    protected final EventFilter eventFilter;
    protected final EventFilter reprocessingEventFilter;

    public StreamProcessorController(StreamProcessorContext context)
    {
        this.streamProcessorContext = context;
        this.agentRunnerService = context.getAgentRunnerService();
        this.streamProcessor = context.getStreamProcessor();
        this.sourceLogStreamReader = context.getSourceLogStreamReader();
        this.targetLogStreamReader = context.getTargetLogStreamReader();
        this.logStreamWriter = context.getLogStreamWriter();
        this.snapshotPolicy = context.getSnapshotPolicy();
        this.snapshotStorage = context.getSnapshotStorage();
        this.streamProcessorCmdQueue = context.getStreamProcessorCmdQueue();
        this.eventFilter = context.getEventFilter();
        this.reprocessingEventFilter = context.getReprocessingEventFilter();
    }

    @Override
    public int doWork()
    {
        return stateMachineAgent.doWork();
    }

    @Override
    public String roleName()
    {
        return streamProcessorContext.getName();
    }

    public CompletableFuture<Void> openAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        stateMachineAgent.addCommand(context ->
        {
            final boolean opening = context.tryTake(TRANSITION_OPEN);
            if (opening)
            {
                context.setFuture(future);
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot open stream processor."));
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
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    public CompletableFuture<Void> closeAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        stateMachineAgent.addCommand(context ->
        {
            final boolean closing = context.tryTake(TRANSITION_CLOSE);
            if (closing)
            {
                context.setFuture(future);
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot close stream processor."));
            }
        });

        return future;
    }

    public boolean isOpen()
    {
        return stateMachineAgent.getCurrentState() == openedState
                || stateMachineAgent.getCurrentState() == processState
                || stateMachineAgent.getCurrentState() == snapshottingState;
    }

    public boolean isClosing()
    {
        return stateMachineAgent.getCurrentState() == closingState
                || stateMachineAgent.getCurrentState() == closingSnapshottingState;
    }

    public boolean isClosed()
    {
        return stateMachineAgent.getCurrentState() == closedState;
    }

    public boolean isFailed()
    {
        return stateMachineAgent.getCurrentState() == failedState;
    }

    protected boolean isSourceStreamWriter()
    {
        return streamProcessorContext.getTargetStream().getId() == streamProcessorContext.getSourceStream().getId();
    }

    public EventFilter getEventFilter()
    {
        return eventFilter;
    }

    public EventFilter getReprocessingEventFilter()
    {
        return reprocessingEventFilter;
    }

    protected final BiConsumer<Context, Exception> stateFailureHandler = (context, e) ->
    {
        e.printStackTrace();

        context.take(TRANSITION_FAIL);
        context.completeFutureExceptionally(e);
    };

    private class OpeningState implements TransitionState<Context>
    {
        @Override
        public void work(Context context)
        {
            final LogStream targetStream = streamProcessorContext.getTargetStream();

            targetLogStreamReader.wrap(targetStream);
            logStreamWriter.wrap(targetStream);
            sourceLogStreamReader.wrap(streamProcessorContext.getSourceStream());

            targetStream.removeFailureListener(targetLogStreamFailureListener);
            targetStream.registerFailureListener(targetLogStreamFailureListener);

            context.take(TRANSITION_DEFAULT);
        }

        @Override
        public void onFailure(Context context, Exception e)
        {
            stateFailureHandler.accept(context, e);
        }
    }

    private class OpenedState implements State<Context>
    {
        @Override
        public int doWork(Context context)
        {
            int workCount = 0;

            workCount += streamProcessorCmdQueue.drain(streamProcessorCmdConsumer);

            if (!streamProcessor.isSuspended() && sourceLogStreamReader.hasNext())
            {
                workCount += 1;

                final LoggedEvent event = sourceLogStreamReader.next();
                context.setEvent(event);

                if (eventFilter == null || eventFilter.applies(event))
                {
                    context.take(TRANSITION_PROCESS);
                }
            }

            return workCount;
        }

        @Override
        public void onFailure(Context context, Exception e)
        {
            stateFailureHandler.accept(context, e);
        }
    }

    private class ProcessState extends ComposedState<Context>
    {
        private EventProcessor eventProcessor;
        private long eventPosition;

        @Override
        protected List<Step<Context>> steps()
        {
            return Arrays.asList(
                    processEventStep,
                    sideEffectsStep,
                    writeEventStep,
                    updateStateStep);
        }

        private Step<Context> processEventStep = context ->
        {
            boolean processEvent = false;

            eventProcessor = streamProcessor.onEvent(context.getEvent());

            if (eventProcessor != null)
            {
                eventProcessor.processEvent();
                processEvent = true;
            }
            else
            {
                context.take(TRANSITION_DEFAULT);
            }
            return processEvent;
        };

        private Step<Context> sideEffectsStep = context -> eventProcessor.executeSideEffects();

        private Step<Context> writeEventStep = context ->
        {
            logStreamWriter
                .producerId(streamProcessorContext.getId())
                .sourceEvent(streamProcessorContext.getSourceStream().getId(), context.getEvent().getPosition());

            eventPosition = eventProcessor.writeEvent(logStreamWriter);
            return eventPosition >= 0;
        };

        private FailSafeStep<Context> updateStateStep = context ->
        {
            eventProcessor.updateState();

            streamProcessor.afterEvent();

            final boolean hasWrittenEvent = eventPosition > 0;
            if (hasWrittenEvent)
            {
                context.setLastWrittenEventPosition(eventPosition);
            }

            if (hasWrittenEvent && snapshotPolicy.apply(context.getEvent().getPosition()))
            {
                context.take(TRANSITION_SNAPSHOT);
            }
            else
            {
                context.take(TRANSITION_DEFAULT);
            }
        };

        @Override
        public void onFailure(Context context, Exception e)
        {
            stateFailureHandler.accept(context, new RuntimeException("log stream processor failed to process event. It stop processing further events.", e));
        }
    }

    /**
     * @param context
     * @return true if successful
     */
    protected boolean ensureSnapshotWritten(Context context)
    {
        final long sourceEventPosition = context.getEvent().getPosition();
        final long lastWrittenEventPosition = context.getLastWrittenEventPosition();
        final long appenderPosition = streamProcessorContext.getTargetStream().getCurrentAppenderPosition();

        final long snapshotPosition = isSourceStreamWriter() ? sourceEventPosition : lastWrittenEventPosition;
        final boolean snapshotAlreadyPresent = snapshotPosition <= context.getSnapshotPosition();

        if (!snapshotAlreadyPresent)
        {
            final boolean appenderCaughtUp = appenderPosition >= lastWrittenEventPosition;

            if (appenderCaughtUp)
            {
                writeSnapshot(context, snapshotPosition);
                return true;
            }
            else
            {
                return false;
            }
        }
        else
        {
            return true;
        }

    }

    protected void writeSnapshot(final Context context, final long eventPosition)
    {
        SnapshotWriter snapshotWriter = null;
        try
        {
            snapshotWriter = snapshotStorage.createSnapshot(streamProcessorContext.getName(), eventPosition);

            snapshotWriter.writeSnapshot(streamProcessor.getStateResource());
            snapshotWriter.commit();
            context.setSnapshotPosition(eventPosition);
        }
        catch (Exception e)
        {
            e.printStackTrace();

            if (snapshotWriter != null)
            {
                snapshotWriter.abort();
            }
        }
    }

    private class SnapshottingState implements State<Context>
    {
        @Override
        public int doWork(Context context)
        {
            int workCount = 0;

            final boolean snapshotWritten = ensureSnapshotWritten(context);

            if (snapshotWritten)
            {
                context.take(TRANSITION_DEFAULT);
                workCount += 1;
            }

            return workCount;
        }


    }

    private class RecoveringState implements TransitionState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            streamProcessor.getStateResource().reset();

            long snapshotPosition = -1;

            final ReadableSnapshot lastSnapshot = snapshotStorage.getLastSnapshot(streamProcessorContext.getName());

            if (lastSnapshot != null)
            {
                // recover last snapshot
                lastSnapshot.recoverFromSnapshot(streamProcessor.getStateResource());

                // read the last event from snapshot
                snapshotPosition = lastSnapshot.getPosition();
                final boolean found = targetLogStreamReader.seek(snapshotPosition);

                if (found && targetLogStreamReader.hasNext())
                {
                    final LoggedEvent lastEventFromSnapshot = targetLogStreamReader.next();

                    // resume the next position on source log stream to continue from
                    final long sourceEventPosition = isSourceStreamWriter() ? snapshotPosition : lastEventFromSnapshot.getSourceEventPosition();
                    sourceLogStreamReader.seek(sourceEventPosition + 1);
                }
                else
                {
                    throw new IllegalStateException("Cannot found event with the snapshot position in target log stream.");
                }
            }
            context.setSnapshotPosition(snapshotPosition);

            context.take(TRANSITION_DEFAULT);
        }

        @Override
        public void onFailure(Context context, Exception e)
        {
            stateFailureHandler.accept(context, e);
        }
    }

    private class ReprocessingState implements State<Context>
    {
        @Override
        public int doWork(Context context)
        {
            if (targetLogStreamReader.hasNext())
            {
                final LoggedEvent targetEvent = targetLogStreamReader.next();
                processEvent(context, targetEvent);
            }
            else
            {
                // all events are re-processed
                streamProcessor.onOpen(streamProcessorContext);

                context.take(TRANSITION_DEFAULT);
                context.completeFuture();
            }
            return 1;
        }

        protected void processEvent(Context context, final LoggedEvent targetEvent)
        {
            // ignore events from other producers
            if (targetEvent.getProducerId() == streamProcessorContext.getId() && (reprocessingEventFilter == null || reprocessingEventFilter.applies(targetEvent)))
            {
                final long sourceEventPosition = targetEvent.getSourceEventPosition();

                if (isSourceStreamWriter() && sourceEventPosition <= context.getSnapshotPosition())
                {
                    // ignore the event when it was processed before creating the snapshot
                    return;
                }

                // seek to the source event (assuming that the reader is near the position)
                LoggedEvent sourceEvent = null;
                long currentSourceEventPosition = -1;
                while (sourceLogStreamReader.hasNext() && currentSourceEventPosition < sourceEventPosition)
                {
                    sourceEvent = sourceLogStreamReader.next();
                    currentSourceEventPosition = sourceEvent.getPosition();
                }

                if (sourceEvent != null && currentSourceEventPosition == sourceEventPosition)
                {
                    // re-process the event from source stream
                    final EventProcessor eventProcessor = streamProcessor.onEvent(sourceEvent);
                    eventProcessor.processEvent();
                    eventProcessor.updateState();
                    streamProcessor.afterEvent();
                }
                else
                {
                    // source or target log is maybe corrupted
                    throw new IllegalStateException("Cannot find source event of written event: " + targetEvent);
                }
            }
        }

        @Override
        public void onFailure(Context context, Exception e)
        {
            stateFailureHandler.accept(context, e);
        }
    }

    private class ClosingSnapshottingState implements State<Context>
    {

        @Override
        public int doWork(Context context) throws Exception
        {
            final boolean hasProcessedAnyEvent = context.getEvent() != null;

            if (hasProcessedAnyEvent)
            {
                final boolean snapshotWritten = ensureSnapshotWritten(context);

                if (snapshotWritten)
                {
                    context.take(TRANSITION_DEFAULT);
                    return 1;
                }
                else
                {
                    return 0;
                }
            }
            else
            {
                context.take(TRANSITION_DEFAULT);
                return 1;
            }


        }
    }

    private class ClosingState implements State<Context>
    {
        @Override
        public int doWork(Context context)
        {
            streamProcessor.onClose();

            streamProcessorContext.getTargetStream().removeFailureListener(targetLogStreamFailureListener);

            context.take(TRANSITION_DEFAULT);
            return 1;
        }
    }

    private class ClosedState implements WaitState<Context>
    {
        @Override
        public void work(Context context)
        {
            if (isRunning.compareAndSet(true, false))
            {
                context.completeFuture();

                agentRunnerService.remove(StreamProcessorController.this);
            }
        }
    }

    private class FailedState implements WaitState<Context>
    {
        @Override
        public void work(Context context)
        {
            // wait for recovery
        }
    }

    private class TargetLogStreamFailureListener implements LogStreamFailureListener
    {
        @Override
        public void onFailed(long failedPosition)
        {
            stateMachineAgent.addCommand(context ->
            {
                final boolean failed = context.tryTake(TRANSITION_FAIL);
                if (failed)
                {
                    context.setFailedEventPosition(failedPosition);
                }
            });
        }

        @Override
        public void onRecovered()
        {
            stateMachineAgent.addCommand(context ->
            {
                final long failedEventPosition = context.getFailedEventPosition();
                if (failedEventPosition < 0)
                {
                    // ignore
                }
                else if (failedEventPosition <= context.getLastWrittenEventPosition())
                {
                    context.take(TRANSITION_RECOVER);
                }
                else
                {
                    // no recovery required if the log stream failed after all events of the processor are written
                    context.take(TRANSITION_OPEN);
                }
                context.setFailedEventPosition(-1);
            });
        }
    }

    private class Context extends SimpleStateMachineContext
    {
        private LoggedEvent event;
        private long lastWrittenEventPosition = -1;
        private long snapshotPosition = -1;
        private long failedEventPosition = -1;
        private CompletableFuture<Void> future;

        Context(StateMachine<Context> stateMachine)
        {
            super(stateMachine);
        }

        public LoggedEvent getEvent()
        {
            return event;
        }

        public void setEvent(LoggedEvent event)
        {
            this.event = event;
        }

        public void completeFuture()
        {
            if (future != null)
            {
                future.complete(null);
                future = null;
            }
        }

        public void completeFutureExceptionally(Throwable e)
        {
            if (future != null)
            {
                future.completeExceptionally(e);
                future = null;
            }
        }

        public void setFuture(CompletableFuture<Void> future)
        {
            this.future = future;
        }

        public long getLastWrittenEventPosition()
        {
            return lastWrittenEventPosition;
        }

        public void setLastWrittenEventPosition(long lastWrittenEventPosition)
        {
            this.lastWrittenEventPosition = lastWrittenEventPosition;
        }

        public long getFailedEventPosition()
        {
            return failedEventPosition;
        }

        public void setFailedEventPosition(long failedEventPosition)
        {
            this.failedEventPosition = failedEventPosition;
        }

        public void setSnapshotPosition(long snapshotPosition)
        {
            this.snapshotPosition = snapshotPosition;
        }

        public long getSnapshotPosition()
        {
            return snapshotPosition;
        }
    }

}
