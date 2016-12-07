package org.camunda.tngp.logstreams.processor;

import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class StreamProcessorContext
{
    protected long id;
    protected String name;

    protected StreamProcessor streamProcessor;

    protected LogStream sourceStream;
    protected LogStream targetStream;

    protected LogStreamReader logStreamReader;
    protected LogStreamWriter logStreamWriter;

    protected AgentRunnerService agentRunnerService;

    protected final Map<String, SnapshotSupport> resources = new HashMap<>();

    public SnapshotSupport getResource(String name)
    {
        return resources.get(name);
    }

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

    public long getId()
    {
        return id;
    }

    public void setId(long id)
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

    public void setLogStreamReader(LogStreamReader logStreamReader)
    {
        this.logStreamReader = logStreamReader;
    }

    public LogStreamReader getLogStreamReader()
    {
        return logStreamReader;
    }

    public LogStreamWriter getLogStreamWriter()
    {
        return logStreamWriter;
    }

    public void setLogStreamWriter(LogStreamWriter logStreamWriter)
    {
        this.logStreamWriter = logStreamWriter;
    }

}
