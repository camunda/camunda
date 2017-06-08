package org.camunda.tngp.transport;

import static org.camunda.tngp.util.EnsureUtil.*;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.transport.impl.ChannelImpl;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.PooledFuture;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.WaitState;


public class AsyncRequestController
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_FAILED = 1;

    protected final ChannelManager channelManager;
    protected final TransportConnectionPool connectionPool;
    protected final StateMachine<Context> stateMachine;

    protected final RequestChannelState requestChannelState = new RequestChannelState();
    protected final AwaitChannelState awaitChannelState = new AwaitChannelState();
    protected final OpenConnectionState openConnectionState = new OpenConnectionState();
    protected final OpenRequestState openRequestState = new OpenRequestState();
    protected final SendRequestState sendRequestState = new SendRequestState();
    protected final AwaitResponseState awaitResponseState = new AwaitResponseState();
    protected final HandleResponseState handleResponseState = new HandleResponseState();
    protected final ClosedState closedState = new ClosedState();
    protected final ClosedState failedState = new ClosedState();

    public AsyncRequestController(final ChannelManager channelManager, final TransportConnectionPool connectionPool)
    {
        this.channelManager = channelManager;
        this.connectionPool = connectionPool;

        this.stateMachine =
            StateMachine.builder(Context::new)
                .initialState(requestChannelState)
                .from(requestChannelState).take(TRANSITION_DEFAULT).to(awaitChannelState)
                .from(awaitChannelState).take(TRANSITION_DEFAULT).to(openConnectionState)
                .from(awaitChannelState).take(TRANSITION_FAILED).to(failedState)
                .from(openConnectionState).take(TRANSITION_DEFAULT).to(openRequestState)
                .from(openRequestState).take(TRANSITION_DEFAULT).to(sendRequestState)
                .from(sendRequestState).take(TRANSITION_DEFAULT).to(awaitResponseState)
                .from(sendRequestState).take(TRANSITION_FAILED).to(failedState)
                .from(awaitResponseState).take(TRANSITION_DEFAULT).to(handleResponseState)
                .from(awaitResponseState).take(TRANSITION_FAILED).to(failedState)
                .from(handleResponseState).take(TRANSITION_DEFAULT).to(closedState)
                .from(handleResponseState).take(TRANSITION_FAILED).to(failedState)
                .build();
    }

    public AsyncRequestController configure(final SocketAddress socketAddress, final BufferWriter bufferWriter, final BufferReader bufferReader, final CompletableFuture<Integer> resultFuture)
    {
        reset();

        ensureNotNull("socketAddress", socketAddress);
        ensureNotNull("bufferWriter", bufferWriter);
        ensureNotNull("bufferReader", bufferReader);
        ensureNotNull("resultFuture", resultFuture);

        final Context context = stateMachine.getContext();
        context.socketAddress = socketAddress;
        context.bufferWriter = bufferWriter;
        context.bufferReader = bufferReader;
        context.resultFuture = resultFuture;

        return this;
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    /**
     *
     * Allows to access the channel used by the controller. E.g. to keep an reference
     * on it for subscriptions.
     *
     * @return the channel used by the request controller
     */
    public Channel borrowChannel()
    {
        final Channel channel = stateMachine.getContext().channel;

        if (channel != null && channel instanceof ChannelImpl)
        {
            ((ChannelImpl) channel).countUsageBegin();
        }

        return channel;
    }

    public void reset()
    {
        stateMachine.reset();
    }

    private class RequestChannelState implements State<Context>
    {
        @Override
        public int doWork(final Context context) throws Exception
        {
            final PooledFuture<Channel> channelFuture = channelManager.requestChannelAsync(context.socketAddress);
            if (channelFuture != null)
            {
                context.channelFuture = channelFuture;
                context.take(TRANSITION_DEFAULT);
            }

            return 1;
        }

    }

    private class AwaitChannelState implements WaitState<Context>
    {
        @Override
        public void work(final Context context) throws Exception
        {
            final PooledFuture<Channel> channelFuture = context.channelFuture;

            if (channelFuture.isFailed())
            {
                context.exception = new RuntimeException("Unable to connect to target");
                context.take(TRANSITION_FAILED);
            }
            else
            {
                final Channel channel = channelFuture.poll();

                if (channel != null)
                {
                    context.channel = channel;
                    channelFuture.release();
                    context.take(TRANSITION_DEFAULT);
                }
            }
        }


    }

    private class OpenConnectionState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final TransportConnection connection = connectionPool.openConnection();

            if (connection != null)
            {
                context.connection = connection;
                context.take(TRANSITION_DEFAULT);
            }

            return 1;
        }

    }

    private class OpenRequestState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final PooledTransportRequest request = context.connection.openRequest(context.channel.getStreamId(), context.bufferWriter.getLength());
            if (request != null)
            {
                context.request = request;
                context.take(TRANSITION_DEFAULT);
            }

            return 1;
        }


    }

    private class SendRequestState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final PooledTransportRequest request = context.request;
            final MutableDirectBuffer buffer = request.getClaimedRequestBuffer();
            final int offset = request.getClaimedOffset();

            try
            {
                context.bufferWriter.write(buffer, offset);
                request.commit();
                context.take(TRANSITION_DEFAULT);
            }
            catch (final Exception e)
            {
                request.abort();
                context.request = null;
                context.exception = e;
                context.take(TRANSITION_FAILED);
            }


            return 1;
        }

    }

    private class AwaitResponseState implements WaitState<Context>
    {

        @Override
        public void work(final Context context) throws Exception
        {

            try
            {
                if (context.request.pollResponse())
                {
                    context.take(TRANSITION_DEFAULT);
                }
            }
            catch (final Exception e)
            {
                context.exception = e;
                context.take(TRANSITION_FAILED);
            }
        }

    }

    private class HandleResponseState implements State<Context>
    {
        @Override
        public int doWork(final Context context) throws Exception
        {
            final PooledTransportRequest request = context.request;

            final DirectBuffer buffer = request.getResponseBuffer();
            final int length = request.getResponseLength();

            try
            {
                context.bufferReader.wrap(buffer, 0, length);
                context.take(TRANSITION_DEFAULT);
            }
            catch (final Exception e)
            {
                context.exception = e;
                context.take(TRANSITION_FAILED);
            }


            return 1;
        }

    }

    private class ClosedState implements WaitState<Context>
    {

        @Override
        public void work(final Context context) throws Exception
        {
            final CompletableFuture<Integer> resultFuture = context.resultFuture;
            final Exception exception = context.exception;

            if (resultFuture != null)
            {
                if (exception != null)
                {
                    resultFuture.completeExceptionally(exception);
                }
                else
                {
                    resultFuture.complete(context.request.getChannelId());
                }
            }

            context.reset();
        }

    }


    class Context extends SimpleStateMachineContext
    {
        public SocketAddress socketAddress;
        public BufferWriter bufferWriter;
        public BufferReader bufferReader;
        public CompletableFuture<Integer> resultFuture;

        public PooledFuture<Channel> channelFuture;
        public Channel channel;
        public TransportConnection connection;
        public PooledTransportRequest request;
        public Exception exception;

        Context(final StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        public void complete()
        {
        }

        @Override
        public void reset()
        {
            socketAddress = null;
            bufferWriter = null;
            bufferReader = null;

            if (resultFuture != null && !resultFuture.isDone())
            {
                resultFuture.cancel(true);
            }
            resultFuture = null;

            if (request != null)
            {
                request.close();
                request = null;
            }

            if (connection != null)
            {
                connection.close();
                connection = null;
            }

            if (channel != null)
            {
                channelManager.returnChannel(channel);
                channel = null;
            }

            channelFuture = null;
            exception = null;
        }

    }

}
