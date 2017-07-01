package io.zeebe.broker.clustering.util;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.Channel;
import io.zeebe.transport.ChannelManager;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.util.PooledFuture;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

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
    private final ClosingChannelState closingChannelState = new ClosingChannelState();
    private final ClosingState closingState = new ClosingState();

    private SingleMessageContext singleMessageContext;
    private final StateMachineAgent<SingleMessageContext> singleMessageStateMachine;

    public SingleMessageController(final ChannelManager clientChannelManager, final Dispatcher sendBuffer)
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
                    .from(openChannelState).take(TRANSITION_CLOSE).to(closingChannelState)

                    .from(sendMessageState).take(TRANSITION_DEFAULT).to(sentState)
                    .from(sendMessageState).take(TRANSITION_FAILED).to(failedState)
                    .from(sendMessageState).take(TRANSITION_CLOSE).to(closingChannelState)

                    .from(sentState).take(TRANSITION_CLOSE).to(closingChannelState)
                    .from(failedState).take(TRANSITION_CLOSE).to(closingChannelState)

                    .from(closingChannelState).take(TRANSITION_DEFAULT).to(closingState)
                    .from(closingChannelState).take(TRANSITION_CLOSE).to(closingChannelState)

                    .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                    .from(closingState).take(TRANSITION_CLOSE).to(closingState)

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
        final ChannelManager clientChannelManager;
        final SocketAddress receiver;
        final MessageWriter messageWriter;

        PooledFuture<Channel> channelFuture;
        Channel channel;
        BufferWriter requestWriter;

        SingleMessageContext(final StateMachine<?> stateMachine, final ChannelManager clientChannelManager, final Dispatcher sendBuffer)
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
                final ChannelManager clientChannelManager = context.clientChannelManager;
                final SocketAddress receiver = context.receiver;

                workcount += 1;
                context.channelFuture = clientChannelManager.requestChannelAsync(receiver);
            }

            if (context.channelFuture != null)
            {
                if (!context.channelFuture.isFailed())
                {
                    final Channel clientChannel = context.channelFuture.poll();
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
            final Channel channel = context.channel;

            int workcount = 0;

            final boolean isSent = messageWriter.protocol(Protocols.FULL_DUPLEX_SINGLE_MESSAGE)
                .channelId(channel.getStreamId())
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

    static class ClosingChannelState implements State<SingleMessageContext>
    {
        @Override
        public int doWork(SingleMessageContext context) throws Exception
        {
            int workcount = 0;

            final PooledFuture<Channel> channelFuture = context.channelFuture;
            if (channelFuture != null)
            {
                if (channelFuture.isFailed() || channelFuture.poll() != null)
                {
                    channelFuture.release();
                    context.channelFuture = null;

                    workcount += 1;
                    context.take(TRANSITION_DEFAULT);
                }
            }
            else
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class ClosingState implements TransitionState<SingleMessageContext>
    {
        @Override
        public void work(SingleMessageContext context) throws Exception
        {
            final ChannelManager clientChannelManager = context.clientChannelManager;
            final Channel endpointChannel = context.channel;
            clientChannelManager.returnChannel(endpointChannel);

            context.channel = null;
            context.requestWriter = null;
            context.receiver.reset();

            context.take(TRANSITION_DEFAULT);
        }
    }
}
