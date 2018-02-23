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

import static io.zeebe.logstreams.log.LogStreamUtil.INVALID_ADDRESS;
import static io.zeebe.logstreams.log.LogStreamUtil.getAddressForPosition;
import static io.zeebe.util.EnsureUtil.ensureFalse;
import static io.zeebe.util.EnsureUtil.ensureGreaterThanOrEqual;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import java.io.File;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.impl.PositionUtil;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.ZbActorScheduler;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicLongPosition;
import org.agrona.concurrent.status.CountersManager;
import org.agrona.concurrent.status.Position;

/**
 * Represents the implementation of the LogStream interface.
 */
public final class LogStreamImpl implements LogStream
{
    public static final String EXCEPTION_MSG_TRUNCATE_FAILED = "Truncation failed! Position %d was not found.";
    public static final String EXCEPTION_MSG_TRUNCATE_AND_LOG_STREAM_CTRL_IN_PARALLEL = "Can't truncate the log storage and have a log stream controller active at the same time.";
    public static final String EXCEPTION_MSG_TRUNCATE_COMMITTED_POSITION = "Can't truncate position which is already committed!";

    private static final int DEFAULT_INDEX_BLOCK_SIZE = 1024 * 1024 * 4;
    private static final int DEFAULT_READ_BLOCK_SIZE = 1024;

    private volatile int term = 0;

    private final DirectBuffer topicName;
    private final int partitionId;
    private final String name;

    private final LogStorage logStorage;
    private final LogBlockIndex blockIndex;
    private final ZbActorScheduler actorScheduler;

    private final LogBlockIndexController logBlockIndexController;

    private final ActorConditions onCommitPositionUpdatedConditions = new ActorConditions();

    private LogStreamController logStreamController;
    private Dispatcher writeBuffer;
    private final Position commitPosition = new AtomicLongPosition();

    private LogStreamImpl(final LogStreamBuilder logStreamBuilder)
    {
        final DirectBuffer topicName = logStreamBuilder.getTopicName();
        if (topicName.capacity() > MAX_TOPIC_NAME_LENGTH)
        {
            throw new RuntimeException(String.format("Topic name exceeds max length (%d > %d bytes)", topicName.capacity(), MAX_TOPIC_NAME_LENGTH));
        }

        this.topicName = cloneBuffer(topicName);
        this.partitionId = logStreamBuilder.getPartitionId();
        this.name = logStreamBuilder.getLogName();
        this.logStorage = logStreamBuilder.getLogStorage();
        this.blockIndex = logStreamBuilder.getBlockIndex();
        this.actorScheduler = logStreamBuilder.getActorScheduler();

        commitPosition.setOrdered(INVALID_ADDRESS);
        this.logBlockIndexController = new LogBlockIndexController(logStreamBuilder, commitPosition, onCommitPositionUpdatedConditions);

        if (!logStreamBuilder.isLogStreamControllerDisabled())
        {
            this.logStreamController = new LogStreamController(logStreamBuilder);
            this.writeBuffer = logStreamBuilder.getWriteBuffer();
        }
    }

    @Override
    public LogBlockIndexController getLogBlockIndexController()
    {
        return logBlockIndexController;
    }

    @Override
    public LogStreamController getLogStreamController()
    {
        return logStreamController;
    }

    @Override
    public DirectBuffer getTopicName()
    {
        return topicName;
    }

    @Override
    public int getPartitionId()
    {
        return partitionId;
    }

    @Override
    public String getLogName()
    {
        return name;
    }

    @Override
    public void open()
    {
        openAsync().join();
    }

    @Override
    public ActorFuture<Void> openAsync()
    {
        if (logStreamController != null)
        {
            final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

            actorScheduler.submitActor(new ZbActor()
            {
                @Override
                protected void onActorStarted()
                {
                    final List<ActorFuture<Void>> openFutures = Arrays
                            .asList(logBlockIndexController.openAsync(), openStreamControlling(actorScheduler, logStreamController.getMaxAppendBlockSize()));

                    actor.runOnCompletion(openFutures, failure ->
                    {
                        if (failure == null)
                        {
                            future.complete(null);
                        }
                        else
                        {
                            future.completeExceptionally(failure);
                        }
                    });
                }
            });

            return future;
        }
        else
        {
            return logBlockIndexController.openAsync();
        }
    }

    @Override
    public void close()
    {
        closeAsync().join();
    }

