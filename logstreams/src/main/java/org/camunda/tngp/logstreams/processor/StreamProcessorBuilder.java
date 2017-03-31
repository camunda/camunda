/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.logstreams.processor;

import java.time.Duration;
import java.util.Objects;

import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.snapshot.TimeBasedSnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotPolicy;
import org.camunda.tngp.logstreams.spi.SnapshotPositionProvider;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class StreamProcessorBuilder
{
    protected int id;
    protected String name;

    protected StreamProcessor streamProcessor;

    protected LogStream sourceStream;
    protected LogStream targetStream;

    protected AgentRunnerService agentRunnerService;

    protected SnapshotPolicy snapshotPolicy;
    protected SnapshotStorage snapshotStorage;
    protected SnapshotPositionProvider snapshotPositionProvider;

    protected LogStreamReader sourceLogStreamReader;
    protected LogStreamReader targetLogStreamReader;
    protected LogStreamWriter logStreamWriter;

    protected EventFilter eventFilter;
    protected EventFilter reprocessingEventFilter;

    protected DeferredCommandContext streamProcessorCmdQueue;

    protected boolean readOnly;

    public StreamProcessorBuilder(int id, String name, StreamProcessor streamProcessor)
    {
        this.id = id;
        this.name = name;
        this.streamProcessor = streamProcessor;
    }

    public StreamProcessorBuilder sourceStream(LogStream stream)
    {
        this.sourceStream = stream;
        return this;
    }

    public StreamProcessorBuilder targetStream(LogStream stream)
    {
        this.targetStream = stream;
        return this;
    }

    public StreamProcessorBuilder agentRunnerService(AgentRunnerService agentRunnerService)
    {
        this.agentRunnerService = agentRunnerService;
        return this;
    }

    public StreamProcessorBuilder snapshotPolicy(SnapshotPolicy snapshotPolicy)
    {
        this.snapshotPolicy = snapshotPolicy;
        return this;
    }

    public StreamProcessorBuilder snapshotStorage(SnapshotStorage snapshotStorage)
    {
        this.snapshotStorage = snapshotStorage;
        return this;
    }

    public StreamProcessorBuilder snapshotPositionProvider(SnapshotPositionProvider snapshotPositionProvider)
    {
        this.snapshotPositionProvider = snapshotPositionProvider;
        return this;
    }

    public StreamProcessorBuilder streamProcessorCmdQueue(DeferredCommandContext streamProcessorCmdQueue)
    {
        this.streamProcessorCmdQueue = streamProcessorCmdQueue;
        return this;
    }

    /**
     * @param eventFilter may be null to accept all events
     */
    public StreamProcessorBuilder eventFilter(EventFilter eventFilter)
    {
        this.eventFilter = eventFilter;
        return this;
    }

    public StreamProcessorBuilder readOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
        return this;
    }

    /**
     * @param reprocessingEventFilter may be null to re-process all events
     */
    public StreamProcessorBuilder reprocessingEventFilter(EventFilter reprocessingEventFilter)
    {
        this.reprocessingEventFilter = reprocessingEventFilter;
        return this;
    }

    protected void initContext()
    {
        Objects.requireNonNull(streamProcessor, "No stream processor provided.");
        Objects.requireNonNull(sourceStream, "No source stream provided.");
        Objects.requireNonNull(targetStream, "No target stream provided.");
        Objects.requireNonNull(agentRunnerService, "No agent runner service provided.");
        Objects.requireNonNull(snapshotStorage, "No snapshot storage provided.");

        if (streamProcessorCmdQueue == null)
        {
            streamProcessorCmdQueue = new DeferredCommandContext(100);
        }

        if (snapshotPolicy == null)
        {
            snapshotPolicy = new TimeBasedSnapshotPolicy(Duration.ofMinutes(1));
        }

        if (snapshotPositionProvider == null)
        {
            if (sourceStream.getId() == targetStream.getId())
            {
                snapshotPositionProvider = new LastProcessedEventPositionProvider();
            }
            else
            {
                snapshotPositionProvider = new LastWrittenEventPositionProvider();
            }
        }

        sourceLogStreamReader = new BufferedLogStreamReader();
        targetLogStreamReader = new BufferedLogStreamReader();

        logStreamWriter = new LogStreamWriter();
        if (readOnly)
        {
            logStreamWriter.disable();
        }
    }

    public StreamProcessorController build()
    {
        initContext();

        final StreamProcessorContext ctx = new StreamProcessorContext();

        ctx.setId(id);
        ctx.setName(name);

        ctx.setStreamProcessor(streamProcessor);
        ctx.setStreamProcessorCmdQueue(streamProcessorCmdQueue);

        ctx.setSourceStream(sourceStream);
        ctx.setTargetStream(targetStream);

        ctx.setAgentRunnerService(agentRunnerService);

        ctx.setSourceLogStreamReader(sourceLogStreamReader);
        ctx.setTargetLogStreamReader(targetLogStreamReader);
        ctx.setLogStreamWriter(logStreamWriter);

        ctx.setSnapshotPolicy(snapshotPolicy);
        ctx.setSnapshotStorage(snapshotStorage);
        ctx.setSnapshotPositionProvider(snapshotPositionProvider);

        ctx.setEventFilter(eventFilter);
        ctx.setReprocessingEventFilter(reprocessingEventFilter);
        ctx.setReadOnly(readOnly);

        return new StreamProcessorController(ctx);
    }

}
