package org.camunda.tngp.broker.clustering.util;

import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.channel.EndpointChannel;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.requestresponse.client.TransportRequest;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.StateMachineCommand;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class RequestResponseController
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_FAILED = 2;
    private static final int TRANSITION_CLOSE = 3;

    private static final StateMachineCommand<RequestResponseContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final WaitState<RequestResponseContext> closedState = (c) ->
    {
    };
    private final WaitState<RequestResponseContext> responseAvailableState = (c) ->
    {
    };
    private final WaitState<RequestResponseContext> failedState = (c) ->
    {
    };

    private final ClosingState closingState = new ClosingState();
    private final OpenConnectionState openConnectionState = new OpenConnectionState();
    private final OpenChannelState openChannelState = new OpenChannelState();
    private final OpenRequestState openRequestState = new OpenRequestState();
    private final SendRequestState sendRequestState = new SendRequestState();
    private final PollResponseState pollResponseState = new PollResponseState();

    private RequestResponseContext requestResponseContext;
    private final StateMachineAgent<RequestResponseContext> requestStateMachine;

    public RequestResponseController(
            final ClientChannelManager clientChannelManager,
            final TransportConnectionPool connections)
    {
        this(clientChannelManager, connections, -1);
    }

    public RequestResponseController(
            final ClientChannelManager clientChannelManager,
            final TransportConnectionPool connections,
            final int timeout)
    {
        this.requestStateMachine = new StateMachineAgent<>(
                StateMachine.<RequestResponseContext> builder(s ->
                {
                    requestResponseContext = new RequestResponseContext(s, timeout, clientChannelManager, connections);
                    return requestResponseContext;
                })
                    .initialState(closedState)

                    .from(closedState).take(TRANSITION_OPEN).to(openConnectionState)
                    .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                    .from(openConnectionState).take(TRANSITION_DEFAULT).to(openChannelState)
                    .from(openConnectionState).take(TRANSITION_CLOSE).to(closingState)

                    .from(openChannelState).take(TRANSITION_DEFAULT).to(openRequestState)
                    .from(openChannelState).take(TRANSITION_FAILED).to(failedState)
                    .from(openChannelState).take(TRANSITION_CLOSE).to(closingState)

                    .from(openRequestState).take(TRANSITION_DEFAULT).to(sendRequestState)
                    .from(openRequestState).take(TRANSITION_FAILED).to(failedState)
                    .from(openRequestState).take(TRANSITION_CLOSE).to(closingState)

                    .from(sendRequestState).take(TRANSITION_DEFAULT).to(pollResponseState)
                    .from(sendRequestState).take(TRANSITION_FAILED).to(failedState)
                    .from(sendRequestState).take(TRANSITION_CLOSE).to(closingState)

                    .from(pollResponseState).take(TRANSITION_DEFAULT).to(responseAvailableState)
                    .from(pollResponseState).take(TRANSITION_FAILED).to(failedState)
                    .from(pollResponseState).take(TRANSITION_CLOSE).to(closingState)

                    .from(responseAvailableState).take(TRANSITION_CLOSE).to(closingState)
                    .from(failedState).take(TRANSITION_CLOSE).to(closingState)

                    .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                    .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                    .build());
    }

    public void open(final Endpoint receiver, final BufferWriter requestWriter)
    {
        if (isClosed())
        {
            requestResponseContext.receiver.wrap(receiver);
            requestResponseContext.requestWriter = requestWriter;
            requestResponseContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        requestResponseContext.reset();

        requestStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return requestStateMachine.doWork();
    }

    public boolean isFailed()
    {
        return requestStateMachine.getCurrentState() == failedState;
    }

    public boolean isResponseAvailable()
    {
        return requestStateMachine.getCurrentState() == responseAvailableState;
    }

    public boolean isClosed()
    {
        return requestStateMachine.getCurrentState() == closedState;
    }

    public DirectBuffer getResponseBuffer()
    {
        return requestResponseContext.getResponseBuffer();
    }

    public int getResponseLength()
    {
        return requestResponseContext.getResponseLength();
    }

    class RequestResponseContext extends SimpleStateMachineContext
    {
        TransportConnection connection;
        EndpointChannel endpointChannel;
        ClientChannel channel;
        TransportRequest request;

        final Endpoint receiver;

        final int timeout;
        final ClientChannelManager clientChannelManager;
        final TransportConnectionPool connections;

        BufferWriter requestWriter;

        RequestResponseContext(StateMachine<?> stateMachine, final int timeout, final ClientChannelManager clientChannelManager, final TransportConnectionPool connections)
        {
            super(stateMachine);
            this.receiver = new Endpoint();
            this.timeout = timeout;
            this.clientChannelManager = clientChannelManager;
            this.connections = connections;
        }

        public void reset()
        {
            receiver.reset();
            requestWriter = null;
        }

        public DirectBuffer getResponseBuffer()
        {
            if (request != null)
            {
                return request.getResponseBuffer();
            }
            return null;
        }

        public int getResponseLength()
        {
            if (request != null)
            {
                return request.getResponseLength();
            }
            return 0;
        }
    }

    class OpenConnectionState implements State<RequestResponseContext>
    {
        @Override
        public int doWork(RequestResponseContext context) throws Exception
        {
            final TransportConnectionPool connections = context.connections;
            int workcount = 0;

            final TransportConnection connection = connections.openConnection();

            if (connection != null)
            {
                workcount += 1;

                context.connection = connection;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    class OpenChannelState implements State<RequestResponseContext>
    {
        @Override
        public int doWork(RequestResponseContext context) throws Exception
        {
            EndpointChannel endpointChannel = context.endpointChannel;
            final ClientChannelManager clientChannelManager = context.clientChannelManager;
            final Endpoint receiver = context.receiver;

            int workcount = 0;

            if (endpointChannel == null || endpointChannel.isClosed())
            {
                workcount += 1;
                endpointChannel = clientChannelManager.claim(receiver);
            }

            context.endpointChannel = endpointChannel;
            final ClientChannel channel = endpointChannel.getClientChannel();

            if (channel != null)
            {
                workcount += 1;
                context.channel = channel;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }

        @Override
        public void onFailure(RequestResponseContext context, Exception e)
        {
            e.printStackTrace();
            context.take(TRANSITION_FAILED);
        }
    }

    class OpenRequestState implements State<RequestResponseContext>
    {

        @Override
        public int doWork(RequestResponseContext context) throws Exception
        {
            int workcount = 0;

            final TransportConnection connection = context.connection;
            final ClientChannel channel = context.channel;
            final BufferWriter requestWriter = context.requestWriter;

            final int channelId = channel.getId();

            final int length = requestWriter.getLength();

            final long timeout = TimeUnit.SECONDS.toMillis(context.timeout);
            final PooledTransportRequest request = connection.openRequest(channelId, length, timeout);

            if (request != null)
            {
                workcount += 1;
                context.request = request;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }

        @Override
        public void onFailure(RequestResponseContext context, Exception e)
        {
            e.printStackTrace();
            context.take(TRANSITION_FAILED);
        }
    }

    class SendRequestState implements TransitionState<RequestResponseContext>
    {
        @Override
        public void work(RequestResponseContext context) throws Exception
        {
            final TransportRequest request = context.request;
            final BufferWriter requestWriter = context.requestWriter;

            final int claimedOffset = request.getClaimedOffset();
            final MutableDirectBuffer claimedRequestBuffer = request.getClaimedRequestBuffer();

            requestWriter.write(claimedRequestBuffer, claimedOffset);
            request.commit();

            context.take(TRANSITION_DEFAULT);
        }

        @Override
        public void onFailure(RequestResponseContext context, Exception e)
        {
            e.printStackTrace();
            context.take(TRANSITION_FAILED);
        }
    }

    class PollResponseState implements State<RequestResponseContext>
    {
        @Override
        public int doWork(RequestResponseContext context) throws Exception
        {
            int workcount = 0;
            final TransportRequest request = context.request;

            if (request.pollResponse())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }

        @Override
        public void onFailure(RequestResponseContext context, Exception e)
        {
            e.printStackTrace();
            context.take(TRANSITION_FAILED);
        }
    }

    class ClosingState implements TransitionState<RequestResponseContext>
    {
        @Override
        public void work(RequestResponseContext context) throws Exception
        {
            final TransportRequest request = context.request;
            if (request != null)
            {
                request.close();
            }

            final TransportConnection connection = context.connection;
            if (connection != null)
            {
                connection.close();
            }

            final EndpointChannel endpointChannel = context.endpointChannel;
            context.clientChannelManager.reclaim(endpointChannel);

            context.connection = null;
            context.endpointChannel = null;
            context.channel = null;
            context.request = null;

            context.take(TRANSITION_DEFAULT);
        }
    }

}
