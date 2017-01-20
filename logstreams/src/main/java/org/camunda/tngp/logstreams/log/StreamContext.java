package org.camunda.tngp.logstreams.log;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.impl.LogStreamController;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class StreamContext
{
    protected int logId;

    protected String logName;

    protected Dispatcher writeBuffer;

    protected LogBlockIndex blockIndex;

    protected LogStorage logStorage;

    protected SnapshotStorage snapshotStorage;

    protected SnapshotPolicy snapshotPolicy;

    protected LogStreamController logStreamController;

    protected AgentRunnerService agentRunnerService;

    protected AgentRunnerService writeBufferAgentRunnerService;

    protected int maxAppendBlockSize;

    protected int indexBlockSize;

    public int getLogId()
    {
        return logId;
    }

    public void setLogId(int logId)
    {
        this.logId = logId;
    }

    public Dispatcher getWriteBuffer()
    {
        return writeBuffer;
    }

    public void setWriteBuffer(Dispatcher writeBuffer)
    {
        this.writeBuffer = writeBuffer;
    }

    public LogBlockIndex getBlockIndex()
    {
        return blockIndex;
    }

    public void setBlockIndex(LogBlockIndex blockIndex)
    {
        this.blockIndex = blockIndex;
    }

    public LogStorage getLogStorage()
    {
        return logStorage;
    }

    public void setLogStorage(LogStorage logStorage)
    {
        this.logStorage = logStorage;
    }

    public String getLogName()
    {
        return logName;
    }

    public void setLogName(String logName)
    {
        this.logName = logName;
    }

    public LogStreamController getLogStreamController()
    {
        return logStreamController;
    }

    public void setLogStreamController(LogStreamController logStreamController)
    {
        this.logStreamController = logStreamController;
    }

    public AgentRunnerService getAgentRunnerService()
    {
        return agentRunnerService;
    }

    public void setAgentRunnerService(AgentRunnerService agentRunnerService)
    {
        this.agentRunnerService = agentRunnerService;
    }

    public SnapshotStorage getSnapshotStorage()
    {
        return snapshotStorage;
    }

    public void setSnapshotStorage(SnapshotStorage snapshotStorage)
    {
        this.snapshotStorage = snapshotStorage;
    }

    public SnapshotPolicy getSnapshotPolicy()
    {
        return snapshotPolicy;
    }

    public void setSnapshotPolicy(SnapshotPolicy snapshotPolicy)
    {
        this.snapshotPolicy = snapshotPolicy;
    }

    public int getMaxAppendBlockSize()
    {
        return maxAppendBlockSize;
    }

    public void setMaxAppendBlockSize(int maxAppendBlockSize)
    {
        this.maxAppendBlockSize = maxAppendBlockSize;
    }

    public int getIndexBlockSize()
    {
        return indexBlockSize;
    }

    public void setIndexBlockSize(int indexBlockSize)
    {
        this.indexBlockSize = indexBlockSize;
    }

    public AgentRunnerService getWriteBufferAgentRunnerService()
    {
        return writeBufferAgentRunnerService;
    }

    public void setWriteBufferAgentRunnerService(AgentRunnerService writeBufferAgentRunnerService)
    {
        this.writeBufferAgentRunnerService = writeBufferAgentRunnerService;
    }

}