    @Override
    public ActorFuture<Void> closeAsync()
    {
        final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();

        final LogStreamController logStreamController = this.logStreamController;
        final Dispatcher writeBuffer = this.writeBuffer;
        actorScheduler.submitActor(new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                if (writeBuffer != null)
                {
                    actor.runOnCompletion(logBlockIndexController.closeAsync(), (v1, t1) ->
                    {
                        actor.runOnCompletion(logStreamController.closeAsync(), (v2, t2) ->
                        {
                            actor.runOnCompletion(writeBuffer.closeAsync(), (v3, t3) ->
                            {
                                logStorage.close();

                                LogStreamImpl.this.writeBuffer = null;

                                if (t1 != null)
                                {
                                    closeFuture.completeExceptionally(t1);
                                }
                                else if (t2 != null)
                                {
                                    closeFuture.completeExceptionally(t2);
                                }
                                else if (t3 != null)
                                {
                                    closeFuture.completeExceptionally(t3);
                                }
                                else
                                {
                                    closeFuture.complete(null);
                                }
                            });
                        });
                    });
                }
                else
                {
                    actor.runOnCompletion(logBlockIndexController.closeAsync(), (v, t) ->
                    {
                        logStorage.close();

                        if (t == null)
                        {
                            closeFuture.complete(null);
                        }
                        else
                        {
                            closeFuture.completeExceptionally(t);
                        }
                    });
                }
            }
        });
        return closeFuture;
    }

    @Override
    public long getCurrentAppenderPosition()
    {
        return logStreamController == null ? 0 : logStreamController.getCurrentAppenderPosition();
    }

    @Override
    public long getCommitPosition()
    {
        return commitPosition.get();
    }

    @Override
    public void setCommitPosition(long commitPosition)
    {
        this.commitPosition.setOrdered(commitPosition);

        onCommitPositionUpdatedConditions.signalConsumers();
    }

    @Override
    public synchronized void registerOnCommitPositionUpdatedCondition(ActorCondition condition)
    {
        onCommitPositionUpdatedConditions.registerConsumer(condition);
    }

    @Override
    public synchronized void removeOnCommitPositionUpdatedCondition(ActorCondition condition)
    {
        onCommitPositionUpdatedConditions.removeConsumer(condition);
    }

    @Override
    public int getTerm()
    {
        return term;
    }

    @Override
    public void setTerm(int term)
    {
        this.term = term;
    }

    @Override
    public LogStorage getLogStorage()
    {
        return logStorage;
    }

    @Override
    public LogBlockIndex getLogBlockIndex()
    {
        return blockIndex;
    }

    @Override
    public ActorFuture<Void> closeLogStreamController()
    {
        final LogStreamController logStreamController = this.logStreamController;
        final Dispatcher writeBuffer = this.writeBuffer;
        if (logStreamController != null && writeBuffer != null)
        {
            final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();

            actorScheduler.submitActor(new ZbActor()
            {
                @Override
                protected void onActorStarted()
                {
                    actor.runOnCompletion(logStreamController.closeAsync(), (v2, t2) ->
                    {
                        actor.runOnCompletion(writeBuffer.closeAsync(), (v1, t1) ->
                        {
                            LogStreamImpl.this.writeBuffer = null;

                            if (t1 != null)
                            {
                                closeFuture.completeExceptionally(t1);
                            }
                            else if (t2 != null)
                            {
                                closeFuture.completeExceptionally(t2);
                            }
                            else
                            {
                                closeFuture.complete(null);
                            }
                        });
                    });
                }
            });

            return closeFuture;
        }
        else
        {
            return CompletableActorFuture.completed(null);
        }
    }

    @Override
    public ActorFuture<Void> openLogStreamController()
    {
        return openLogStreamController(actorScheduler, DEFAULT_MAX_APPEND_BLOCK_SIZE);
    }

    @Override
    public ActorFuture<Void> openLogStreamController(ZbActorScheduler actorScheduler)
    {
        return openLogStreamController(actorScheduler, DEFAULT_MAX_APPEND_BLOCK_SIZE);
    }

    @Override
    public ActorFuture<Void> openLogStreamController(ZbActorScheduler actorScheduler,
                                                           int maxAppendBlockSize)
    {
        return openStreamControlling(actorScheduler, maxAppendBlockSize);
    }

    private ActorFuture<Void> openStreamControlling(ZbActorScheduler actorScheduler, int maxAppendBlockSize)
    {
        if ((writeBuffer != null && writeBuffer.isClosed()) || writeBuffer == null)
        {
            final LogStreamBuilder logStreamBuilder = createNewBuilder(actorScheduler, maxAppendBlockSize);
            writeBuffer = logStreamBuilder.getWriteBuffer();
            if (logStreamController == null)
            {
                logStreamController = new LogStreamController(logStreamBuilder);
            }
            else
            {
                logStreamController.wrap(logStreamBuilder);
            }
        }
        return logStreamController.openAsync();
    }

    private LogStreamBuilder createNewBuilder(ZbActorScheduler actorScheduler, int maxAppendBlockSize)
    {
        if (!logStorage.isOpen())
        {
            logStorage.open();
        }
        return new LogStreamBuilder(topicName, partitionId)
            .logStorage(logStorage)
            .logBlockIndex(blockIndex)
            .actorScheduler(actorScheduler)
            .maxAppendBlockSize(maxAppendBlockSize);
    }

    @Override
    public Dispatcher getWriteBuffer()
    {
        return writeBuffer;
    }

    @Override
    public void truncate(long position)
    {
        if (logStreamController != null && !logStreamController.isClosed())
        {
            throw new IllegalStateException(EXCEPTION_MSG_TRUNCATE_AND_LOG_STREAM_CTRL_IN_PARALLEL);
        }

        if (position <= getCommitPosition())
        {
            throw new IllegalArgumentException(EXCEPTION_MSG_TRUNCATE_COMMITTED_POSITION);
        }

        final long truncateAddress = getAddressForPosition(this, position);
        if (truncateAddress != INVALID_ADDRESS)
        {
            logStorage.truncate(truncateAddress);
            logBlockIndexController.truncate();
        }
        else
        {
            throw  new IllegalArgumentException(String.format(EXCEPTION_MSG_TRUNCATE_FAILED, position));
        }
    }

    @Override
    public String toString()
    {
        return "LogStreamImpl{" +
            "topicName=" + bufferAsString(topicName) +
            ", partitionId=" + partitionId +
            ", term=" + term +
            ", name='" + name + '\'' +
            '}';
    }

    // BUILDER ////////////////////////
    public static class LogStreamBuilder<T extends LogStreamBuilder>
    {
        // MANDATORY /////
        // LogController Base
        protected final DirectBuffer topicName;
        protected final int partitionId;
        protected final String logName;
        protected ZbActorScheduler actorScheduler;
        protected LogStorage logStorage;
        protected LogBlockIndex logBlockIndex;

        protected String logRootPath;
        protected String logDirectory;

        protected CountersManager countersManager;

        // OPTIONAL ////////////////////////////////////////////
        protected boolean logStreamControllerDisabled;
        protected int initialLogSegmentId = 0;
        protected boolean deleteOnClose;
        protected int maxAppendBlockSize = 1024 * 1024 * 4;
        protected int writeBufferSize = 1024 * 1024 * 16;
        protected int logSegmentSize = 1024 * 1024 * 128;
        protected int indexBlockSize = DEFAULT_INDEX_BLOCK_SIZE;
        protected float deviation = LogBlockIndexController.DEFAULT_DEVIATION;
        protected int readBlockSize = DEFAULT_READ_BLOCK_SIZE;
        protected Duration snapshotPeriod;
        protected SnapshotStorage snapshotStorage;

        protected Dispatcher writeBuffer;

        public LogStreamBuilder(final DirectBuffer topicName, final int partitionId)
        {
            this.topicName = topicName;
            this.partitionId = partitionId;
            this.logName = String.format("%s.%d", bufferAsString(topicName), partitionId);
        }

        @SuppressWarnings("unchecked")
        protected T self()
        {
            return (T) this;
        }

        public T logRootPath(String logRootPath)
        {
            this.logRootPath = logRootPath;
            return self();
        }

        public T logDirectory(String logDir)
        {
            this.logDirectory = logDir;
            return self();
        }

        public T writeBufferSize(int writeBufferSize)
        {
            this.writeBufferSize = writeBufferSize;
            return self();
        }

        public T maxAppendBlockSize(int maxAppendBlockSize)
        {
            this.maxAppendBlockSize = maxAppendBlockSize;
            return self();
        }

        public T initialLogSegmentId(int logFragmentId)
        {
            this.initialLogSegmentId = logFragmentId;
            return self();
        }

        public T logSegmentSize(int logSegmentSize)
        {
            this.logSegmentSize = logSegmentSize;
            return self();
        }

        public T deleteOnClose(boolean deleteOnClose)
        {
            this.deleteOnClose = deleteOnClose;
            return self();
        }

        public T actorScheduler(ZbActorScheduler actorScheduler)
        {
            this.actorScheduler = actorScheduler;
            return self();
        }

        public T countersManager(CountersManager countersManager)
        {
            this.countersManager = countersManager;
            return self();
        }

        public T indexBlockSize(int indexBlockSize)
        {
            this.indexBlockSize = indexBlockSize;
            return self();
        }

        public T deviation(float deviation)
        {
            this.deviation = deviation;
            return self();
        }

        public T logStorage(LogStorage logStorage)
        {
            this.logStorage = logStorage;
            return self();
        }

        public T logBlockIndex(LogBlockIndex logBlockIndex)
        {
            this.logBlockIndex = logBlockIndex;
            return self();
        }

        public T logStreamControllerDisabled(boolean logStreamControllerDisabled)
        {
            this.logStreamControllerDisabled = logStreamControllerDisabled;
            return self();
        }

        public T writeBuffer(Dispatcher writeBuffer)
        {
            this.writeBuffer = writeBuffer;
            return self();
        }

        public T snapshotStorage(SnapshotStorage snapshotStorage)
        {
            this.snapshotStorage = snapshotStorage;
            return self();
        }

        public T snapshotPeriod(Duration snapshotPeriod)
        {
            this.snapshotPeriod = snapshotPeriod;
            return self();
        }

        public T readBlockSize(int readBlockSize)
        {
            this.readBlockSize = readBlockSize;
            return self();
        }

        // getter /////////////////

        public String getLogDirectory()
        {
            if (logDirectory == null)
            {
                logDirectory = logRootPath + File.separatorChar + logName + File.separatorChar;
            }
            return logDirectory;
        }

        public DirectBuffer getTopicName()
        {
            return topicName;
        }

        public int getPartitionId()
        {
            return partitionId;
        }

        public String getLogName()
        {
            return logName;
        }

        public ZbActorScheduler getActorScheduler()
        {
            Objects.requireNonNull(actorScheduler, "No actor scheduler provided.");
            return actorScheduler;
        }

        protected void initLogStorage()
        {
        }

        public LogStorage getLogStorage()
        {
            if (logStorage == null)
            {
                initLogStorage();
            }
            return logStorage;
        }

        public LogBlockIndex getBlockIndex()
        {
            if (logBlockIndex == null)
            {
                this.logBlockIndex = new LogBlockIndex(100000, (c) -> new UnsafeBuffer(ByteBuffer.allocate(c)));
            }
            return logBlockIndex;
        }

        public int getMaxAppendBlockSize()
        {
            return maxAppendBlockSize;
        }

        public int getIndexBlockSize()
        {
            return indexBlockSize;
        }

        public int getReadBlockSize()
        {
            return readBlockSize;
        }

        public Duration getSnapshotPeriod()
        {
            if (snapshotPeriod == null)
            {
                snapshotPeriod = Duration.ofMinutes(1);
            }
            return snapshotPeriod;
        }

        protected Dispatcher initWriteBuffer(Dispatcher writeBuffer, BufferedLogStreamReader logReader,
                                             String logName, int writeBufferSize)
        {
            if (writeBuffer == null)
            {
                // Get position of last entry
                long lastPosition = 0;

                logReader.seekToLastEvent();

                if (logReader.hasNext())
                {
                    final LoggedEvent lastEntry = logReader.next();
                    lastPosition = lastEntry.getPosition();
                }

                // dispatcher needs to generate positions greater than the last position
                int partitionId = 0;

                if (lastPosition > 0)
                {
                    partitionId = PositionUtil.partitionId(lastPosition);
                }

                writeBuffer = Dispatchers.create("log-write-buffer-" + logName)
                    .bufferSize(writeBufferSize)
                    .subscriptions("log-appender")
                    .initialPartitionId(partitionId + 1)
                    .actorScheduler(actorScheduler)
                    .build();
            }
            return writeBuffer;
        }

        public Dispatcher getWriteBuffer()
        {
            if (writeBuffer == null)
            {
                final BufferedLogStreamReader logReader = new BufferedLogStreamReader(true);
                logReader.wrap(getLogStorage(), getBlockIndex());

                writeBuffer = initWriteBuffer(writeBuffer, logReader, logName, writeBufferSize);

                logReader.close();
            }
            return writeBuffer;
        }

        public boolean isLogStreamControllerDisabled()
        {
            return logStreamControllerDisabled;
        }

        public void initSnapshotStorage()
        {
        }

        public SnapshotStorage getSnapshotStorage()
        {
            if (snapshotStorage == null)
            {
                initSnapshotStorage();
            }
            return snapshotStorage;
        }

        public float getDeviation()
        {
            return deviation;
        }

        public LogStream build()
        {
            Objects.requireNonNull(getTopicName(), "topicName");
            ensureGreaterThanOrEqual("partitionId", partitionId, 0);
            Objects.requireNonNull(getLogStorage(), "logStorage");
            Objects.requireNonNull(getBlockIndex(), "blockIndex");
            Objects.requireNonNull(getActorScheduler(), "actorScheduler");
            ensureFalse("deviation", deviation <= 0f || deviation > 1f);

            return new LogStreamImpl(this);
        }
    }

}
