package io.zeebe.client.impl;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.cmd.BrokerRequestException;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.impl.cmd.AbstractCmdImpl;
import io.zeebe.client.impl.cmd.ClientResponseHandler;
import io.zeebe.client.impl.cmd.ReceiverAwareResponseResult;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RequestResponseController;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.WaitState;

public class ClientCommandController<R> implements BufferReader
{

    public static final int DEFAULT_RETRIES = 5;

    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_FAILED = 1;
    protected static final int TRANSITION_REFRESH_TOPOLOGY = 2;

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ErrorResponseDecoder errorResponseDecoder = new ErrorResponseDecoder();

    protected final StateMachine<Context<R>> stateMachine;
    protected final DetermineRemoteState determineRemoteState = new DetermineRemoteState();
    protected final RefreshTopologyState refreshTopologyState = new RefreshTopologyState();
    protected final AwaitTopologyRefreshState awaitTopologyRefreshState = new AwaitTopologyRefreshState();
    protected final ExecuteRequestState executeRequestState = new ExecuteRequestState();
    protected final HandleResponseState handleResponseState = new HandleResponseState();
    protected final FinishedState finishedState = new FinishedState();
    protected final FailedState failedState = new FailedState();
    protected final ClosedState closedState = new ClosedState();

    protected final ClientTopologyManager topologyManager;
    protected final AbstractCmdImpl<R> command;
    protected final CompletableFuture<R> future;

    protected final RequestResponseController requestController;

    public ClientCommandController(final ClientTransport transport, final ClientTopologyManager topologyManager, final AbstractCmdImpl<R> command, final CompletableFuture<R> future)
    {
        this.topologyManager = topologyManager;
        this.command = command;
        this.future = future;

        this.requestController = new RequestResponseController(transport);

        stateMachine = StateMachine.<Context<R>>builder(Context::new)
            .initialState(determineRemoteState)
            .from(determineRemoteState).take(TRANSITION_DEFAULT).to(executeRequestState)
            .from(determineRemoteState).take(TRANSITION_REFRESH_TOPOLOGY).to(refreshTopologyState)
            .from(determineRemoteState).take(TRANSITION_FAILED).to(failedState)
            .from(refreshTopologyState).take(TRANSITION_DEFAULT).to(awaitTopologyRefreshState)
            .from(awaitTopologyRefreshState).take(TRANSITION_DEFAULT).to(determineRemoteState)
            .from(awaitTopologyRefreshState).take(TRANSITION_FAILED).to(determineRemoteState)
            .from(executeRequestState).take(TRANSITION_DEFAULT).to(handleResponseState)
            .from(executeRequestState).take(TRANSITION_REFRESH_TOPOLOGY).to(refreshTopologyState)
            .from(executeRequestState).take(TRANSITION_FAILED).to(failedState)
            .from(handleResponseState).take(TRANSITION_DEFAULT).to(finishedState)
            .from(handleResponseState).take(TRANSITION_FAILED).to(failedState)
            .from(handleResponseState).take(TRANSITION_REFRESH_TOPOLOGY).to(refreshTopologyState)
            .from(finishedState).take(TRANSITION_DEFAULT).to(closedState)
            .from(failedState).take(TRANSITION_DEFAULT).to(closedState)
            .build();
    }

    public int doWork()
    {
        int workCount = stateMachine.doWork();
        workCount += requestController.doWork();
        return workCount;
    }

    public boolean isClosed()
    {
        return stateMachine.getCurrentState() == closedState;
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length)
    {
        messageHeaderDecoder.wrap(buffer, 0);

        final int schemaId = messageHeaderDecoder.schemaId();
        final int templateId = messageHeaderDecoder.templateId();
        final int blockLength = messageHeaderDecoder.blockLength();
        final int version = messageHeaderDecoder.version();

        final int responseMessageOffset = messageHeaderDecoder.encodedLength();

        final Context<R> context = stateMachine.getContext();

        final ClientResponseHandler<R> responseHandler = command.getResponseHandler();

        if (schemaId == responseHandler.getResponseSchemaId() && templateId == responseHandler.getResponseTemplateId())
        {
            final R responseObject = responseHandler.readResponse(buffer, responseMessageOffset, blockLength, version);

            // expose request channel if need to keep a reference of it (e.g. subscriptions)
            if (responseObject instanceof ReceiverAwareResponseResult)
            {
                final RemoteAddress receiver = requestController.getReceiverRemote();
                ((ReceiverAwareResponseResult) responseObject).setReceiver(receiver);
            }

            context.responseObject = responseObject;
        }
        else
        {
            errorResponseDecoder.wrap(buffer, responseMessageOffset, blockLength, version);

            final int errorDataLength = errorResponseDecoder.errorDataLength();
            context.errorBuffer = BufferUtil.wrapArray(new byte[errorDataLength]);
            context.errorCode = errorResponseDecoder.errorCode();

            errorResponseDecoder.getErrorData(context.errorBuffer, 0, errorDataLength);
        }

    }

