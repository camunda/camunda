package org.camunda.tngp.broker.clustering.util;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.ClientChannelPool;
import org.camunda.tngp.transport.PooledFuture;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.StateMachineCommand;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class SingleMessageController
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_FAILED = 2;
    private static final int TRANSITION_CLOSE = 3;

    private static final StateMachineCommand<SingleMessageContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final WaitState<SingleMessageContext> closedState = (c) ->
    {
    };
    private final WaitState<SingleMessageContext> sentState = (c) ->
    {
    };
    private final WaitState<SingleMessageContext> failedState = (c) ->
    {
    };

    private final OpenChannelState openChannelState = new OpenChannelState();
    private final SendMessageState sendMessageState = new SendMessageState();
    private final CloseChannelState closeChannelState = new CloseChannelState();

    private SingleMessageContext singleMessageContext;
    private final StateMachineAgent<SingleMessageContext> singleMessageStateMachine;

    public SingleMessageController(final ClientChannelPool clientChannelManager, final Dispatcher sendBuffer)
    {
        this.singleMessageStateMachine = new StateMachineAgent<>(
                StateMachine.<SingleMessageContext> builder(s ->
                {
                    singleMessageContext = new SingleMessageContext(s, clientChannelManager, sendBuffer);
                    return singleMessageContext;
                })
                    .initialState(closedState)

                    .from(closedState).take(TRANSITION_OPEN).to(openChannelState)
                    .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                    .from(openChannelState).take(TRANSITION_DEFAULT).to(sendMessageState)
                    .from(openChannelState).take(TRANSITION_FAILED).to(failedState)
                    .from(openChannelState).take(TRANSITION_CLOSE).to(closeChannelState)

                    .from(sendMessageState).take(TRANSITION_DEFAULT).to(sentState)
                    .from(sendMessageState).take(TRANSITION_FAILED).to(failedState)
                    .from(sendMessageState).take(TRANSITION_CLOSE).to(closeChannelState)

                    .from(sentState).take(TRANSITION_CLOSE).to(closeChannelState)
                    .from(failedState).take(TRANSITION_CLOSE).to(closeChannelState)

                    .from(closeChannelState).take(TRANSITION_DEFAULT).to(closedState)
                    .from(closeChannelState).take(TRANSITION_CLOSE).to(closeChannelState)

                    .build());
    }

    public void open(final SocketAddress receiver, final BufferWriter requestWriter)
    {
        if (isClosed())
        {
            singleMessageContext.receiver.wrap(receiver);
            singleMessageContext.requestWriter = requestWriter;
            singleMessageContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        singleMessageStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return singleMessageStateMachine.doWork();
    }

    public boolean isClosed()
    {
        return singleMessageStateMachine.getCurrentState() == closedState;
    }

    public boolean isFailed()
    {
        return singleMessageStateMachine.getCurrentState() == failedState;
    }

    public boolean isSent()
    {
        return singleMessageStateMachine.getCurrentState() == sentState;
    }

    static class SingleMessageContext extends SimpleStateMachineContext
    {
        final ClientChannelPool clientChannelManager;
        final SocketAddress receiver;
        final MessageWriter messageWriter;

        PooledFuture<ClientChannel> channelFuture;
        ClientChannel channel;
        BufferWriter requestWriter;

        SingleMessageContext(final StateMachine<?> stateMachine, final ClientChannelPool clientChannelManager, final Dispatcher sendBuffer)
        {
            super(stateMachine);
            this.clientChannelManager = clientChannelManager;
            this.receiver = new SocketAddress();
            this.messageWriter = new MessageWriter(sendBuffer);
        }
    }

    static class OpenChannelState implements State<SingleMessageContext>
    {
        @Override
        public int doWork(SingleMessageContext context) throws Exception
        {

            int workcount = 0;

            if (context.channelFuture == null)
            {
                final ClientChannelPool clientChannelManager = context.clientChannelManager;
                final SocketAddress receiver = context.receiver;

                workcount += 1;
                context.channelFuture = clientChannelManager.requestChannelAsync(receiver);
            }

            if (context.channelFuture != null)
            {
                if (!context.channelFuture.isFailed())
                {
                    final ClientChannel clientChannel = context.channelFuture.poll();
                    if (clientChannel != null)
                    {
                        context.channel = clientChannel;
                        context.channelFuture.release();
                        context.channelFuture = null;
                        context.take(TRANSITION_DEFAULT);
                    }
                }
                else
                {
                    context.channelFuture.release();
                    context.channelFuture = null;
                    context.take(TRANSITION_FAILED);
                }
            }

            return workcount;
        }

        @Override
        public void onFailure(SingleMessageContext context, Exception e)
        {
            e.printStackTrace();
            context.take(TRANSITION_FAILED);
        }
    }

    static class SendMessageState implements State<SingleMessageContext>
    {
        @Override
        public int doWork(SingleMessageContext context) throws Exception
        {
            final MessageWriter messageWriter = context.messageWriter;
            final BufferWriter requestWriter = context.requestWriter;
            final ClientChannel channel = context.channel;

            int workcount = 0;

            final boolean isSent = messageWriter.protocol(Protocols.FULL_DUPLEX_SINGLE_MESSAGE)
                .channelId(channel.getId())
                .message(requestWriter)
                .tryWriteMessage();

            if (isSent)
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            // TODO: timeout

            return workcount;

        }

        @Override
        public void onFailure(SingleMessageContext context, Exception e)
        {
            e.printStackTrace();
            context.take(TRANSITION_FAILED);
        }
    }

    static class CloseChannelState implements TransitionState<SingleMessageContext>
    {
        @Override
        public void work(SingleMessageContext context) throws Exception
        {
            final ClientChannelPool clientChannelManager = context.clientChannelManager;
            final ClientChannel endpointChannel = context.channel;
            clientChannelManager.returnChannel(endpointChannel);

            final PooledFuture<ClientChannel> channelFuture = context.channelFuture;
            if (channelFuture != null)
            {
                channelFuture.release();
            }

            context.channelFuture = null;
            context.channel = null;
            context.requestWriter = null;
            context.receiver.reset();

            context.take(TRANSITION_DEFAULT);
        }
    }
}
