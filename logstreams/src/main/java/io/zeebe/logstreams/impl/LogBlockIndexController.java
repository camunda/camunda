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

import static io.zeebe.logstreams.impl.LogEntryDescriptor.getPosition;
import static io.zeebe.logstreams.log.LogStreamUtil.INVALID_ADDRESS;
import static io.zeebe.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
import static io.zeebe.logstreams.spi.LogStorage.OP_RESULT_INVALID_ADDR;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.spi.*;
import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocators;
import io.zeebe.util.sched.*;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.*;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.Position;
import org.slf4j.Logger;

/**
 * Represents the log block index controller, which creates the log block index
 * for the given log storage.
 */
public class LogBlockIndexController extends ZbActor
{
    public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

    /**
     * The default deviation is 10%. That means for blocks which are filled 90%
     * a block index will be created.
     */
    public static final float DEFAULT_DEVIATION = 0.1f;

    /**
     * Per default we start with reading from log storage.
     */
    private Runnable currentRunnable = this::readLogStorage;
    private Duration currentDelay = Duration.ZERO;

    private final Runnable runCurrentWork = this::runCurrentWork;
    private final Runnable readLogStorage = this::readLogStorage;
    private final Runnable tryCreateBlockIndex = this::tryToCreateBlockIndex;
    private final Runnable createSnapshot = this::createSnapshot;

    //  MANDATORY //////////////////////////////////////////////////

    private final String name;
    private final LogStorage logStorage;
    private final LogBlockIndex blockIndex;
    private final ZbActorScheduler actorScheduler;

    /**
     * Defines the block size for which an index will be created.
     */
    private final int indexBlockSize;

    /**
     * The deviation which will be used in calculation of the index block size.
     * It defines the allowable tolerance. That means if the deviation is set to 0.1f (10%),
     * an index will be created if the block is 90 % filled.
     */
    private final float deviation;

    private final SnapshotStorage snapshotStorage;
    private final Duration snapshotPeriod;
    private final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
    private final CompleteEventsInBlockProcessor readResultProcessor = new CompleteEventsInBlockProcessor();

    private final AtomicBoolean isOpenend = new AtomicBoolean(false);

    private final CompletableActorFuture<Void> openFuture = new CompletableActorFuture<>();


    // INTERNAL ///////////////////////////////////////////////////

    private int currentBlockSize = 0;
    private long currentBlockAddress = INVALID_ADDRESS;
    private long firstEventPosition = 0;

    private long nextAddress = INVALID_ADDRESS;
    private int bufferSize;
    private ByteBuffer ioBuffer;
    private AllocatedBuffer allocatedBuffer;
    private final Position commitPosition;
    private final ActorConditions onCommitPositionUpdatedConditions;

    public LogBlockIndexController(LogStreamImpl.LogStreamBuilder logStreamBuilder, Position commitPosition, ActorConditions onCommitPositionUpdatedConditions)
    {
        this.name = logStreamBuilder.getLogName() + ".index";
        this.logStorage = logStreamBuilder.getLogStorage();
        this.blockIndex = logStreamBuilder.getBlockIndex();
        this.actorScheduler = logStreamBuilder.getActorScheduler();
        this.commitPosition = commitPosition;
        this.onCommitPositionUpdatedConditions = onCommitPositionUpdatedConditions;

        this.deviation = logStreamBuilder.getDeviation();
        this.indexBlockSize = (int) (logStreamBuilder.getIndexBlockSize() * (1f - deviation));
        this.snapshotStorage = logStreamBuilder.getSnapshotStorage();
        this.snapshotPeriod = logStreamBuilder.getSnapshotPeriod();
        this.bufferSize = logStreamBuilder.getReadBlockSize();
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
        allocatedBuffer = BufferAllocators.allocateDirect(bufferSize);
        ioBuffer = allocatedBuffer.getRawBuffer();
        buffer.wrap(ioBuffer);

        if (!logStorage.isOpen())
        {
            logStorage.open();
        }

        recoverBlockIndex();
    }

    private void runCurrentWork()
    {
        actor.runDelayed(currentDelay, currentRunnable);
    }

    private void recoverBlockIndex()
    {
        try
        {
            LOG.trace("Recover block index.");
            final long recoveredAddress = logStorage.getFirstBlockAddress();
            final ReadableSnapshot lastSnapshot = snapshotStorage.getLastSnapshot(name);
            if (lastSnapshot != null)
            {
                lastSnapshot.recoverFromSnapshot(blockIndex);
                nextAddress = Math.max(blockIndex.lookupBlockAddress(lastSnapshot.getPosition()),
                                       recoveredAddress);
            }
            else
            {
                nextAddress = recoveredAddress;
            }

            // register condition after index is recovered
            final ActorCondition onCommitCondition = actor.onCondition("log-index-on-commit-position", runCurrentWork);
            onCommitPositionUpdatedConditions.registerConsumer(onCommitCondition);

            openFuture.complete(null);

            actor.runAtFixedRate(snapshotPeriod, createSnapshot);
            // start reading after started
            runCurrentWork();
        }
        catch (Exception e)
        {
            LOG.error("Fail to recover block index.", e);
            openFuture.completeExceptionally(e);
        }
    }

