package org.camunda.tngp.log.impl;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.status.AtomicCounter;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.impl.agent.LogAppendHandler;
import org.camunda.tngp.log.impl.agent.LogConductorCmd;
import org.camunda.tngp.log.spi.LogStorage;

public class LogContext
{
    protected int logId;

    protected String logName;

    protected Dispatcher writeBuffer;

    protected AtomicCounter positionCounter;

    protected LogBlockIndex blockIndex;

    protected LogStorage logStorage;

    protected LogAppendHandler logAppendHandler;

    protected ManyToOneConcurrentArrayQueue<LogConductorCmd> toConductorCmdQueue = new ManyToOneConcurrentArrayQueue<>(64);

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

    public AtomicCounter getPositionCounter()
    {
        return positionCounter;
    }

    public void setPositionCounter(AtomicCounter positionCounter)
    {
        this.positionCounter = positionCounter;
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

    public ManyToOneConcurrentArrayQueue<LogConductorCmd> getToConductorCmdQueue()
    {
        return toConductorCmdQueue;
    }

    public void setToConductorCmdQueue(ManyToOneConcurrentArrayQueue<LogConductorCmd> toConductorCmdQueue)
    {
        this.toConductorCmdQueue = toConductorCmdQueue;
    }

    public String getLogName()
    {
        return logName;
    }

    public void setLogName(String logName)
    {
        this.logName = logName;
    }

    public LogAppendHandler getLogAppendHandler()
    {
        return logAppendHandler;
    }

    public void setLogAppendHandler(LogAppendHandler logAppendHandler)
    {
        this.logAppendHandler = logAppendHandler;
    }
}
