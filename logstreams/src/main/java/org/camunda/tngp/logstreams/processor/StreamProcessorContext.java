package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotPositionProvider;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class StreamProcessorContext
{
    protected int id;
    protected String name;

    protected StreamProcessor streamProcessor;
    protected boolean isReadOnlyProcessor;

    protected LogStream sourceStream;
    protected LogStream targetStream;

    protected LogStreamReader sourceLogStreamReader;
    protected LogStreamReader targetLogStreamReader;
    protected LogStreamWriter logStreamWriter;

    protected SnapshotPolicy snapshotPolicy;
    protected SnapshotStorage snapshotStorage;
    protected SnapshotPositionProvider snapshotPositionProvider;

    protected AgentRunnerService agentRunnerService;

    protected EventFilter eventFilter;
    protected EventFilter reprocessingEventFilter;

    protected DeferredCommandContext streamProcessorCmdContext;

    public LogStream getSourceStream()
    {
        return sourceStream;
    }

    public void setSourceStream(LogStream sourceStream)
    {
        this.sourceStream = sourceStream;
    }

    public void setTargetStream(LogStream targetStream)
    {
        this.targetStream = targetStream;
    }

    public StreamProcessor getStreamProcessor()
    {
        return streamProcessor;
    }

    public void setStreamProcessor(StreamProcessor streamProcessor)
    {
        this.streamProcessor = streamProcessor;
    }

    public LogStream getTargetStream()
    {
        return targetStream;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public AgentRunnerService getAgentRunnerService()
    {
        return agentRunnerService;
    }

    public void setAgentRunnerService(AgentRunnerService agentRunnerService)
    {
        this.agentRunnerService = agentRunnerService;
    }

    public void setSourceLogStreamReader(LogStreamReader sourceLogStreamReader)
    {
        this.sourceLogStreamReader = sourceLogStreamReader;
    }

    public LogStreamReader getSourceLogStreamReader()
    {
        return sourceLogStreamReader;
    }

    public LogStreamWriter getLogStreamWriter()
    {
        return logStreamWriter;
    }

    public void setLogStreamWriter(LogStreamWriter logStreamWriter)
    {
        this.logStreamWriter = logStreamWriter;
    }

    public SnapshotPolicy getSnapshotPolicy()
    {
        return snapshotPolicy;
    }

    public void setSnapshotPolicy(SnapshotPolicy snapshotPolicy)
    {
        this.snapshotPolicy = snapshotPolicy;
    }

    public SnapshotStorage getSnapshotStorage()
    {
        return snapshotStorage;
    }

    public void setSnapshotStorage(SnapshotStorage snapshotStorage)
    {
        this.snapshotStorage = snapshotStorage;
    }

    public SnapshotPositionProvider getSnapshotPositionProvider()
    {
        return snapshotPositionProvider;
    }

    public void setSnapshotPositionProvider(SnapshotPositionProvider snapshotPositionProvider)
    {
        this.snapshotPositionProvider = snapshotPositionProvider;
    }

    public LogStreamReader getTargetLogStreamReader()
    {
        return targetLogStreamReader;
    }

    public void setTargetLogStreamReader(LogStreamReader targetLogStreamReader)
    {
        this.targetLogStreamReader = targetLogStreamReader;
    }

    public DeferredCommandContext getStreamProcessorCmdQueue()
    {
        return streamProcessorCmdContext;
    }

    public void setStreamProcessorCmdQueue(DeferredCommandContext streamProcessorCmdQueue)
    {
        this.streamProcessorCmdContext = streamProcessorCmdQueue;
    }

    public void setEventFilter(EventFilter eventFilter)
    {
        this.eventFilter = eventFilter;
    }

    public EventFilter getEventFilter()
    {
        return eventFilter;
    }

    public void setReprocessingEventFilter(EventFilter reprocessingEventFilter)
    {
        this.reprocessingEventFilter = reprocessingEventFilter;
    }

    public EventFilter getReprocessingEventFilter()
    {
        return reprocessingEventFilter;
    }

    public void setReadOnly(boolean readOnly)
    {
        this.isReadOnlyProcessor = readOnly;
    }

    public boolean isReadOnlyProcessor()
    {
        return isReadOnlyProcessor;
    }

}
