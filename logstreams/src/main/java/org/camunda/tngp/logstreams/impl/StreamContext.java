package org.camunda.tngp.logstreams.impl;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class StreamContext
{
    protected int logId;

    protected String logName;

    protected Dispatcher writeBuffer;

    protected LogBlockIndex blockIndex;

    protected LogStorage logStorage;

    protected LogStreamController logStreamController;

    protected AgentRunnerService agentRunnerService;

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
}
