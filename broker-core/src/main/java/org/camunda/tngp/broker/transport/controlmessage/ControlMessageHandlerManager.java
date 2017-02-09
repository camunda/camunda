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
package org.camunda.tngp.broker.transport.controlmessage;

import static org.camunda.tngp.broker.services.DispatcherSubscriptionNames.TRANSPORT_CONTROL_MESSAGE_HANDLER_SUBSCRIPTION;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestDecoder;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;
import org.camunda.tngp.util.time.ClockUtil;

public class ControlMessageHandlerManager implements Agent
{
    protected static final String NAME = "control.message.handler";

    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;
    protected static final int TRANSITION_PROCESS = 3;
    protected static final int TRANSITION_FAILED = 4;

    protected final State<Context> openingState = new OpeningState();
    protected final State<Context> openedState = new OpenedState();
    protected final State<Context> processingState = new ProcessingState();
    protected final State<Context> processingFailedState = new ProcessingFailedState();
    protected final State<Context> closedState = new ClosedState();

    protected final StateMachineAgent<Context> stateMachineAgent = new StateMachineAgent<>(StateMachine.<Context> builder(s -> new Context(s))
            .initialState(closedState)
            .from(openingState).take(TRANSITION_DEFAULT).to(openedState)
            .from(openedState).take(TRANSITION_PROCESS).to(processingState)
            .from(openedState).take(TRANSITION_FAILED).to(processingFailedState)
            .from(openedState).take(TRANSITION_CLOSE).to(closedState)
            .from(processingState).take(TRANSITION_DEFAULT).to(openedState)
            .from(processingFailedState).take(TRANSITION_DEFAULT).to(openedState)
            .from(closedState).take(TRANSITION_OPEN).to(openingState)
            .build());

    protected final AgentRunnerService agentRunnerService;
    protected final AtomicBoolean isRunning = new AtomicBoolean(false);

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ControlMessageRequestDecoder requestDecoder = new ControlMessageRequestDecoder();

    protected byte[] requestRawBuffer = new byte[1024 * 1024];
    protected final UnsafeBuffer requestBuffer = new UnsafeBuffer(requestRawBuffer);

    protected final Dispatcher controlMessageDispatcher;
    protected Subscription subscription;

    protected final Int2ObjectHashMap<ControlMessageHandler> handlersByTypeId = new Int2ObjectHashMap<>();

    protected final ErrorResponseWriter errorResponseWriter;
    protected final BrokerEventMetadata eventMetada = new BrokerEventMetadata();

    protected final long requestTimeoutInMillis;

    public ControlMessageHandlerManager(
            Dispatcher controlMessageDispatcher,
            ErrorResponseWriter errorResponseWriter,
            long requestTimeoutInMillis,
            AgentRunnerService agentRunnerService,
            List<ControlMessageHandler> handlers)
    {
        this.agentRunnerService = agentRunnerService;
        this.controlMessageDispatcher = controlMessageDispatcher;
        this.requestTimeoutInMillis = requestTimeoutInMillis;
        this.errorResponseWriter = errorResponseWriter;

        for (ControlMessageHandler handler : handlers)
        {
            final ControlMessageType messageType = handler.getMessageType();
            handlersByTypeId.put(messageType.value(), handler);
        }
    }

    @Override
    public String roleName()
    {
        return NAME;
    }

    @Override
    public int doWork()
    {
        return stateMachineAgent.doWork();
    }

    public CompletableFuture<Void> openAsync()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        stateMachineAgent.addCommand(context ->
        {
            final boolean opening = context.tryTake(TRANSITION_OPEN);
            if (opening)
            {
                context.setOpenCloseFuture(future);
            }
            else
            {
                future.completeExceptionally(new IllegalStateException("Cannot open control message handler."));
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
                context.setOpenCloseFuture(future);
            }
            else
            {
                future.complete(null);
            }
        });

