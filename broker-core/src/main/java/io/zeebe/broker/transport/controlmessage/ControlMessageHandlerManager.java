package io.zeebe.broker.transport.controlmessage;

import static io.zeebe.broker.services.DispatcherSubscriptionNames.TRANSPORT_CONTROL_MESSAGE_HANDLER_SUBSCRIPTION;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.protocol.clientapi.ControlMessageRequestDecoder;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;
import io.zeebe.util.time.ClockUtil;

public class ControlMessageHandlerManager implements Actor
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

    protected final ActorScheduler actorScheduler;
    protected final AtomicBoolean isRunning = new AtomicBoolean(false);
    protected ActorReference actorRef;

    protected final ControlMessageRequestHeaderDescriptor requestHeaderDescriptor = new ControlMessageRequestHeaderDescriptor();
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ControlMessageRequestDecoder requestDecoder = new ControlMessageRequestDecoder();

    protected final UnsafeBuffer requestBuffer = new UnsafeBuffer(new byte[1024 * 32]);

    protected final Dispatcher controlMessageDispatcher;
    protected Subscription subscription;

    protected final Int2ObjectHashMap<ControlMessageHandler> handlersByTypeId = new Int2ObjectHashMap<>();

    protected final ErrorResponseWriter errorResponseWriter;
    protected final BrokerEventMetadata eventMetada = new BrokerEventMetadata();
    protected final ServerResponse response = new ServerResponse();

    protected final long requestTimeoutInMillis;

    public ControlMessageHandlerManager(
            ServerOutput output,
            Dispatcher controlMessageDispatcher,
            long requestTimeoutInMillis,
            ActorScheduler actorScheduler,
            List<ControlMessageHandler> handlers)
    {
        this.actorScheduler = actorScheduler;
        this.controlMessageDispatcher = controlMessageDispatcher;
        this.requestTimeoutInMillis = requestTimeoutInMillis;
        this.errorResponseWriter = new ErrorResponseWriter(output);

        for (ControlMessageHandler handler : handlers)
        {
            final ControlMessageType messageType = handler.getMessageType();
            handlersByTypeId.put(messageType.value(), handler);
        }
    }

    @Override
    public String name()
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
                actorRef = actorScheduler.schedule(this);
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
            requestHeaderDescriptor.wrap(buffer, offset);

            eventMetada.reset();

            eventMetada
                .requestId(requestHeaderDescriptor.requestId())
                .requestStreamId(requestHeaderDescriptor.streamId());

            offset += ControlMessageRequestHeaderDescriptor.headerLength();

            messageHeaderDecoder.wrap(requestBuffer, 0);
            offset += messageHeaderDecoder.encodedLength();

            requestDecoder.wrap(buffer, offset, requestDecoder.sbeBlockLength(), requestDecoder.sbeSchemaVersion());

            final ControlMessageType messageType = requestDecoder.messageType();
            context.lastRequestMessageType(messageType);

            ensureBufferCapacity(requestDecoder.dataLength());
            requestDecoder.getData(requestBuffer, 0, requestDecoder.dataLength());

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
            byte[] raw = requestBuffer.byteArray();

            if (length <= raw.length)
            {
                Arrays.fill(raw, (byte) 0);
            }
            else
            {
                raw = new byte[length];
            }

            requestBuffer.wrap(raw, 0, length);
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
                final boolean success = errorResponseWriter
                    .errorCode(ErrorCode.REQUEST_TIMEOUT)
                    .errorMessage("Timeout while handle control message.")
                    .failedRequest(requestBuffer, 0, requestBuffer.capacity())
                    .tryWriteResponseOrLogFailure(eventMetada.getRequestStreamId(), eventMetada.getRequestId());
                // TODO: proper backpressure

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
            final boolean success = errorResponseWriter
                .errorCode(ErrorCode.MESSAGE_NOT_SUPPORTED)
                .errorMessage("Cannot handle control message with type '%s'.", context.getLastRequestMessageType().name())
                .failedRequest(requestBuffer, 0, requestBuffer.capacity())
                .tryWriteResponseOrLogFailure(eventMetada.getRequestStreamId(), eventMetada.getRequestId());
            // TODO: proper backpressure

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

                actorRef.close();
            }
        }
    }

    static class Context extends SimpleStateMachineContext
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
