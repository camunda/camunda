package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotPositionProvider;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class TestStreamProcessorBuilder extends StreamProcessorBuilder
{
    public TestStreamProcessorBuilder(int id, String name, StreamProcessor streamProcessor)
    {
        super(id, name, streamProcessor);
    }

    @Override
    protected void initContext()
    {
        // do nothing
    }

    @Override
    public TestStreamProcessorBuilder sourceStream(LogStream stream)
    {
        return (TestStreamProcessorBuilder) super.sourceStream(stream);
    }

    @Override
    public TestStreamProcessorBuilder targetStream(LogStream stream)
    {
        return (TestStreamProcessorBuilder) super.targetStream(stream);
    }

    @Override
    public TestStreamProcessorBuilder agentRunnerService(AgentRunnerService agentRunnerService)
    {
        return (TestStreamProcessorBuilder) super.agentRunnerService(agentRunnerService);
    }

    @Override
    public TestStreamProcessorBuilder snapshotPolicy(SnapshotPolicy snapshotPolicy)
    {
        return (TestStreamProcessorBuilder) super.snapshotPolicy(snapshotPolicy);
    }

    @Override
    public TestStreamProcessorBuilder snapshotStorage(SnapshotStorage snapshotStorage)
    {
        return (TestStreamProcessorBuilder) super.snapshotStorage(snapshotStorage);
    }

    @Override
    public TestStreamProcessorBuilder snapshotPositionProvider(SnapshotPositionProvider snapshotPositionProvider)
    {
        return (TestStreamProcessorBuilder) super.snapshotPositionProvider(snapshotPositionProvider);
    }

    @Override
    public TestStreamProcessorBuilder streamProcessorCmdQueue(DeferredCommandContext streamProcessorCmdQueue)
    {
        return (TestStreamProcessorBuilder) super.streamProcessorCmdQueue(streamProcessorCmdQueue);
    }

    @Override
    public TestStreamProcessorBuilder eventFilter(EventFilter eventFilter)
    {
        return (TestStreamProcessorBuilder) super.eventFilter(eventFilter);
    }

    @Override
    public TestStreamProcessorBuilder reprocessingEventFilter(EventFilter eventFilter)
    {
        return (TestStreamProcessorBuilder) super.reprocessingEventFilter(eventFilter);
    }

    @Override
    public TestStreamProcessorBuilder errorHandler(StreamProcessorErrorHandler streamProcessorErrorHandler)
    {
        return (TestStreamProcessorBuilder) super.errorHandler(streamProcessorErrorHandler);
    }

    public TestStreamProcessorBuilder sourceLogStreamReader(LogStreamReader sourceLogStreamReader)
    {
        this.sourceLogStreamReader = sourceLogStreamReader;
        return this;
    }

    public TestStreamProcessorBuilder targetLogStreamReader(LogStreamReader targetLogStreamReader)
    {
        this.targetLogStreamReader = targetLogStreamReader;
        return this;
    }

    public TestStreamProcessorBuilder logStreamWriter(LogStreamWriter logStreamWriter)
    {
        this.logStreamWriter = logStreamWriter;
        return this;
    }

}