    private class DetermineRemoteState implements State<Context<R>>
    {
        @Override
        public int doWork(final Context<R> context) throws Exception
        {
            ++context.attempts;

            final Topic topic = command.getTopic();

            if (context.attempts > DEFAULT_RETRIES)
            {
                if (topic != null)
                {
                    context.exception = new RuntimeException("Cannot execute command. No broker for topic with name '" + topic.getTopicName() + "' and partition id '" + topic.getPartitionId() + "' found.", context.exception);
                }
                else
                {
                    context.exception = new RuntimeException("Cannot execute command. No broker found.", context.exception);
                }
                context.take(TRANSITION_FAILED);
                return 0;
            }

            final SocketAddress address = topologyManager.getLeaderForTopic(topic);

            if (address != null)
            {
                requestController.open(address, command.getRequestWriter(), ClientCommandController.this);
                context.take(TRANSITION_DEFAULT);
            }
            else
            {
                context.take(TRANSITION_REFRESH_TOPOLOGY);
            }

            return 1;
        }

    }
    private class RefreshTopologyState implements State<Context<R>>
    {

        @Override
        public int doWork(final Context<R> context) throws Exception
        {
            context.topologyRefreshFuture = topologyManager.refreshNow();
            context.take(TRANSITION_DEFAULT);
            return 1;
        }

    }
    private class AwaitTopologyRefreshState implements State<Context<R>>
    {
        @Override
        public int doWork(final Context<R> context) throws Exception
        {
            int workCount = 0;

            final CompletableFuture<Void> topologyRefreshFuture = context.topologyRefreshFuture;

            if (topologyRefreshFuture.isDone())
            {
                try
                {
                    topologyRefreshFuture.get();
                    context.take(TRANSITION_DEFAULT);
                }
                catch (final Exception e)
                {
                    context.exception = e;
                    context.take(TRANSITION_FAILED);
                }


                workCount += 1;
            }

            return workCount;
        }

    }

    private class ExecuteRequestState implements State<Context<R>>
    {
        @Override
        public int doWork(final Context<R> context) throws Exception
        {
            if (requestController.isResponseAvailable())
            {
                requestController.close();
                context.take(TRANSITION_DEFAULT);

                return 1;
            }
            else if (requestController.isFailed())
            {
                final Exception failure = requestController.getFailure();
                context.exception = failure;

                requestController.close();

                if (failure instanceof ClientCommandRejectedException)
                {
                    context.take(TRANSITION_FAILED);
                }
                else
                {
                    context.take(TRANSITION_REFRESH_TOPOLOGY);
                }

                return 1;
            }
            else
            {
                // wait
                return 0;
            }
        }
    }

    private class HandleResponseState implements State<Context<R>>
    {

        @Override
        public int doWork(final Context<R> context) throws Exception
        {
            final ErrorCode errorCode = context.errorCode;

            if (errorCode == ErrorCode.NULL_VAL)
            {
                // request was successful
                context.take(TRANSITION_DEFAULT);
            }
            else if (errorCode == ErrorCode.TOPIC_NOT_FOUND)
            {
                // reset error context
                context.errorCode = ErrorCode.NULL_VAL;
                context.errorBuffer = null;

                // topic not found -> refresh topology -> retry request
                context.take(TRANSITION_REFRESH_TOPOLOGY);
            }
            else
            {
                context.take(TRANSITION_FAILED);
            }

            return 1;
        }

    }

    private class FinishedState implements State<Context<R>>
    {

        @Override
        public int doWork(final Context<R> context) throws Exception
        {
            future.complete(context.responseObject);
            context.take(TRANSITION_DEFAULT);
            return 1;
        }

    }

    private class FailedState implements State<Context<R>>
    {

        @Override
        public int doWork(final Context<R> context) throws Exception
        {
            final ErrorCode errorCode = context.errorCode;
            Throwable exception = context.exception;

            if (errorCode != ErrorCode.NULL_VAL)
            {
                try
                {
                    final String errorMessage = BufferUtil.bufferAsString(context.errorBuffer);
                    exception = new BrokerRequestException(context.errorCode, errorMessage);
                }
                catch (final Exception e)
                {
                    exception = new BrokerRequestException(errorCode, "Unable to parse error message from response: " + e.getMessage());
                }
            }
            else if (exception == null)
            {
                exception = new RuntimeException("Unknown error during request execution");
            }

            future.completeExceptionally(exception);

            context.take(TRANSITION_DEFAULT);

            return 1;
        }

    }

    private class ClosedState implements WaitState<Context<R>>
    {

        @Override
        public void work(final Context<R> context) throws Exception
        {
            context.reset();
        }
    }

    static class Context<R> extends SimpleStateMachineContext
    {
        CompletableFuture<Void> topologyRefreshFuture;

        int attempts;
        R responseObject;
        ErrorCode errorCode = ErrorCode.NULL_VAL;
        MutableDirectBuffer errorBuffer;
        Throwable exception;

        Context(final StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        @Override
        public void reset()
        {
            topologyRefreshFuture = null;
            attempts = 0;
            responseObject = null;
            errorCode = ErrorCode.NULL_VAL;
            errorBuffer = null;
            exception = null;
        }

    }

}
