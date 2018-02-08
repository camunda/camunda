/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport.impl.actor;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Iterator;

import io.zeebe.dispatcher.*;
import io.zeebe.transport.NotConnectedException;
import io.zeebe.transport.impl.*;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

public class Sender extends ZbActor
{
    private static final String SUBSCRIPTION_NAME = "sender";
    private static final String NOT_CONNECTED_ERROR = "No available channel for remote";
    private static final String COULD_NOT_WRITE_TO_CHANNEL = "Could not write to channel";

    private final Dispatcher sendBuffer;
    private final int maxPeekSize;
    protected final Duration keepAlivePeriod;
    protected final SendFailureHandler sendFailureHandler;
    private final Runnable sendData = this::sendData;
    private final Runnable peekNextBlock = this::peekNextBlock;

    // state

    private final Int2ObjectHashMap<TransportChannel> channelMap = new Int2ObjectHashMap<>();
    private final ByteBuffer keepAliveBuffer = ByteBuffer.allocate(ControlMessages.KEEP_ALIVE.capacity());
    private TransportChannel writeChannel;
    private int bytesWritten;
    private final BlockPeek blockPeek = new BlockPeek();
    private Subscription senderSubscription;

    public Sender(ActorContext actorContext, TransportContext context)
    {
        this.sendBuffer = context.getSetSendBuffer();
        this.maxPeekSize = context.getMessageMaxLength() * 16;
        this.sendFailureHandler = context.getSendFailureHandler();
        this.keepAlivePeriod = context.getChannelKeepAlivePeriod();

        actorContext.setSender(this);

        ControlMessages.KEEP_ALIVE.getBytes(0, keepAliveBuffer, ControlMessages.KEEP_ALIVE.capacity());
        keepAliveBuffer.flip();
    }

    @Override
    protected void onActorStarted()
    {
        actor.await(sendBuffer.openSubscriptionAsync(SUBSCRIPTION_NAME), (subscription, t) ->
        {
            if (t == null)
            {
                senderSubscription = subscription;
                actor.consume(subscription, peekNextBlock);

                if (keepAlivePeriod != null)
                {
                    actor.runAtFixedRate(keepAlivePeriod, this::sendKeepalives);
                }
            }
            else
            {
                t.printStackTrace();
                actor.close();
            }
        });
    }

    @Override
    protected void onActorClosing()
    {
        actor.await(sendBuffer.closeSubscriptionAsync(senderSubscription), (t) ->
        {
            // done
        });
    }

    private void peekNextBlock()
    {
        final int blockSize = senderSubscription.peekBlock(blockPeek, maxPeekSize, true);

        if (blockSize > 0)
        {
            writeChannel = channelMap.get(blockPeek.getStreamId());

            if (writeChannel != null && !writeChannel.isClosed())
            {
                bytesWritten = 0;
                actor.runUntilDone(sendData);
            }
            else
            {
                actor.run(discard(NOT_CONNECTED_ERROR, new NotConnectedException(NOT_CONNECTED_ERROR)));
            }
        }
    }

    private void sendData()
    {
        final int bytesWritten = writeChannel.write(blockPeek.getRawBuffer());

        if (bytesWritten == -1)
        {
            actor.run(discard(COULD_NOT_WRITE_TO_CHANNEL, null));
            actor.done();
        }
        else
        {
            this.bytesWritten += bytesWritten;

            if (this.bytesWritten == blockPeek.getBlockLength())
            {
                blockPeek.markCompleted();
                actor.done();
                actor.run(peekNextBlock);
            }
            else
            {
                actor.yield();
            }
        }
    }

    private Runnable discard(String failure, Exception failureCause)
    {
        return () ->
        {
            if (sendFailureHandler != null)
            {
                final Iterator<DirectBuffer> messagesIt = blockPeek.iterator();
                while (messagesIt.hasNext())
                {
                    final DirectBuffer nextMessage = messagesIt.next();
                    sendFailureHandler.onFragment(
                            nextMessage,
                            0,
                            nextMessage.capacity(),
                            blockPeek.getStreamId(),
                            failure,
                            failureCause);
                }
            }
            blockPeek.markFailed();
            actor.run(peekNextBlock);
        };
    }

    private void sendKeepalives()
    {
        channelMap.values()
            .forEach((ch) -> actor.runUntilDone(this.sendKeepaliveOnChannel(ch)));
    }

    private Runnable sendKeepaliveOnChannel(TransportChannel ch)
    {
        return () ->
        {
            final int result = ch.write(keepAliveBuffer);

            if (result == -1)
            {
                actor.done();
                keepAliveBuffer.clear();
            }
            else if (keepAliveBuffer.hasRemaining())
            {
                actor.yield();
            }
            else
            {
                keepAliveBuffer.clear();
                actor.done();
            }
        };
    }

    public void removeChannel(TransportChannel c)
    {
        actor.run(() ->
        {
            channelMap.remove(c.getStreamId());
        });
    }

    public void registerChannel(TransportChannel c)
    {
        actor.run(() ->
        {
            channelMap.put(c.getStreamId(), c);
        });
    }

    public ActorFuture<Void> close()
    {
        return actor.close();
    }
}