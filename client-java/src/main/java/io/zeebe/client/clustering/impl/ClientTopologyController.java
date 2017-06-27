package io.zeebe.client.clustering.impl;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RequestResponseController;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.WaitState;


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

    protected final RequestResponseController requestController;

    public ClientTopologyController(final ClientTransport clientTransport)
    {
        requestController = new RequestResponseController(clientTransport);

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
        int workCount = stateMachine.doWork();
        return workCount += requestController.doWork();
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
            requestController.open(context.socketAddress, context.bufferWriter, context.bufferReader);

            context.take(TRANSITION_DEFAULT);

            return 1;
        }

    }

    private class AwaitTopologyState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            if (requestController.isResponseAvailable())
            {
                context.resultFuture.complete(null);
                context.take(TRANSITION_DEFAULT);

                return 1;
            }
            else if (requestController.isFailed())
            {
                context.resultFuture.completeExceptionally(requestController.getFailure());
                context.take(TRANSITION_FAILED);

                return 1;
            }
            else
            {
                return 0;
            }
        }
    }

    private class ClosedState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            requestController.close();
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

            nextRefresh = Instant.now().plus(REFRESH_INTERVAL);
        }

    }

}
