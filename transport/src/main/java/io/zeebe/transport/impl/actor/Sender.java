package io.zeebe.transport.impl.actor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.zeebe.dispatcher.BlockPeek;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.Sender.SenderContext;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.state.ComposedState;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;

public class Sender implements Actor
{
    private final ActorContext actorContext;
    private final Int2ObjectHashMap<TransportChannel> channelMap = new Int2ObjectHashMap<>();
    private final Subscription senderSubscription;
    private final int maxPeekSize;
    private final boolean isClient;
    protected final FragmentHandler sendFailureHandler;

    private static final int DEFAULT = 0;
    private static final int DISCARD = 1;

    private final ProcessState processState = new ProcessState();
    private final DiscardState discardState = new DiscardState();

    private final StateMachine<SenderContext> stateMachine = StateMachine.<SenderContext>builder((s) -> new SenderContext(s))
        .initialState(processState)
        .from(processState).take(DISCARD).to(discardState)
        .from(processState).take(DEFAULT).to(processState)
        .from(discardState).take(DEFAULT).to(processState)
        .build();

    private StateMachineAgent<SenderContext> stateMachineAgent = new StateMachineAgent<>(stateMachine);

    public Sender(ActorContext actorContext, TransportContext context)
    {
        this.actorContext = actorContext;
        this.senderSubscription = context.getSenderSubscription();
        this.maxPeekSize = context.getMessageMaxLength() * 16;
        this.isClient = actorContext instanceof ClientActorContext;
        this.sendFailureHandler = context.getSendFailureHandler();

        actorContext.setSender(this);
    }

    @Override
    public int doWork() throws Exception
    {
        return stateMachineAgent.doWork();
    }

    class ProcessState extends ComposedState<SenderContext>
    {
        private final PollState pollState = new PollState();
        private final AwaitChannelState awaitChannelState = new AwaitChannelState();
        private final WriteState writeState = new WriteState();

        class PollState implements Step<SenderContext>
        {
            @Override
            public boolean doWork(SenderContext context)
            {
                context.reset();

                final int blockSize = senderSubscription.peekBlock(context.blockPeek, maxPeekSize, true);

                return blockSize > 0;
            }
        }

        class AwaitChannelState implements Step<SenderContext>
        {
            @Override
            public boolean doWork(SenderContext context)
            {
                final CompletableFuture<Void> channelFuture = context.channelFuture;
                final BlockPeek blockPeek = context.blockPeek;
                final TransportChannel ch = channelMap.get(blockPeek.getStreamId());

                if (ch != null)
                {
                    context.writeChannel = ch;
                    return true;
                }
                else
                {
                    if (isClient)
                    {
                        if (channelFuture == null)
                        {
                            context.channelFuture = ((ClientActorContext) actorContext).requestChannel(blockPeek.getStreamId());
                        }
                        else if (channelFuture.isCancelled() || channelFuture.isCompletedExceptionally())
                        {
                            context.take(DISCARD);
                        }
                    }
                    else
                    {
                        context.take(DISCARD);
                    }
                    return false;
                }
            }
        }

        class WriteState implements Step<SenderContext>
        {
            @Override
            public boolean doWork(SenderContext context)
            {
                final BlockPeek blockPeek = context.blockPeek;
                final TransportChannel writeChannel = context.writeChannel;

                final int bytesWritten = writeChannel.write(blockPeek.getRawBuffer());

                if (bytesWritten == -1)
                {
                    context.take(DISCARD);
                    return false;
                }
                else
                {
                    context.bytesWritten += bytesWritten;

                    if (context.bytesWritten == blockPeek.getBlockLength())
                    {
                        blockPeek.markCompleted();
                        context.take(DEFAULT);
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }
            }
        }

        @Override
        protected List<Step<SenderContext>> steps()
        {
            return Arrays.asList(pollState, awaitChannelState, writeState);
        }

    }

    class DiscardState implements State<SenderContext>
    {

        @Override
        public int doWork(SenderContext context) throws Exception
        {
            final BlockPeek blockPeek = context.blockPeek;

            if (sendFailureHandler != null)
            {
                final Iterator<DirectBuffer> messagesIt = blockPeek.iterator();
                while (messagesIt.hasNext())
                {
                    final DirectBuffer nextMessage = messagesIt.next();
                    sendFailureHandler.onFragment(nextMessage, 0, nextMessage.capacity(), blockPeek.getStreamId(), false);
                }
            }

            blockPeek.markFailed();

            context.take(DEFAULT);

            return 1;
        }
    }

    public void removeChannel(TransportChannel c)
    {
        stateMachineAgent.addCommand((ctx) ->
        {
            channelMap.remove(c.getStreamId());
        });
    }

    public void registerChannel(TransportChannel c)
    {
        stateMachineAgent.addCommand((ctx) ->
        {
            channelMap.put(c.getStreamId(), c);
        });
    }

    static class SenderContext extends SimpleStateMachineContext
    {
        final BlockPeek blockPeek = new BlockPeek();

        CompletableFuture<Void> channelFuture;
        TransportChannel writeChannel;
        int bytesWritten;

        SenderContext(StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        @Override
        public void reset()
        {
            writeChannel = null;
            bytesWritten = 0;
            channelFuture = null;
        }
    }
}
