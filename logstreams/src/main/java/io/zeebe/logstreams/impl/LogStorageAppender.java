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

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.dispatcher.*;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.sched.*;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;

/**
 * Consume the write buffer and append the blocks on the log storage.
 */
public class LogStorageAppender extends Actor
{
    public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

    private final AtomicBoolean isOpenend = new AtomicBoolean(false);
    private final AtomicBoolean isFailed = new AtomicBoolean(false);

    private final ActorConditions onLogStorageAppendedConditions;

    private Runnable peekedBlockHandler;

    //  MANDATORY //////////////////////////////////////////////////
    private String name;
    private LogStorage logStorage;
    private ActorScheduler actorScheduler;

    private final BlockPeek blockPeek = new BlockPeek();
    private int maxAppendBlockSize;
    private Dispatcher writeBuffer;
    private Subscription writeBufferSubscription;

    public LogStorageAppender(LogStreamImpl.LogStreamBuilder logStreamBuilder, ActorConditions onLogStorageAppendedConditions)
    {
        wrap(logStreamBuilder);

        this.onLogStorageAppendedConditions = onLogStorageAppendedConditions;
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
        openAsync().join();
    }

    public ActorFuture<Void> openAsync()
    {
        if (isOpenend.compareAndSet(false, true))
        {
            return actorScheduler.submitActor(this, true, SchedulingHints.ioBound((short) 0));
        }
        else
        {
            return CompletableActorFuture.completed(null);
        }
    }

    @Override
    protected void onActorStarting()
    {
        if (!logStorage.isOpen())
        {
            logStorage.open();
        }

        actor.runOnCompletion(writeBuffer.getSubscriptionAsync("log-appender"), (subscription, failure) ->
        {
            if (failure == null)
            {
                writeBufferSubscription = subscription;

                actor.consume(writeBufferSubscription, this::peekBlock);
                peekedBlockHandler = this::appendBlock;
            }
            else
            {
                throw new RuntimeException("Failed to open a subscription", failure);
            }
        });
    }

    private void peekBlock()
    {
        if (writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true) > 0)
        {
            peekedBlockHandler.run();
        }
        else
        {
            actor.yield();
        }
    }

    private void appendBlock()
    {
        final ByteBuffer rawBuffer = blockPeek.getRawBuffer();
        final MutableDirectBuffer buffer = blockPeek.getBuffer();

        final long address = logStorage.append(rawBuffer);
        if (address >= 0)
        {
            blockPeek.markCompleted();

            onLogStorageAppendedConditions.signalConsumers();
        }
        else
        {
            isFailed.set(true);

            final long positionOfFirstEventInBlock = LogEntryDescriptor.getPosition(buffer, 0);
            LOG.error("Failed to append log storage on position '{}'. Discard the following blocks.", positionOfFirstEventInBlock);

            // recover log storage from failure - see zeebe-io/zeebe#500
            peekedBlockHandler = this::discardBlock;

            discardBlock();
        }
    }

    private void discardBlock()
    {
        blockPeek.markFailed();
        // continue with next block
        actor.yield();
    }

    public void close()
    {
        closeAsync().join();
    }

    public ActorFuture<Void> closeAsync()
    {
        if (isOpenend.compareAndSet(true, false))
        {
            return actor.close();
        }
        else
        {
            return CompletableActorFuture.completed(null);
        }
    }

    @Override
    protected void onActorClosing()
    {
        isFailed.set(false);
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
            return -1L;
        }
    }
}
