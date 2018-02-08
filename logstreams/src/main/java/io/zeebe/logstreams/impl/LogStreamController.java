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
package io.zeebe.logstreams.impl;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.positionOffset;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.dispatcher.BlockPeek;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.logstreams.log.LogStreamFailureListener;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.sched.FutureUtil;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.ZbActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.future.CompletedActorFuture;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;

public class LogStreamController extends ZbActor
{
    public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

    private final AtomicBoolean isOpenend = new AtomicBoolean(false);
    private final AtomicBoolean isFailed = new AtomicBoolean(false);

    private final Runnable peekNextBlock = this::peekNextBlock;
    private final Runnable invokeListenersOnFailed = this::invokeListenersOnFailed;
    private final Runnable discardNextBlock = this::discardNextBlock;
    private final Runnable invokeListenersOnRecovered = this::invokeListenersOnRecovered;

    private long firstEventPosition;

    private final CompletableActorFuture<Void> openFuture = new CompletableActorFuture<>();
    private final CompletableActorFuture<Void> recoverFuture = new CompletableActorFuture<>();

    //  MANDATORY //////////////////////////////////////////////////
    private String name;
    private LogStorage logStorage;
    private ZbActorScheduler actorScheduler;

    private final BlockPeek blockPeek = new BlockPeek();
    private int maxAppendBlockSize;
    private Dispatcher writeBuffer;
    private Subscription writeBufferSubscription;

    private List<LogStreamFailureListener> failureListeners = new ArrayList<>();

    public LogStreamController(LogStreamImpl.LogStreamBuilder logStreamBuilder)
    {
        wrap(logStreamBuilder);
    }

    protected void wrap(LogStreamImpl.LogStreamBuilder logStreamBuilder)
    {
        this.name = logStreamBuilder.getLogName() + ".appender";
        this.logStorage = logStreamBuilder.getLogStorage();
        this.actorScheduler = logStreamBuilder.getActorScheduler();

        this.maxAppendBlockSize = logStreamBuilder.getMaxAppendBlockSize();
        this.writeBuffer = logStreamBuilder.getWriteBuffer();
    }

    @Override
    public String getName()
    {
        return name;
    }

    public void open()
    {
        FutureUtil.join(openAsync());
    }

    public ActorFuture<Void> openAsync()
    {
        // reset future
        openFuture.close();
        openFuture.setAwaitingResult();

        if (isOpenend.compareAndSet(false, true))
        {
            actorScheduler.submitActor(this);
        }
        else
        {
            openFuture.complete(null);
        }

        return openFuture;
    }

    @Override
    protected void onActorStarted()
    {
        if (!logStorage.isOpen())
        {
            logStorage.open();
        }

        actor.await(writeBuffer.getSubscriptionAsync("log-appender"), (subscription, failure) ->
        {
            if (failure == null)
            {
                writeBufferSubscription = subscription;

                final Runnable peekBlocksAndAppend = this::peekBlocksAndAppend;
                actor.consume(writeBufferSubscription, peekBlocksAndAppend);

                openFuture.complete(null);
            }
            else
            {
                openFuture.completeExceptionally(failure);
            }
        });
    }

    private void peekBlocksAndAppend()
    {
        actor.runUntilDone(peekNextBlock);
    }

    private void peekNextBlock()
    {
        final int bytesAvailable = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

        if (bytesAvailable > 0)
        {
            final ByteBuffer nioBuffer = blockPeek.getRawBuffer();
            final MutableDirectBuffer buffer = blockPeek.getBuffer();

            final long position = buffer.getLong(positionOffset(messageOffset(0)));
            firstEventPosition = position;

            final long address = logStorage.append(nioBuffer);

            if (address >= 0)
            {
                blockPeek.markCompleted();
                // continue with next block
                actor.yield();
            }
            else
            {
                blockPeek.markFailed();
                actor.done();

                actor.run(invokeListenersOnFailed);
            }
        }
        else
        {
            actor.done();
        }
    }

    private void invokeListenersOnFailed()
    {
        LOG.debug("Failing for first event position: {}", firstEventPosition);

        for (int i = 0; i < failureListeners.size(); i++)
        {
            final LogStreamFailureListener logStreamWriteErrorListener = failureListeners.get(i);
            try
            {
                logStreamWriteErrorListener.onFailed(firstEventPosition);
            }
            catch (Exception e)
            {
                LOG.error("Exception while invoking {}", logStreamWriteErrorListener);
            }
        }

        actor.runUntilDone(discardNextBlock);
    }

    private void discardNextBlock()
    {
        final int available = writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true);

        if (available > 0)
        {
            blockPeek.markFailed();
            // continue with next block
            actor.yield();
        }
        else
        {
            actor.done();

            // block until recovered
            isFailed.set(true);
            actor.await(recoverFuture, (v, failure) ->
            {
                // reset future
                recoverFuture.close();
                recoverFuture.setAwaitingResult();

                actor.run(invokeListenersOnRecovered);
            });
        }
    }

    public void recover()
    {
        if (isFailed.compareAndSet(true, false))
        {
            recoverFuture.complete(null);
        }
    }

    private void invokeListenersOnRecovered()
    {
        for (int i = 0; i < failureListeners.size(); i++)
        {
            final LogStreamFailureListener logStreamWriteErrorListener = failureListeners.get(i);
            try
            {
                logStreamWriteErrorListener.onRecovered();
            }
            catch (Exception e)
            {
                LOG.error("Exception while invoking {}", logStreamWriteErrorListener);
            }
        }
    }

    public void close()
    {
        FutureUtil.join(closeAsync());
    }

    public ActorFuture<Void> closeAsync()
    {
        if (isOpenend.compareAndSet(true, false))
        {
            return actor.close();
        }
        else
        {
            return new CompletedActorFuture<>(null);
        }
    }

    @Override
    protected void onActorClosing()
    {
        isOpenend.set(false);
        isFailed.set(false);
    }

    public void registerFailureListener(LogStreamFailureListener listener)
    {
        actor.call(() -> failureListeners.add(listener));
    }

    public void removeFailureListener(LogStreamFailureListener listener)
    {
        actor.call(() -> failureListeners.remove(listener));
    }

    public boolean isOpened()
    {
        return isOpenend.get();
    }

    public boolean isClosed()
    {
        return !isOpenend.get();
    }

    public boolean isFailed()
    {
        return isFailed.get();
    }

    public long getCurrentAppenderPosition()
    {
        if (writeBufferSubscription != null)
        {
            return writeBufferSubscription.getPosition();
        }
        else
        {
            return -1;
        }
    }

    protected int getMaxAppendBlockSize()
    {
        return maxAppendBlockSize;
    }
}
