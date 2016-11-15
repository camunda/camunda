package org.camunda.tngp.logstreams.impl.processor;

import java.util.List;
import java.util.Objects;

import org.agrona.concurrent.status.Position;
import org.camunda.tngp.logstreams.LogStreamWriter;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamReader;
import org.camunda.tngp.logstreams.processor.RecoveryHandler;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.processor.StreamProcessorResource;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class StreamProcessorContextImpl implements StreamProcessorContext
{
    protected LogStream sourceStream;

    protected LogStream targetStream;

    protected StreamProcessor streamProcessor;

    protected LogStreamReader streamReader;

    protected LogStreamWriter eventWriter;

    protected RecoveryHandler recoveryHandler;

    protected Position position;

    protected AgentRunnerService agentRunnerService;

    protected List<StreamProcessorResource> resources;

    protected SnapshotStorage snapshotStorage;

    public LogStream getSourceStream()
    {
        return sourceStream;
    }

    public void setSourceStream(LogStream stream)
    {
        this.sourceStream = stream;
    }

    public void setTargetStream(LogStream targetStream)
    {
        this.targetStream = targetStream;
    }

    public LogStream getTargetStream()
    {
        return targetStream;
    }

    public StreamProcessor getStreamProcessor()
    {
        return streamProcessor;
    }

    public void setStreamProcessor(StreamProcessor streamProcessor)
    {
        this.streamProcessor = streamProcessor;
    }

    public LogStreamReader getStreamReader()
    {
        return streamReader;
    }

    public void setStreamReader(LogStreamReader streamReader)
    {
        this.streamReader = streamReader;
    }

    public LogStreamWriter getEventWriter()
    {
        return this.eventWriter;
    }

    public void setEventLogger(LogStreamWriter eventWriter)
    {
        this.eventWriter = eventWriter;
    }

    public RecoveryHandler getRecoveryHandler()
    {
        return recoveryHandler;
    }

    public void setRecoveryHandler(RecoveryHandler recoveryHandler)
    {
        this.recoveryHandler = recoveryHandler;
    }

    public Position getPosition()
    {
        return position;
    }

    public void setPosition(Position position)
    {
        this.position = position;
    }

    public AgentRunnerService getAgentRunnerService()
    {
        return agentRunnerService;
    }

    public void setAgentRunnerService(AgentRunnerService agentRunnerService)
    {
        this.agentRunnerService = agentRunnerService;
    }

    public List<StreamProcessorResource> getResources()
    {
        return resources;
    }

    public void setResources(List<StreamProcessorResource> resources)
    {
        this.resources = resources;
    }

    @Override
    public SnapshotSupport getResource(String name)
    {
        Objects.requireNonNull(name, "name cannot be null.");

        for (int i = 0; i < resources.size(); i++)
        {
            final StreamProcessorResource resource = resources.get(i);
            if (name.equals(resource.getName()))
            {
                return resource.getResource();
            }
        }

        throw new IllegalArgumentException(String.format("No resource with name %s defined", name));
    }

    public SnapshotStorage getSnapshotStorage()
    {
        return snapshotStorage;
    }

    public void setSnapshotStorage(SnapshotStorage snapshotStorage)
    {
        this.snapshotStorage = snapshotStorage;
    }
}
