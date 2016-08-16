package org.camunda.tngp.log;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.appender.LogAppendHandler;
import org.camunda.tngp.log.appender.LogSegmentAllocationDescriptor;
import org.camunda.tngp.log.conductor.LogConductorCmd;
import org.camunda.tngp.log.fs.LogSegments;

import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class LogContext
{
    protected final int id;
    protected final String name;

    protected ManyToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue;

    protected LogSegmentAllocationDescriptor logAllocationDescriptor;

    protected Dispatcher writeBuffer;

    protected LogSegments logSegments = new LogSegments();

    protected LogAppendHandler logAppendHandler = new LogAppendHandler();

    protected boolean deleteOnClose;

    public LogContext(String name, int id)
    {
        this.name = name;
        this.id = id;
    }

    public ManyToOneConcurrentArrayQueue<LogConductorCmd> getLogConductorCmdQueue()
    {
        return logConductorCmdQueue;
    }

    public void setLogConductorCmdQueue(ManyToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue)
    {
        this.logConductorCmdQueue = logConductorCmdQueue;
    }

    public LogSegmentAllocationDescriptor getLogAllocationDescriptor()
    {
        return logAllocationDescriptor;
    }

    public void setLogAllocationDescriptor(LogSegmentAllocationDescriptor logAllocationDescriptor)
    {
        this.logAllocationDescriptor = logAllocationDescriptor;
    }

    public Dispatcher getWriteBuffer()
    {
        return writeBuffer;
    }

    public void setWriteBuffer(Dispatcher writeBuffer)
    {
        this.writeBuffer = writeBuffer;
    }

    public LogSegments getLogSegments()
    {
        return logSegments;
    }

    public void setAvailableSegments(LogSegments availableSegments)
    {
        this.logSegments = availableSegments;
    }

    public String getName()
    {
        return name;
    }

    public int getId()
    {
        return id;
    }

    public LogAppendHandler getLogAppendHandler()
    {
        return logAppendHandler;
    }

    public void setDeleteOnClose(boolean deleteOnClose)
    {
        this.deleteOnClose = deleteOnClose;
    }

    public boolean isDeleteOnClose()
    {
        return deleteOnClose;
    }
}
