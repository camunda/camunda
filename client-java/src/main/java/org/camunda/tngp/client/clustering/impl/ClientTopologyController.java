package org.camunda.tngp.client.clustering.impl;

import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.camunda.tngp.transport.AsyncRequestController;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.WaitState;


public class ClientTopologyController
{
    public static final Duration REFRESH_INTERVAL = Duration.ofSeconds(10);

    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_FAILED = 1;

    protected final StateMachine<Context> stateMachine;
    protected final RequestTopologyState requestTopologyState = new RequestTopologyState();
    protected final AwaitTopologyState awaitTopologyState = new AwaitTopologyState();
    protected final ClosedState closedState = new ClosedState();
    protected final IdleState idleState = new IdleState();

    protected final AsyncRequestController requestController;

    public ClientTopologyController(final ChannelManager channelManager, final TransportConnectionPool connectionPool)
    {
        requestController = new AsyncRequestController(channelManager, connectionPool);

        stateMachine = StateMachine.builder(Context::new)
            .initialState(requestTopologyState)
            .from(requestTopologyState).take(TRANSITION_DEFAULT).to(awaitTopologyState)
            .from(awaitTopologyState).take(TRANSITION_DEFAULT).to(closedState)
            .from(awaitTopologyState).take(TRANSITION_FAILED).to(closedState)
            .from(closedState).take(TRANSITION_DEFAULT).to(idleState)
            .from(idleState).take(TRANSITION_DEFAULT).to(requestTopologyState)
            .build();
    }

    public ClientTopologyController configure(final SocketAddress socketAddress, final BufferWriter bufferWriter, final BufferReader bufferReader, final CompletableFuture<Void> resultFuture)
    {
        stateMachine.reset();

        ensureNotNull("socketAddress", socketAddress);
        ensureNotNull("bufferWriter", bufferWriter);
        ensureNotNull("bufferReader", bufferReader);
        ensureNotNull("resultFuture", resultFuture);

        final Context context = stateMachine.getContext();
        context.resultFuture = resultFuture;
        context.socketAddress = socketAddress;
        context.bufferWriter = bufferWriter;
        context.bufferReader = bufferReader;

        return this;
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    public boolean isIdle()
    {
        return stateMachine.getCurrentState() == idleState;
    }

    private class RequestTopologyState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            context.requestFuture = new CompletableFuture<>();
            requestController.configure(context.socketAddress, context.bufferWriter, context.bufferReader, context.requestFuture);

            context.take(TRANSITION_DEFAULT);

            return 1;
        }

    }

    private class AwaitTopologyState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final CompletableFuture<Integer> requestFuture = context.requestFuture;

            if (requestFuture.isDone())
            {
                try
                {
                    requestFuture.get();
                    context.take(TRANSITION_DEFAULT);
                }
                catch (final ExecutionException e)
                {
                    context.exception = e.getCause();
                    context.take(TRANSITION_FAILED);
                }

                return 1;
            }
            else
            {
                return requestController.doWork();
            }
        }
    }

    private class ClosedState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final CompletableFuture<Void> resultFuture = context.resultFuture;

            if (resultFuture != null && !resultFuture.isDone())
            {
                final Throwable exception = context.exception;

                if (exception == null)
                {
                    resultFuture.complete(null);
                }
                else
                {
                    resultFuture.completeExceptionally(exception);
                }

            }

            context.take(TRANSITION_DEFAULT);

            context.reset();

            return 1;
        }

    }

    private class IdleState implements WaitState<Context>
    {

        @Override
        public void work(final Context context) throws Exception
        {
            if (Instant.now().isAfter(context.nextRefresh))
            {
                context.take(TRANSITION_DEFAULT);
            }
        }

    }


    static class Context extends SimpleStateMachineContext
    {
        // can be null if automatic refresh was trigger
        CompletableFuture<Void> resultFuture;

        // keep during reset to allow automatic refresh with last configuration
        SocketAddress socketAddress;
        BufferWriter bufferWriter;
        BufferReader bufferReader;

        CompletableFuture<Integer> requestFuture;
        Throwable exception;

        // set during reset of context, e.g. on configure(...) and CloseState
        Instant nextRefresh;

        Context(final StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        @Override
        public void reset()
        {
            if (resultFuture != null && !resultFuture.isDone())
            {
                resultFuture.cancel(true);
            }
            resultFuture = null;

            requestFuture = null;
            exception = null;

            nextRefresh = Instant.now().plus(REFRESH_INTERVAL);
        }

    }

}
