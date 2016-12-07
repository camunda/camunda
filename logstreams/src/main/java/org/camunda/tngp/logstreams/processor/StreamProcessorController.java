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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.util.agent.AgentRunnerService;
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
    protected static final int TRANSITION_PROCESS = 3;

    protected final State<Context> openingState = new OpeningState();
    protected final State<Context> openedState = new OpenedState();
    protected final State<Context> processingState = new ProcessingState();
    protected final State<Context> closingState = new ClosingState();
    protected final State<Context> closedState = new ClosedState();

    protected final StateMachineAgent<Context> stateMachineAgent = new StateMachineAgent<>(StateMachine.<Context> builder(s -> new Context(s))
            .initialState(closedState)
            .from(openingState).take(TRANSITION_DEFAULT).to(openedState)
            .from(openedState).take(TRANSITION_PROCESS).to(processingState)
            .from(openedState).take(TRANSITION_CLOSE).to(closingState)
            .from(processingState).take(TRANSITION_DEFAULT).to(openedState)
            .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
            .from(closedState).take(TRANSITION_OPEN).to(openingState)
            .build());

    protected final StreamProcessor streamProcessor;
    protected final StreamProcessorContext streamProcessorContext;

    protected final LogStreamReader logStreamReader;
    protected final LogStreamWriter logStreamWriter;

    protected final AgentRunnerService agentRunnerService;
    protected final AtomicBoolean isRunning = new AtomicBoolean(false);

    public StreamProcessorController(StreamProcessorContext context)
    {
        this.streamProcessorContext = context;
        this.agentRunnerService = context.getAgentRunnerService();
        this.streamProcessor = context.getStreamProcessor();
        this.logStreamReader = context.getLogStreamReader();
        this.logStreamWriter = context.getLogStreamWriter();
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
        return stateMachineAgent.getCurrentState() == openedState || stateMachineAgent.getCurrentState() == processingState;
    }

    public boolean isClosed()
    {
        return stateMachineAgent.getCurrentState() == closedState;
    }

    private class OpeningState implements TransitionState<Context>
    {
        @Override
        public void work(Context context)
        {
            logStreamReader.wrap(streamProcessorContext.getSourceStream());
            logStreamWriter.wrap(streamProcessorContext.getTargetStream());

            streamProcessor.open(streamProcessorContext);

            context.take(TRANSITION_DEFAULT);
            context.completeFuture();
        }
    }

    private class OpenedState implements State<Context>
    {
        @Override
        public int doWork(Context context)
        {
            int workCount = 0;

            if (logStreamReader.hasNext())
            {
                workCount = 1;

                final LoggedEvent event = logStreamReader.next();
                context.setEvent(event);

                context.take(TRANSITION_PROCESS);
            }

            return workCount;
        }
    }

    private class ProcessingState implements TransitionState<Context>
    {
        @Override
        public void work(Context context)
        {
            final EventProcessor eventProcessor = streamProcessor.onEvent(context.getEvent());

            eventProcessor.processEvent();

            boolean executedSideEffect = false;
            do
            {
                executedSideEffect = eventProcessor.executeSideEffects();
            }
            while (!executedSideEffect);

            boolean wroteEvent = false;
            do
            {
                logStreamWriter.streamProcessorId(streamProcessorContext.getId());
                wroteEvent = eventProcessor.writeEvent(logStreamWriter);
            }
            while (!wroteEvent);

            eventProcessor.updateState();

            streamProcessor.afterEvent();

            context.take(TRANSITION_DEFAULT);
        }
    }

    private class ClosingState implements TransitionState<Context>
    {
        @Override
        public void work(Context context)
        {
            streamProcessor.close();

            context.take(TRANSITION_DEFAULT);
            context.completeFuture();
        }
    }

    private class ClosedState implements WaitState<Context>
    {
        @Override
        public void work(Context context)
        {
            if (isRunning.compareAndSet(true, false))
            {
                agentRunnerService.remove(StreamProcessorController.this);
            }
        }
    }

    private class Context extends SimpleStateMachineContext
    {
        private LoggedEvent event;
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

        public void setFuture(CompletableFuture<Void> future)
        {
            this.future = future;
        }

    }

}
