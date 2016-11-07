package org.camunda.tngp.logstreams;

import java.io.File;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.impl.PositionUtil;
import org.camunda.tngp.logstreams.impl.LogBlockIndex;
import org.camunda.tngp.logstreams.impl.LogStreamController;
import org.camunda.tngp.logstreams.impl.StreamContext;
import org.camunda.tngp.logstreams.impl.StreamImpl;
import org.camunda.tngp.logstreams.impl.TimeBasedSnapshotPolicy;
import org.camunda.tngp.logstreams.impl.fs.FsLogStorage;
import org.camunda.tngp.logstreams.impl.fs.FsLogStorageConfiguration;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class FsLogStreamBuilder
{
    protected final String name;
    protected final int id;
    protected int writeBufferSize = 1024  * 1024 * 16;
    protected String logRootPath;
    protected String logDirectory;
    protected int logSegmentSize = 1024 * 1024 * 128;
    protected int initialLogSegmentId = 0;
    protected boolean deleteOnClose;
    protected AgentRunnerService agentRunnerService;
    protected CountersManager countersManager;
    protected SnapshotPolicy snapshotPolicy;

    protected Dispatcher writeBuffer;

    public FsLogStreamBuilder(String name, int id)
    {
        this.name = name;
        this.id = id;
    }

    public FsLogStreamBuilder writeBufferSize(int writeBfferSize)
    {
        this.writeBufferSize = writeBfferSize;
        return this;
    }

    public FsLogStreamBuilder logRootPath(String logRootPath)
    {
        this.logRootPath = logRootPath;
        return this;
    }

    public FsLogStreamBuilder logDirectory(String logDir)
    {
        this.logDirectory = logDir;
        return this;
    }

    public FsLogStreamBuilder initialLogSegmentId(int logFragmentId)
    {
        this.initialLogSegmentId = logFragmentId;
        return this;
    }

    public FsLogStreamBuilder logSegmentSize(int logSegmentSize)
    {
        this.logSegmentSize = logSegmentSize;
        return this;
    }

    public FsLogStreamBuilder deleteOnClose(boolean deleteOnClose)
    {
        this.deleteOnClose = deleteOnClose;
        return this;
    }

    public FsLogStreamBuilder agentRunnerService(AgentRunnerService agentRunnerService)
    {
        this.agentRunnerService = agentRunnerService;
        return this;
    }

    public FsLogStreamBuilder countersManager(CountersManager countersManager)
    {
        this.countersManager = countersManager;
        return this;
    }

    public FsLogStreamBuilder snapshotPolicy(SnapshotPolicy snapshotPolicy)
    {
        this.snapshotPolicy = snapshotPolicy;
        return this;
    }

    public LogStream build()
    {
        final StreamContext ctx = new StreamContext();

        ctx.setLogId(id);
        ctx.setLogName(name);

        initAgentRunnerService(ctx);
        initLogStorage(ctx);
        initSnapshotPolicy(ctx);
        initSnapshotStorage(ctx);
        initBlockIndex(ctx);
        initWriteBuffer(ctx);
        initController(ctx);

        return new StreamImpl(ctx);
    }

    protected void initAgentRunnerService(StreamContext ctx)
    {
        Objects.requireNonNull(agentRunnerService, "No agent runner service provided.");

        ctx.setAgentRunnerService(agentRunnerService);
    }

    protected void initController(StreamContext ctx)
    {
        final LogStreamController logStreamController = new LogStreamController(name, ctx);

        ctx.setLogStreamController(logStreamController);
    }

    protected void initWriteBuffer(StreamContext ctx)
    {
        if (writeBuffer == null)
        {
            // Get position of last entry
            long position = 0;

            final BufferedLogStreamReader logReader = new BufferedLogStreamReader(ctx);
            logReader.seekToLastEvent();

            if (logReader.hasNext())
            {
                final LoggedEvent lastEntry = logReader.next();
                position = lastEntry.getPosition() + 1;
            }

            // dispatcher needs to generate positions greater than the last position
            int partitionId = 0;

            if (position > 0)
            {
                partitionId = PositionUtil.partitionId(position) + 1;
            }

            writeBuffer = Dispatchers.create("log-write-buffer")
                    .bufferSize(writeBufferSize)
                    .subscriptions("log-appender")
                    .initialPartitionId(partitionId + 1)
                    .build();
        }

        ctx.setWriteBuffer(writeBuffer);
    }

    protected void initBlockIndex(StreamContext ctx)
    {
        final LogBlockIndex blockIndex = new LogBlockIndex(100000, (c) ->
        {
            return new UnsafeBuffer(ByteBuffer.allocate(c));
        });

        ctx.setBlockIndex(blockIndex);
    }

    protected void initLogStorage(StreamContext ctx)
    {
        if (logDirectory == null)
        {
            logDirectory = logRootPath + File.separatorChar + name + File.separatorChar;
        }

        final File file = new File(logDirectory);
        file.mkdirs();

        final FsLogStorageConfiguration storageConfig = new FsLogStorageConfiguration(logSegmentSize,
                logDirectory,
                initialLogSegmentId,
                deleteOnClose);

        final FsLogStorage storage = new FsLogStorage(storageConfig);

        storage.open();

        ctx.setLogStorage(storage);
    }

    protected void initSnapshotPolicy(StreamContext ctx)
    {
        if (snapshotPolicy == null)
        {
            final SnapshotPolicy snapshotPolicy = new TimeBasedSnapshotPolicy(Duration.ofMinutes(1));

            ctx.setSnapshotPolicy(snapshotPolicy);
        }
    }

    protected void initSnapshotStorage(StreamContext ctx)
    {
        final SnapshotStorage snapshotStorage = new FsSnapshotStorageBuilder(logDirectory).build();

        ctx.setSnapshotStorage(snapshotStorage);
    }

    public String getLogDirectory()
    {
        return logDirectory;
    }

}
