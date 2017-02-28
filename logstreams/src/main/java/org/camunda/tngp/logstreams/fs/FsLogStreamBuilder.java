package org.camunda.tngp.logstreams.fs;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.impl.LogStreamImpl;
import org.camunda.tngp.logstreams.impl.log.fs.FsLogStorage;
import org.camunda.tngp.logstreams.impl.log.fs.FsLogStorageConfiguration;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.snapshot.TimeBasedSnapshotPolicy;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.util.agent.AgentRunnerService;

import java.io.File;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class FsLogStreamBuilder extends LogStreamImpl.LogStreamBuilder
{
    // MANDATORY /////
    // LogController Base
    protected final String logName;
    protected final int logId;
    protected AgentRunnerService agentRunnerService;
    protected LogStorage logStorage;
    protected LogBlockIndex logBlockIndex;

    protected String logRootPath;
    protected String logDirectory;

    protected CountersManager countersManager;

    // OPTIONAL ////////////////////////////////////////////
    protected boolean withoutLogStreamController;
    protected int initialLogSegmentId = 0;
    protected boolean deleteOnClose;
    protected int maxAppendBlockSize = 1024 * 1024 * 4;
    protected int writeBufferSize = 1024 * 1024 * 16;
    protected int logSegmentSize = 1024 * 1024 * 128;
    protected int indexBlockSize = 1024 * 1024 * 4;
    protected SnapshotPolicy snapshotPolicy;
    protected SnapshotStorage snapshotStorage;

    protected AgentRunnerService writeBufferAgentRunnerService;
    protected Dispatcher writeBuffer;

    public FsLogStreamBuilder(String logName, int logId)
    {
        this.logName = logName;
        this.logId = logId;
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

    public FsLogStreamBuilder writeBufferSize(int writeBufferSize)
    {
        this.writeBufferSize = writeBufferSize;
        return this;
    }

    public FsLogStreamBuilder maxAppendBlockSize(int maxAppendBlockSize)
    {
        this.maxAppendBlockSize = maxAppendBlockSize;
        return this;
    }

    public FsLogStreamBuilder writeBufferAgentRunnerService(AgentRunnerService writeBufferAgentRunnerService)
    {
        this.writeBufferAgentRunnerService = writeBufferAgentRunnerService;
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

    public FsLogStreamBuilder indexBlockSize(int indexBlockSize)
    {
        this.indexBlockSize = indexBlockSize;
        return this;
    }

    public FsLogStreamBuilder logStorage(LogStorage logStorage)
    {
        this.logStorage = logStorage;
        return this;
    }

    public FsLogStreamBuilder logBlockIndex(LogBlockIndex logBlockIndex)
    {
        this.logBlockIndex = logBlockIndex;
        return this;
    }

    public FsLogStreamBuilder withoutLogStreamController(boolean withoutLogStreamController)
    {
        this.withoutLogStreamController = withoutLogStreamController;
        return this;
    }

    public FsLogStreamBuilder writeBuffer(Dispatcher writeBuffer)
    {
        this.writeBuffer = writeBuffer;
        return this;
    }

    public FsLogStreamBuilder snapshotStorage(SnapshotStorage snapshotStorage)
    {
        this.snapshotStorage = snapshotStorage;
        return this;
    }

    public FsLogStreamBuilder snapshotPolicy(SnapshotPolicy snapshotPolicy)
    {
        this.snapshotPolicy = snapshotPolicy;
        return this;
    }

    // GETTER implementation of abstract builder ///////////////////////////////////////////////

    @Override
    public String getLogName()
    {
        return logName;
    }

    @Override
    public int getLogId()
    {
        return logId;
    }

    @Override
    public AgentRunnerService getAgentRunnerService()
    {
        Objects.requireNonNull(agentRunnerService, "No agent runner service provided.");
        return agentRunnerService;
    }

    protected void initLogStorage()
    {
        if (logDirectory == null)
        {
            logDirectory = logRootPath + File.separatorChar + logName + File.separatorChar;
        }

        final File file = new File(logDirectory);
        file.mkdirs();

        final FsLogStorageConfiguration storageConfig = new FsLogStorageConfiguration(logSegmentSize,
            logDirectory,
            initialLogSegmentId,
            deleteOnClose);

        logStorage = new FsLogStorage(storageConfig);
        logStorage.open();
    }

    @Override
    public LogStorage getLogStorage()
    {
        if (logStorage == null)
        {
            initLogStorage();
        }
        return logStorage;
    }

    @Override
    public LogBlockIndex getBlockIndex()
    {
        if (logBlockIndex == null)
        {
            this.logBlockIndex = new LogBlockIndex(100000, (c) -> new UnsafeBuffer(ByteBuffer.allocate(c)));
        }
        return logBlockIndex;
    }

    @Override
    public int getMaxAppendBlockSize()
    {
        return maxAppendBlockSize;
    }

    @Override
    public int getIndexBlockSize()
    {
        return indexBlockSize;
    }

    @Override
    public SnapshotPolicy getSnapshotPolicy()
    {
        if (snapshotPolicy == null)
        {
            snapshotPolicy = new TimeBasedSnapshotPolicy(Duration.ofMinutes(1));
        }
        return snapshotPolicy;
    }

    @Override
    public AgentRunnerService getWriteBufferAgentRunnerService()
    {
        return writeBufferAgentRunnerService;
    }

    @Override
    public Dispatcher getWriteBuffer()
    {
        if (writeBuffer == null)
        {
            final BufferedLogStreamReader logReader = new BufferedLogStreamReader(getLogStorage(), getBlockIndex());
            writeBuffer = initWriteBuffer(writeBuffer, logReader, logName, writeBufferSize);
        }
        return writeBuffer;
    }

    @Override
    public SnapshotStorage getSnapshotStorage()
    {
        if (snapshotStorage == null)
        {
            snapshotStorage = new FsSnapshotStorageBuilder(logDirectory).build();
        }
        return snapshotStorage;
    }

    public boolean isWithoutLogStreamController()
    {
        return withoutLogStreamController;
    }
}