        return future;
    }

    public boolean isOpen()
    {
        return stateMachineAgent.getCurrentState() == openedState
                || stateMachineAgent.getCurrentState() == processingState
                || stateMachineAgent.getCurrentState() == processingFailedState;
    }

    public boolean isClosed()
    {
        return stateMachineAgent.getCurrentState() == closedState;
    }

    class OpeningState implements TransitionState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            subscription = controlMessageDispatcher.getSubscriptionByName(TRANSPORT_CONTROL_MESSAGE_HANDLER_SUBSCRIPTION);

            context.take(TRANSITION_DEFAULT);
            context.completeOpenCloseFuture();
        }
    }

    class OpenedState implements State<Context>, FragmentHandler
    {
        private Context context;

        @Override
        public int doWork(Context context) throws Exception
        {
            this.context = context;

            int workCount = 0;

            final int result = subscription.poll(this, 1);
            if (result > 0)
            {
                workCount += 1;
                // the next transition is taken while handle the fragment
            }

            return workCount;
        }

        @Override
        public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
        {
            requestDecoder.wrap(buffer, offset, requestDecoder.sbeBlockLength(), requestDecoder.sbeSchemaVersion());

            final ControlMessageType messageType = requestDecoder.messageType();

            ensureBufferCapacity(requestDecoder.dataLength());
            requestDecoder.getData(requestBuffer, 0, requestDecoder.dataLength());

            context.lastRequestMessageType(messageType);

            eventMetada.reset();

            eventMetada
                .reqChannelId(requestDecoder.reqChannelId())
                .reqConnectionId(requestDecoder.reqConnectionId())
                .reqRequestId(requestDecoder.reqRequestId());

            final ControlMessageHandler handler = handlersByTypeId.get(messageType.value());
            if (handler != null)
            {
                final CompletableFuture<Void> future = handler.handle(requestBuffer, eventMetada);
                final long startTime = ClockUtil.getCurrentTimeInMillis();

                context.scheduledProcessing(future, startTime);

                context.take(TRANSITION_PROCESS);
            }
            else
            {
                context.take(TRANSITION_FAILED);
            }

            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }

        protected void ensureBufferCapacity(int length)
        {
            if (length <= requestRawBuffer.length)
            {
                Arrays.fill(requestRawBuffer, (byte) 0);
            }
            else
            {
                requestRawBuffer = new byte[length];
            }
        }
    }

    class ProcessingState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            final CompletableFuture<Void> future = context.getProcessingFuture();
            final long startTime = context.getProcessingStartTime();

            if (future.isDone())
            {
                context.take(TRANSITION_DEFAULT);
            }
            else if (hasTimeout(startTime))
            {
                errorResponseWriter
                    .errorCode(ErrorCode.REQUEST_TIMEOUT)
                    .errorMessage("Timeout while handle control message.")
                    .failedRequest(requestBuffer, 0, requestBuffer.capacity())
                    .metadata(eventMetada)
                    .tryWriteResponseOrLogFailure();

                context.take(TRANSITION_DEFAULT);
            }
        }

        protected boolean hasTimeout(long startTime)
        {
            return ClockUtil.getCurrentTimeInMillis() >= startTime + requestTimeoutInMillis;
        }
    }

    class ProcessingFailedState implements TransitionState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            errorResponseWriter
                .errorCode(ErrorCode.MESSAGE_NOT_SUPPORTED)
                .errorMessage("Cannot handle control message with type '%s'.", context.getLastRequestMessageType().name())
                .failedRequest(requestBuffer, 0, requestBuffer.capacity())
                .metadata(eventMetada)
                .tryWriteResponseOrLogFailure();

            context.take(TRANSITION_DEFAULT);
        }
    }

    class ClosedState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            if (isRunning.compareAndSet(true, false))
            {
                context.completeOpenCloseFuture();

                agentRunnerService.remove(ControlMessageHandlerManager.this);
            }
        }
    }

    class Context extends SimpleStateMachineContext
    {
        private CompletableFuture<Void> openClosefuture;
        private CompletableFuture<Void> processingFuture;
        private long processingStartTime = -1;
        private ControlMessageType lastRequestMessageType;

        Context(StateMachine<Context> stateMachine)
        {
            super(stateMachine);
        }

        public void lastRequestMessageType(ControlMessageType messageType)
        {
            this.lastRequestMessageType = messageType;
        }

        public void scheduledProcessing(CompletableFuture<Void> future, long startTime)
        {
            this.processingFuture = future;
            this.processingStartTime = startTime;
        }

        public CompletableFuture<Void> getProcessingFuture()
        {
            return processingFuture;
        }

        public long getProcessingStartTime()
        {
            return processingStartTime;
        }

        public void setOpenCloseFuture(CompletableFuture<Void> future)
        {
            this.openClosefuture = future;
        }

        public void completeOpenCloseFuture()
        {
            if (openClosefuture != null)
            {
                openClosefuture.complete(null);
                openClosefuture = null;
            }
        }

        public ControlMessageType getLastRequestMessageType()
        {
            return lastRequestMessageType;
        }
    }

}