    private void readLogStorage()
    {
        if (nextAddress == INVALID_ADDRESS)
        {
            nextAddress = resolveLastValidAddress();
        }
        else
        {
            final long currentAddress = nextAddress;

            // read buffer with only complete events
            final long nextAddressToRead = logStorage.read(ioBuffer, currentAddress, readResultProcessor);
            if (nextAddressToRead > currentAddress)
            {
                tryToCreateBlockIndex(currentAddress);
                // set next address
                nextAddress = nextAddressToRead;
                runCurrentWork();
            }
            else if (nextAddressToRead == OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY)
            {
                increaseBufferSize();
                runCurrentWork();
            }
            else if (nextAddressToRead == OP_RESULT_INVALID_ADDR)
            {
                LOG.error("Can't read from illegal address: {}", currentAddress);
            }

        }
    }

    private long resolveLastValidAddress()
    {
        long newAddress = 0;
        if (blockIndex.size() > 0)
        {
            newAddress = blockIndex.getAddress(blockIndex.size());
        }
        if (newAddress <= 0)
        {
            newAddress = logStorage.getFirstBlockAddress();
        }
        return newAddress;
    }

    private void tryToCreateBlockIndex(long currentAddress)
    {
        if (currentBlockAddress == INVALID_ADDRESS)
        {
            currentBlockAddress = currentAddress;
        }

        currentBlockSize += ioBuffer.position();
        // if block size is greater then or equals to index block size we will create an index
        if (currentBlockSize >= indexBlockSize)
        {
            tryToCreateBlockIndex();
        }
        else
        {
            // block was not filled enough
            // read next events into buffer after the current read events
            ioBuffer.clear();
        }
    }

    private void tryToCreateBlockIndex()
    {
        final boolean blockWasCreated = createBlockIndex();

        if (blockWasCreated)
        {
            currentBlockSize = 0;

            // read next bytes
            currentRunnable = readLogStorage;
            currentDelay = Duration.ZERO;
        }
        else
        {
            // try again
            currentRunnable = tryCreateBlockIndex;
            currentDelay = Duration.ofMillis(500);
        }
    }

    private boolean createBlockIndex()
    {
        final boolean canCreateBlock = readResultProcessor.getLastReadEventPosition() <= getCommitPosition();
        if (canCreateBlock)
        {
            firstEventPosition = getPosition(buffer, 0);

            createBlockIdx(currentBlockAddress);

            // reset buffer position and limit for reuse
            ioBuffer.clear();

            // reset cached block address
            currentBlockAddress = INVALID_ADDRESS;
        }
        return canCreateBlock;
    }

    private void createBlockIdx(long addressOfFirstEventInBlock)
    {
        // write block IDX
        final long position = firstEventPosition;
        blockIndex.addBlock(position, addressOfFirstEventInBlock);

        LOG.trace("Add block to index with position {} and address {}.", position, addressOfFirstEventInBlock);
    }

    private long lastWrittenSnapshotEventPosition = -1;

    private void createSnapshot()
    {
        SnapshotWriter snapshotWriter = null;
        try
        {
            if (lastWrittenSnapshotEventPosition < firstEventPosition)
            {
                // should do recovery if fails to flush because of corrupted block index - see #8

                // flush the log to ensure that the snapshot doesn't contains indexes of unwritten events
                logStorage.flush();

                snapshotWriter = snapshotStorage.createSnapshot(name, firstEventPosition);

                snapshotWriter.writeSnapshot(blockIndex);
                snapshotWriter.commit();
                LOG.debug("Created snapshot for block index {}.", name);

                lastWrittenSnapshotEventPosition = firstEventPosition;
            }
        }
        catch (Exception e)
        {
            LOG.error("Failed to create snapshot", e);

            if (snapshotWriter != null)
            {
                snapshotWriter.abort();
            }
        }
        finally
        {
            firstEventPosition = 0;
        }
    }

    private void increaseBufferSize()
    {
        // increase buffer and try again
        bufferSize *= 2;

        allocatedBuffer.close();

        allocatedBuffer = BufferAllocators.allocateDirect(bufferSize);
        ioBuffer = allocatedBuffer.getRawBuffer();
        buffer.wrap(ioBuffer);
    }

    public void close()
    {
        closeAsync().join();
    }

    public ActorFuture<Void> closeAsync()
    {
        if (isOpenend.compareAndSet(true, false))
        {
            currentRunnable = () -> {};
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
        allocatedBuffer.close();

        isOpenend.set(false);
    }

    public void truncate()
    {
        actor.call(() ->
        {
            if (isOpened())
            {
                LOG.trace("Truncate and reset internal. Log block index controller {}.", name);
                currentBlockSize = 0;
                nextAddress = currentBlockAddress;

                currentBlockAddress = INVALID_ADDRESS;
                firstEventPosition = 0;
            }
        });
    }

    public boolean isClosed()
    {
        return !isOpenend.get();
    }

    public boolean isOpened()
    {
        return isOpenend.get();
    }

    public long getNextAddress()
    {
        return nextAddress;
    }

    public int getIndexBlockSize()
    {
        return indexBlockSize;
    }

    public long getCommitPosition()
    {
        if (commitPosition == null)
        {
            return INVALID_ADDRESS;
        }
        else
        {
            return commitPosition.get();
        }
    }

}
